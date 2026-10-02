package com.barbwra.mlum.warehouse.core;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * One finished crate sitting in a warehouse.
 *
 * <p><b>Identity is a UUID, not a position.</b> The Skript prototype addressed crates by their
 * index into a flat list, and every one of that decision's consequences was a bug: deleting a
 * spoiled crate shifted every index after it while the player still had a selection open, so the
 * confirm button sold cargo the player had never picked. A crate here is the same crate no matter
 * what happens to the collection around it.</p>
 *
 * <p><b>Two clocks.</b> {@code craftedAt} and {@code expiresAt} are absolute epoch milliseconds, so
 * shelf life keeps running while the server is down - a warehouse left alone over a weekend has
 * genuinely aged. Nothing decrements.</p>
 *
 * <p>{@code baseValue} is fixed when assembly starts. What the crate actually sells for is that
 * number times the live market index times {@link #freshness}, resolved at dispatch. Freezing the
 * base and floating the multiplier is what lets prices move without rewriting player property.</p>
 */
public record Crate(UUID id,
                    CargoType cargo,
                    int tier,
                    int baseValue,
                    long craftedAt,
                    long expiresAt,
                    boolean reserved) {

    /**
     * Value retained as the crate ages: full until the last fifth of its shelf life, then sliding
     * to 75% at the moment it spoils.
     *
     * <p>A cliff would mean a crate is worth full price one second and worthless the next, which
     * punishes a player for being offline at the wrong hour. A ramp gives them a visible window in
     * which the terminal can warn them and they can still act.</p>
     */
    public float freshness(long now) {
        long life = expiresAt - craftedAt;
        if (life <= 0L) {
            return 1.0F;
        }
        float remaining = (float) (expiresAt - now) / life;
        if (remaining >= 0.2F) {
            return 1.0F;
        }
        if (remaining <= 0.0F) {
            return 0.75F;
        }
        return 0.75F + 0.25F * (remaining / 0.2F);
    }

    public boolean isSpoiled(long now) {
        return now >= expiresAt;
    }

    public long millisRemaining(long now) {
        return Math.max(0L, expiresAt - now);
    }

    public Crate withReserved(boolean value) {
        return new Crate(id, cargo, tier, baseValue, craftedAt, expiresAt, value);
    }

    /* --------------------------------------------------------------------- nbt */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("cargo", cargo.id());
        tag.putInt("tier", tier);
        tag.putInt("value", baseValue);
        tag.putLong("crafted", craftedAt);
        tag.putLong("expires", expiresAt);
        if (reserved) {
            tag.putBoolean("reserved", true);
        }
        return tag;
    }

    public static Crate load(CompoundTag tag) {
        return new Crate(
                tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                CargoType.byId(tag.getString("cargo")),
                Math.max(1, tag.getInt("tier")),
                tag.getInt("value"),
                tag.getLong("crafted"),
                tag.getLong("expires"),
                tag.getBoolean("reserved"));
    }
}
