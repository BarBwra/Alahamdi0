package com.barbwra.mlum.client;

import com.barbwra.mlum.network.S2CVehicleLockState;
import com.barbwra.mlum.vehicle.VehicleLock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/** The lock state of the vehicle under this player, as last pushed by the server. */
@OnlyIn(Dist.CLIENT)
public final class ClientVehicleLock {

    private ClientVehicleLock() {
    }

    private static volatile boolean riding;
    private static volatile VehicleLock.Mode mode = VehicleLock.Mode.LOCKED;
    private static volatile boolean owner;

    public static void accept(S2CVehicleLockState msg) {
        riding = msg.riding();
        owner = msg.owner();
        VehicleLock.Mode[] all = VehicleLock.Mode.values();
        mode = msg.mode() >= 0 && msg.mode() < all.length ? all[msg.mode()] : VehicleLock.Mode.LOCKED;
    }

    /** The mode to draw, or null when there is nothing to draw. */
    @Nullable
    public static VehicleLock.Mode mode() {
        return riding ? mode : null;
    }

    /** Whether this player owns what they are sitting in - only they are shown the key hint. */
    public static boolean isOwner() {
        return riding && owner;
    }

    public static void clear() {
        riding = false;
        owner = false;
        mode = VehicleLock.Mode.LOCKED;
    }
}
