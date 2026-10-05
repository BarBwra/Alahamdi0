package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
 * Inside a Superb Warfare vehicle the hotbar is out of reach, in every seat: nothing in the hands
 * can be used, nothing can be hit, no block broken or placed, and no TACZ gun fired, reloaded or
 * swung ({@code TaczDriverRules}). An armoured hull is not a firing slit.
 *
 * <p>Only the hands are stopped. The vehicle's own guns and cannons fire Superb Warfare's own
 * projectiles through its own keys, and those still hurt whatever they hit. The client side of
 * this (no swing, no hands drawn) is in {@code client.hud.field.VehicleHud}.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DriverRules {

    private DriverRules() {
    }

    private static final String SBW = "com.atsuishio.superbwarfare";
    private static final Map<UUID, Long> TOLD = new HashMap<>();

    /** In any seat of a Superb Warfare vehicle. */
    public static boolean restricted(Player player) {
        return SbwCompat.isRiding(player);
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
            Feedback.bad(sp, "ما تقدر تستخدم اللي في يدك وأنت داخل المركبة");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            tell(event.getEntity());
        }
    }

    /** Anything in the hand: guns, bows, food, potions, pearls, tools. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
            tell(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(PlayerInteractEvent.LeftClickBlock event) {
        if (restricted(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * The backstop: any damage a rider deals with their own hands or a hand weapon's projectile is
     * cancelled, whatever slipped past the rest. Superb Warfare's own projectiles - the vehicle's
     * weapons - are let through.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (!(event.getSource().getEntity() instanceof Player attacker) || !restricted(attacker)) {
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
