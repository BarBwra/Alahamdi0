package com.barbwra.mlum.camo;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ghillie suits: a full set hides its wearer once they settle in.
 *
 * <h2>The rule</h2>
 * <p>All four pieces of one suit, then crouch and hold still for {@code hideSeconds}. A little
 * shuffle - less than {@link #DRIFT} from where you settled - does not count; standing up or moving
 * further shows you again, and the count starts over. With {@code requireCover} on, the suit also
 * needs its kind of cover: the green one in leaves, grass, crops, bushes, flowers or vines, the snow
 * one in or on snow and ice.</p>
 *
 * <h2>How the hiding works</h2>
 * <p>The server sets the player's own invisible flag while they are hidden. That one flag is synced
 * to every client by vanilla and already hides the body, the shadow and the name tag; mobs see an
 * invisible player less well too. Everything vanilla still draws on an invisible player - armour,
 * what is in hand, the backpack on their back - every client leaves out (see {@code GhillieClient}),
 * so nothing at all gives the wearer away. When they show themselves the flag goes back to whatever
 * an invisibility potion says it should be.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Ghillie {

    private Ghillie() {
    }

    public enum Suit { GREEN, SNOW }

    private static final EquipmentSlot[] ARMOUR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    /** Players this class made invisible, so it only ever undoes its own work. */
    private static final Set<UUID> HIDDEN = new HashSet<>();
    /** Where each settling player crouched, and on which tick. */
    private static final Map<UUID, Settle> SETTLING = new HashMap<>();

    /** How far a settled player may shuffle, in blocks, without it counting as moving. */
    public static final double DRIFT = 1.0D;

    /** A crouch in progress: where it started and when. */
    public record Settle(double x, double z, long since) {
    }

    /**
     * The one rule, shared by the server (which hides) and the client (which only shows the count):
     * the settle carried forward this tick, or null when the player is not settling at all.
     */
    public static Settle step(Player player, Settle current, long now) {
        if (!MlumConfig.camouflage() || player.isSpectator() || suitOf(player) == null
                || !player.isCrouching() || (MlumConfig.camouflageNeedsCover() && !inCover(player))) {
            return null;
        }
        if (current != null) {
            double dx = player.getX() - current.x();
            double dz = player.getZ() - current.z();
            if (dx * dx + dz * dz <= DRIFT * DRIFT) {
                return current;
            }
        }
        return new Settle(player.getX(), player.getZ(), now);
    }

    /** 0..1 of the way to hidden. */
    public static float progress(Settle settle, long now) {
        if (settle == null) {
            return 0.0F;
        }
        int need = Math.max(1, MlumConfig.hideSeconds() * 20);
        return Math.min(1.0F, (now - settle.since()) / (float) need);
    }

    /** The suit this entity wears in full, or null. Works on either side. */
    public static Suit suitOf(LivingEntity entity) {
        if (wearsAll(entity, MlumConfig.greenSuit())) {
            return Suit.GREEN;
        }
        if (wearsAll(entity, MlumConfig.snowSuit())) {
            return Suit.SNOW;
        }
        return null;
    }

    private static boolean wearsAll(LivingEntity entity, List<? extends String> ids) {
        if (ids.isEmpty()) {
            return false;
        }
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack worn = entity.getItemBySlot(slot);
            if (worn.isEmpty()) {
                return false;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(worn.getItem());
            if (id == null || !ids.contains(id.toString())) {
                return false;
            }
        }
        return true;
    }

    /** Wearing a full suit and touching its cover. */
    public static boolean inCover(Player player) {
        Suit suit = suitOf(player);
        if (suit == null) {
            return false;
        }
        Level level = player.level();
        // the body, a little wider so a hedge you are pressed against counts
        AABB body = player.getBoundingBox().inflate(0.25D, 0.0D, 0.25D);
        for (BlockPos pos : BlockPos.betweenClosed(
                (int) Math.floor(body.minX), (int) Math.floor(body.minY), (int) Math.floor(body.minZ),
                (int) Math.floor(body.maxX), (int) Math.floor(body.maxY - 1.0E-3D), (int) Math.floor(body.maxZ))) {
            if (matches(suit, level.getBlockState(pos))) {
                return true;
            }
        }
        // and what you stand on: snow always, grass or moss only lying low on it
        BlockState under = level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 0.2D, player.getZ()));
        if (suit == Suit.SNOW) {
            return matches(suit, under);
        }
        return player.isCrouching() && (under.is(Blocks.GRASS_BLOCK) || under.is(Blocks.MOSS_BLOCK) || matches(suit, under));
    }

    private static boolean matches(Suit suit, BlockState state) {
        Block block = state.getBlock();
        if (suit == Suit.SNOW) {
            return block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
                    || block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE
                    || block == Blocks.FROSTED_ICE;
        }
        return block instanceof BushBlock || block instanceof LeavesBlock || block instanceof VineBlock
                || state.is(BlockTags.LEAVES)
                || block == Blocks.MOSS_CARPET || block == Blocks.MOSS_BLOCK
                || block == Blocks.BIG_DRIPLEAF || block == Blocks.BIG_DRIPLEAF_STEM || block == Blocks.SMALL_DRIPLEAF
                || block == Blocks.SUGAR_CANE || block == Blocks.BAMBOO || block == Blocks.CACTUS;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        long now = player.level().getGameTime();
        Settle settle = step(player, SETTLING.get(id), now);
        if (settle == null) {
            SETTLING.remove(id);
        } else {
            SETTLING.put(id, settle);
        }
        boolean hide = settle != null && progress(settle, now) >= 1.0F;
        if (hide) {
            HIDDEN.add(id);
            if (!player.isInvisible()) {
                player.setInvisible(true);
            }
        } else if (HIDDEN.remove(id)) {
            player.setInvisible(player.hasEffect(MobEffects.INVISIBILITY));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SETTLING.remove(event.getEntity().getUUID());
        if (HIDDEN.remove(event.getEntity().getUUID())) {
            event.getEntity().setInvisible(event.getEntity().hasEffect(MobEffects.INVISIBILITY));
        }
    }
}
