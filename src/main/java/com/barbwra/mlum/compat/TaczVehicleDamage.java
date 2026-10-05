package com.barbwra.mlum.compat;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.vehicle.CombatTracker;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * TACZ bullets hurt Superb Warfare vehicles.
 *
 * <p>TACZ hands a hit vehicle its own bullet damage type, which Superb Warfare's vehicles do not
 * take, so rifles, pistols, shotguns and snipers bounced off and only rockets (explosions) did
 * anything. Here the hit is taken over before TACZ applies it: the bullet's damage, times
 * {@code bulletVehicleDamage}, comes straight off the vehicle's health through the vehicle's own
 * damage routine. Registered only when TACZ is installed.</p>
 */
public final class TaczVehicleDamage {

    private TaczVehicleDamage() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, EntityHurtByGunEvent.Pre.class, event -> {
            if (event.getLogicalSide() != LogicalSide.SERVER) {
                return;
            }
            Entity target = event.getHurtEntity();
            if (!SbwCompat.isVehicle(target)) {
                return;
            }
            double scale = MlumConfig.bulletVehicleDamage();
            if (scale <= 0.0D) {
                return;
            }
            float amount = (float) (event.getAmount() * scale);
            if (SbwCompat.damage(target, amount, event.getAttacker())) {
                // handled: TACZ's own hit would only bounce off, or count twice
                event.setCanceled(true);
                CombatTracker.tagVehicle(target);
            }
        });
    }
}
