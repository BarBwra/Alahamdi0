package com.barbwra.mlum.warehouse.client;

import com.barbwra.mlum.warehouse.net.S2CMissionHud;

/**
 * The client's copy of the running delivery, refreshed once a second.
 *
 * <p>Everything time-based is stored as the absolute instant the server sent, so the HUD computes
 * {@code deadline - now} fresh on every frame. That is why a one-packet-per-second feed still draws
 * a countdown that ticks smoothly at 60 fps and never jumps when a packet arrives late.</p>
 */
public final class ClientMissionData {

    private ClientMissionData() {
    }

    private static S2CMissionHud state = S2CMissionHud.INACTIVE;

    public static void accept(S2CMissionHud incoming) {
        state = incoming == null ? S2CMissionHud.INACTIVE : incoming;
    }

    public static S2CMissionHud state() {
        return state;
    }

    public static boolean isActive() {
        return state.active();
    }

    /** True once the leak has fired and the runner's position is public. */
    public static boolean isExposed() {
        return state.active() && System.currentTimeMillis() >= state.leaksAt();
    }

    public static long millisUntilLeak() {
        return Math.max(0L, state.leaksAt() - System.currentTimeMillis());
    }

    public static long millisUntilExpiry() {
        return Math.max(0L, state.expiresAt() - System.currentTimeMillis());
    }

    public static void clear() {
        state = S2CMissionHud.INACTIVE;
    }
}
