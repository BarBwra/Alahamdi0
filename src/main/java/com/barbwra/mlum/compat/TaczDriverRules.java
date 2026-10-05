package com.barbwra.mlum.compat;

import com.barbwra.mlum.vehicle.DriverRules;
import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * No TACZ shooting or gun-butt melee from a Superb Warfare driver's seat, on both sides, so the
 * client does not even show the shot. Registered only when TACZ is installed.
 */
public final class TaczDriverRules {

    private TaczDriverRules() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, event -> {
            if (event.getShooter() instanceof Player p && DriverRules.driving(p)) {
                event.setCanceled(true);
                DriverRules.tell(p);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunMeleeEvent.class, event -> {
            if (event.getShooter() instanceof Player p && DriverRules.driving(p)) {
                event.setCanceled(true);
                DriverRules.tell(p);
            }
        });
    }
}
