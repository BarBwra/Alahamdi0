package com.barbwra.mlum.warehouse.mission;

/**
 * Where a delivery is in its life.
 *
 * <pre>
 *          dispatch                leaksAt                  reached the drop
 *  (none) ──────────▶ TRANSIT ───────────────▶ EXPOSED ─────────────────────▶ SETTLED
 *                        │                        │
 *              logout    ▼         logout         ▼        expiresAt / death
 *                     ┌─────────────────────────────┐
 *                     │            GRACE            │──────────────────────▶ FAILED
 *                     └─────────────────────────────┘
 *                          reconnect in time ▲ back to TRANSIT or EXPOSED
 * </pre>
 *
 * <p>{@link #GRACE} is the state the prototype had no answer for. There, a runner who logged out
 * left a UUID in the active-mission list that could never be cleaned up, because clearing it needed
 * an online player object; the tick loop then threw once per second, forever, and the player kept
 * their 999-day mission potions. Here logging out parks the run on a deadline like any other
 * transition: reconnect inside the window and it resumes exactly where it was, miss it and the
 * cargo is forfeit. Combat logging becomes a loss rather than an escape.</p>
 */
public enum MissionState {

    /** Carrying cargo, position still private. */
    TRANSIT,
    /** The leak has fired - the runner's live position is broadcast to every player. */
    EXPOSED,
    /** The runner is offline. Resumes on reconnect, fails when {@code graceUntil} passes. */
    GRACE,
    /** Delivered and paid. Terminal state. */
    SETTLED,
    /** Timed out, died, or ran out of grace. Terminal state. */
    FAILED;

    public boolean isTerminal() {
        return this == SETTLED || this == FAILED;
    }

    /** True while the runner is actually carrying cargo around the world. */
    public boolean isRunning() {
        return this == TRANSIT || this == EXPOSED;
    }
}
