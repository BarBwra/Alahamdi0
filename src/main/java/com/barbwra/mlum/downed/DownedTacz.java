package com.barbwra.mlum.downed;

import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * No shooting or reloading from the ground.
 *
 * <p>TACZ fires through its own keys and packets, not the use button, so the downed system's usual
 * "no interacting" rule never sees a shot. Its own shoot and reload events are cancelled instead,
 * on both sides. Registered only when TACZ is installed - this class names TACZ types, and loading
 * it without them would fail.</p>
 */
public final class DownedTacz {

    private DownedTacz() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class, event -> {
            if (DownedState.isDowned(event.getShooter())) {
                event.setCanceled(true);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunReloadEvent.class, event -> {
            if (DownedState.isDowned(event.getEntity())) {
                event.setCanceled(true);
            }
        });
    }
}
