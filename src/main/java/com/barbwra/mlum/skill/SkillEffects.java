package com.barbwra.mlum.skill;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.TaczCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What the skills actually do.
 *
 * <h2>Why this file exists</h2>
 * <p>The skills tab used to sell perks and then run a console command - {@code mlum_skill %player%
 * %skill% %level%} - leaving the effect to a server script. That command was never written, and the
 * call site suppressed its output, so buying a skill took the money, marked the card complete, and
 * changed nothing at all. The effects live here now, in code, where they can be read and tested.</p>
 *
 * <h2>Each one is a multiplier on something the game already does</h2>
 * <p>Nothing here adds a new rule; every skill scales an existing one. That keeps them
 * composable - two skills can touch the same kill without fighting - and it means a skill can be
 * dropped to level 0 and leave no trace, which is what makes {@link SkillService#drop} honest.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class SkillEffects {

    private SkillEffects() {
    }

    public static final String BUTCHER = "butcher";
    public static final String MELEE = "blade_master";
    public static final String MEDIC = "medic";
    public static final String SCOUT = "scout";
    public static final String ATTACHMENTS = "attachments";
    /** إيد خفيفة: a fast search knocks things over less often. See {@code LootSearch}. */
    public static final String QUIET_HANDS = "quiet_hands";

    /** What each level of quiet hands takes off the fast search's noise chance. */
    private static final float[] QUIET_CUT = {0.10F, 0.20F, 0.30F};

    /** The chance a fast search makes a noise for this player: the config's, less the skill. */
    public static float noiseChance(Player player, double base) {
        return Math.max(0.0F, (float) base - bonus(QUIET_CUT, SkillService.level(player, QUIET_HANDS)));
    }

    /** Extra drops per level, as a fraction. Index 0 is level 1. */
    private static final float[] BUTCHER_BONUS = {0.10F, 0.20F, 0.35F};
    private static final float[] MELEE_BONUS = {0.15F, 0.30F, 0.50F};
    private static final float[] MEDIC_BONUS = {0.15F, 0.30F, 0.50F};

    private static float bonus(float[] table, int level) {
        return level <= 0 ? 0.0F : table[Math.min(table.length, level) - 1];
    }

    /* ================================================================== drops */

    /**
     * الجزار and السلاح اليدوي, both of which multiply what a kill leaves behind.
     *
     * <p>They stack on purpose and they scale different things: the butcher only ever grows the meat
     * a zombie drops, while the melee skill grows <i>everything</i> - but only when the killing blow
     * came from something held in the hand rather than from a firearm. A player who has both and
     * kills a zombie with a machete gets both, which is exactly the build the two cards describe.</p>
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (event.isCanceled() || event.getDrops().isEmpty()) {
            return;
        }
        Player killer = killerOf(event.getSource().getEntity(), event.getEntity());
        if (!(killer instanceof ServerPlayer player)) {
            return;
        }

        float meat = event.getEntity() instanceof Zombie
                ? bonus(BUTCHER_BONUS, SkillService.level(player, BUTCHER)) : 0.0F;
        float all = wasMelee(player, event) ? bonus(MELEE_BONUS, SkillService.level(player, MELEE)) : 0.0F;
        if (meat <= 0.0F && all <= 0.0F) {
            return;
        }

        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity entity : event.getDrops()) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            float rate = all;
            if (meat > 0.0F && isMeat(stack)) {
                rate += meat;
            }
            int more = scale(player, stack.getCount(), rate);
            if (more <= 0) {
                continue;
            }
            ItemStack copy = stack.copy();
            copy.setCount(more);
            extra.add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(), copy));
        }
        event.getDrops().addAll(extra);
    }

    /**
     * How many more of something to give.
     *
     * <p>The fraction is not thrown away. A 15% bonus on a single bone is 0.15 of an item, and
     * rounding that down would make the skill do nothing at all for every one-item drop in the
     * game - which is most of them. It is rolled instead, so it pays out about right over a
     * session rather than exactly right on every kill.</p>
     */
    private static int scale(ServerPlayer player, int count, float rate) {
        if (rate <= 0.0F || count <= 0) {
            return 0;
        }
        float exact = count * rate;
        int whole = (int) exact;
        return player.getRandom().nextFloat() < exact - whole ? whole + 1 : whole;
    }

    private static boolean isMeat(ItemStack stack) {
        return stack.is(Items.ROTTEN_FLESH) || (stack.getItem().isEdible() && !stack.is(Items.SPIDER_EYE));
    }

    /** True when the blow came from something held rather than from a firearm or a thrown thing. */
    private static boolean wasMelee(Player killer, LivingDropsEvent event) {
        if (event.getSource().getDirectEntity() != killer) {
            // a bullet, an arrow or anything else that travelled is not a melee hit
            return false;
        }
        return !TaczCompat.isGun(killer.getMainHandItem());
    }

    /** The player behind a kill: the attacker itself, or whoever owns the projectile that landed. */
    @Nullable
    private static Player killerOf(Object direct, LivingEntity victim) {
        if (direct instanceof Player player) {
            return player;
        }
        // the same walk the faction points use - a bullet is not a player, its owner is
        return victim.getKillCredit() instanceof Player credited ? credited : null;
    }

    /* ================================================================== medic */

    /**
     * المسعف: everything that heals you heals more.
     *
     * <p>Scoped to a window after the player finishes using something, rather than to a list of item
     * ids. The server runs several mods that heal, each in its own way - a stimpak, a bandage, a
     * potion, a golden apple's regeneration - and a list would have to be maintained forever and
     * would still be wrong the day a new mod is added. "You just used something, and now you are
     * healing" catches all of them and leaves ordinary hunger regeneration alone.</p>
     */
    private static final Map<UUID, Long> USED_AT = new WeakHashMap<>();
    /** Ten seconds, which is long enough to cover the regeneration a healing item grants. */
    private static final long HEAL_WINDOW_TICKS = 200L;

    @SubscribeEvent
    public static void onUseFinish(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getItem().isEmpty()) {
            USED_AT.put(player.getUUID(), player.level().getGameTime());
        }
    }

    /**
     * The other half of it: an item used in one click.
     *
     * <p>{@code Finish} only fires for items with a use duration - food, a potion, a bow. A bandage
     * or a stimpak that heals on the right-click itself never reaches it, and those are most of the
     * medical items on this server. Opening the window here as well is what makes the skill apply to
     * them.</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && !event.getItemStack().isEmpty()) {
            USED_AT.put(player.getUUID(), player.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        float rate = bonus(MEDIC_BONUS, SkillService.level(player, MEDIC));
        if (rate <= 0.0F) {
            return;
        }
        Long usedAt = USED_AT.get(player.getUUID());
        if (usedAt == null || player.level().getGameTime() - usedAt > HEAL_WINDOW_TICKS) {
            return;
        }
        event.setAmount(event.getAmount() * (1.0F + rate));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            USED_AT.remove(player.getUUID());
        }
    }
}
