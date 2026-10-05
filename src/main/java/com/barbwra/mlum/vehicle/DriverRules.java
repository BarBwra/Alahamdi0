package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Whoever drives a Superb Warfare vehicle has their hands on the wheel: no hitting, no bows,
 * crossbows, tridents or anything thrown, and no TACZ gun (that half is {@code TaczDriverRules}).
 *
 * <p>Only hand weapons are stopped. The vehicle's own guns and cannons fire projectiles from Superb
 * Warfare itself, and those still hurt whatever they hit - a tank driver still drives a tank.
 * Passengers in the other seats are not affected.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DriverRules {

    private DriverRules() {
    }

    private static final String SBW = "com.atsuishio.superbwarfare";
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    public static boolean driving(Player player) {
        return SbwCompat.isDriving(player);
    }

    /** Says why, at most every two seconds, so holding the button is not a chat flood. */
    public static void tell(Player player) {
        if (!(player instanceof ServerPlayer sp)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = TOLD.get(sp.getUUID());
        if (last == null || now - last > 2000L) {
            TOLD.put(sp.getUUID(), now);
            Feedback.bad(sp, "ما تقدر تستخدم سلاح وأنت تسوق");
        }
    }

    /** Hitting anything - a mob, a player, another vehicle - from the driver's seat. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (driving(event.getEntity())) {
            event.setCanceled(true);
            tell(event.getEntity());
        }
    }

    /** Bows, crossbows, tridents, potions, pearls and anything else thrown. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (!driving(event.getEntity())) {
            return;
        }
        Item item = event.getItemStack().getItem();
        if (item instanceof ProjectileWeaponItem || item instanceof TridentItem || item instanceof ThrowablePotionItem
                || item instanceof SnowballItem || item instanceof EggItem || item instanceof EnderpearlItem) {
            event.setCanceled(true);
            tell(event.getEntity());
        }
    }

    /**
     * The backstop: any damage a driver deals with their own hands or a hand weapon's projectile is
     * cancelled, whatever slipped past the two above. Projectiles of Superb Warfare's own - the
     * vehicle's weapons - are let through.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || !driving(attacker)) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        boolean byHand = direct == attacker;
        boolean handProjectile = direct instanceof Projectile && !direct.getClass().getName().startsWith(SBW);
        if (byHand || handProjectile) {
            event.setCanceled(true);
        }
    }
}
