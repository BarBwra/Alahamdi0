package com.barbwra.mlum.warehouse.core;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * An assembly line that is currently running.
 *
 * <p>Stores a <b>completion instant</b>, never a remaining count. The prototype subtracted five
 * from a {@code time} field every five seconds, which meant production silently paused whenever
 * the server was down and a crate started before a two-hour outage resumed as though no time had
 * passed. Here the job simply knows when it is due, and {@code now >= completesAt} is the whole
 * test.</p>
 *
 * <p>{@code rolledValue} is decided when the player commits their materials, not when the crate
 * pops. Rolling at the end would let a player watch the market and time their collection.</p>
 */
public record CraftJob(UUID id,
                       String recipeId,
                       int rolledValue,
                       long startedAt,
                       long completesAt) {

    public boolean isDone(long now) {
        return now >= completesAt;
    }

    public long millisRemaining(long now) {
        return Math.max(0L, completesAt - now);
    }

    /** 0..1, for the progress bar. */
    public float progress(long now) {
        long span = completesAt - startedAt;
        if (span <= 0L) {
            return 1.0F;
        }
        float done = (float) (now - startedAt) / span;
        return Math.max(0.0F, Math.min(1.0F, done));
    }

    /* --------------------------------------------------------------------- nbt */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("recipe", recipeId);
        tag.putInt("value", rolledValue);
        tag.putLong("started", startedAt);
        tag.putLong("completes", completesAt);
        return tag;
    }

    public static CraftJob load(CompoundTag tag) {
        return new CraftJob(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                tag.getString("recipe"),
                tag.getInt("value"),
                tag.getLong("started"),
                tag.getLong("completes"));
    }
}
