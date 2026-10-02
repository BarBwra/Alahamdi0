package com.barbwra.mlum.combat;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Firearms hit players for less than they hit everything else.
 *
 * <p>A gun that drops a zombie in two rounds drops a player in two rounds as well, and a server
 * where every fight is decided in under a second stops having fights - it has ambushes. Halving
 * only the player-versus-player case keeps guns lethal against the world while giving a defender
 * enough time to react, take cover, or lose.</p>
 *
 * <p><b>Scoped to gunfire, not to all PvP.</b> Melee, explosions and fall damage are untouched.
 * That is deliberate: the goal is to slow down ranged trades specifically, and a blanket PvP
 * reduction would also blunt the melee weapons this pack wants players to actually use.</p>
 *
 * <p><b>Why {@link LivingHurtEvent} and not {@code LivingDamageEvent}.</b> Hurt fires before armour
 * and enchantment reduction, so scaling here means the victim's own protection still applies to the
 * reduced figure and the two systems compose the way a player expects. Scaling after reduction
 * would make armour worth half as much against guns as it is against everything else.</p>
 *
 * <p>Runs at {@link EventPriority#LOW} so anything that wants to set a flat amount has already had
 * its turn; this only ever scales what the rest of the chain arrived at.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class GunDamageRule {

    private GunDamageRule() {
    }

    /**
     * What a firearm keeps when the target is a player.
     *
     * <p>0.5 - an 8-damage round lands for 4 on a player and 8 on anything else. Change this one
     * number to retune; it is intentionally not a config value yet, because a live server changing
     * a combat constant mid-session gets very confusing bug reports.</p>
     */
    private static final float PLAYER_DAMAGE_FACTOR = 0.5F;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player victim) || victim.level().isClientSide) {
            return;
        }
        if (!isGunfire(event.getSource())) {
            return;
        }
        event.setAmount(event.getAmount() * PLAYER_DAMAGE_FACTOR);
    }

    /**
     * True when this damage came out of a barrel.
     *
     * <p>Two independent tests, because TACZ has changed how it reports damage between versions and
     * neither signal is guaranteed on its own: the projectile that actually landed is usually a
     * {@code tacz:} entity, and the damage type is usually named after the mod. Either is enough,
     * and a hit that satisfies neither is left alone rather than guessed at.</p>
     */
    private static boolean isGunfire(DamageSource source) {
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(direct.getType());
            if (type != null && "tacz".equals(type.getNamespace())) {
                return true;
            }
        }
        String id = source.getMsgId();
        return id.contains("tacz") || id.contains("bullet");
    }
}
