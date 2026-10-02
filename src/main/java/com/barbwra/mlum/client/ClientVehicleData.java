package com.barbwra.mlum.client;

import com.barbwra.mlum.vehicle.VehicleEntry;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** The last garage state the server pushed. Read by the vehicle screen each frame. */
@OnlyIn(Dist.CLIENT)
public final class ClientVehicleData {

    private ClientVehicleData() {
    }

    private static volatile List<VehicleEntry> owned = List.of();
    private static volatile boolean hasActive = false;
    private static volatile boolean combatLocked = false;
    private static volatile long cooldownTicks = 0L;
    private static volatile String blockKey = "";
    private static volatile String activeEntity = "";
    private static volatile int version;

    public static void set(List<VehicleEntry> incoming, boolean active, boolean locked,
                           long cooldown, String block, String activeId) {
        owned = incoming == null ? List.of() : List.copyOf(incoming);
        hasActive = active;
        combatLocked = locked;
        cooldownTicks = Math.max(0L, cooldown);
        blockKey = block == null ? "" : block;
        activeEntity = activeId == null ? "" : activeId;
        version++;
    }

    /** The entity id of the vehicle that is out, or blank. */
    public static String activeEntity() {
        return activeEntity;
    }

    /** Bumped on every garage packet: an action in flight is answered when this moves. */
    public static int version() {
        return version;
    }

    /** Translation key for the placement rule currently forbidding a summon, or empty. */
    public static String blockKey() {
        return blockKey;
    }

    public static List<VehicleEntry> owned() {
        return owned;
    }

    public static boolean hasActive() {
        return hasActive;
    }

    public static boolean combatLocked() {
        return combatLocked;
    }

    public static long cooldownTicks() {
        return cooldownTicks;
    }

    /** The screen ticks this down locally so the countdown reads smoothly between server syncs. */
    public static void tick() {
        if (cooldownTicks > 0L) {
            cooldownTicks--;
        }
    }

    public static void clear() {
        owned = List.of();
        hasActive = false;
        combatLocked = false;
        cooldownTicks = 0L;
        blockKey = "";
        activeEntity = "";
        version++;
    }
}
