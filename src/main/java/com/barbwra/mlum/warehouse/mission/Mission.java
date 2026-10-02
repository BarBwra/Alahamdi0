package com.barbwra.mlum.warehouse.mission;

import com.barbwra.mlum.warehouse.core.CargoType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A delivery in flight.
 *
 * <p><b>The payout is decided once, at dispatch, and stored here.</b> Everything that goes into it -
 * market index, freshness, convoy size, route risk, population, the global event - is resolved
 * server-side while the player is looking at the dispatch preview, written to {@code quotedPayout},
 * and that stored number is what settles. The prototype recomputed the multiplier independently in
 * the screen that displayed it and in the handler that paid it, so a player joining the server
 * between the two changed what a run was worth after it had been authorised.</p>
 *
 * <p>{@code breakdown} keeps the human-readable derivation for the ledger, so a disputed payout can
 * be explained months later without re-deriving anything.</p>
 *
 * <p>The crates themselves leave storage at dispatch, so all that is kept here is what the delivery
 * log needs: cargo type and tier per crate.</p>
 */
public final class Mission {

    /** What a crate contributes to the delivery record once the run lands. */
    public record Manifest(CargoType cargo, int tier) {
    }

    private final UUID id;
    private final UUID runner;
    private final String routeId;
    private final List<Manifest> manifest;
    private final int quotedPayout;
    private final String breakdown;
    private final long startedAt;

    private MissionState state;
    private long leaksAt;
    private long expiresAt;
    private long graceUntil;

    public Mission(UUID id, UUID runner, String routeId, List<Manifest> manifest,
                   int quotedPayout, String breakdown,
                   long startedAt, long leaksAt, long expiresAt) {
        this.id = id;
        this.runner = runner;
        this.routeId = routeId;
        this.manifest = List.copyOf(manifest);
        this.quotedPayout = quotedPayout;
        this.breakdown = breakdown;
        this.startedAt = startedAt;
        this.leaksAt = leaksAt;
        this.expiresAt = expiresAt;
        this.state = MissionState.TRANSIT;
    }

    /* ------------------------------------------------------------- accessors */

    public UUID id() {
        return id;
    }

    public UUID runner() {
        return runner;
    }

    public String routeId() {
        return routeId;
    }

    public List<Manifest> manifest() {
        return manifest;
    }

    public int quotedPayout() {
        return quotedPayout;
    }

    public String breakdown() {
        return breakdown;
    }

    public long startedAt() {
        return startedAt;
    }

    public MissionState state() {
        return state;
    }

    public void setState(MissionState value) {
        this.state = value;
    }

    public long leaksAt() {
        return leaksAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public long graceUntil() {
        return graceUntil;
    }

    public void setGraceUntil(long value) {
        this.graceUntil = value;
    }

    /**
     * Pushes both deadlines back by the time spent offline.
     *
     * <p>Without this a player who reconnects near the end of their grace window would find the
     * hard deadline had kept running while they were not in the world, which turns a disconnect
     * into an automatic loss even when they came straight back. The leak clock moves too - being
     * offline is not a way to stay hidden for free, but it is not extra punishment either.</p>
     */
    public void extendBy(long millis) {
        if (millis > 0L) {
            leaksAt += millis;
            expiresAt += millis;
        }
    }

    public boolean hasLeaked(long now) {
        return now >= leaksAt;
    }

    public long millisUntilLeak(long now) {
        return Math.max(0L, leaksAt - now);
    }

    public long millisUntilExpiry(long now) {
        return Math.max(0L, expiresAt - now);
    }

    /* -------------------------------------------------------------------- nbt */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("runner", runner);
        tag.putString("route", routeId);
        tag.putInt("payout", quotedPayout);
        tag.putString("breakdown", breakdown);
        tag.putLong("started", startedAt);
        tag.putLong("leaks", leaksAt);
        tag.putLong("expires", expiresAt);
        tag.putLong("grace", graceUntil);
        tag.putString("state", state.name());

        ListTag list = new ListTag();
        for (Manifest entry : manifest) {
            CompoundTag part = new CompoundTag();
            part.putString("cargo", entry.cargo().id());
            part.putInt("tier", entry.tier());
            list.add(part);
        }
        tag.put("manifest", list);
        return tag;
    }

    public static Mission load(CompoundTag tag) {
        List<Manifest> manifest = new ArrayList<>();
        ListTag list = tag.getList("manifest", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag part = list.getCompound(i);
            manifest.add(new Manifest(CargoType.byId(part.getString("cargo")), part.getInt("tier")));
        }

        Mission mission = new Mission(
                tag.getUUID("id"),
                tag.getUUID("runner"),
                tag.getString("route"),
                manifest,
                tag.getInt("payout"),
                tag.getString("breakdown"),
                tag.getLong("started"),
                tag.getLong("leaks"),
                tag.getLong("expires"));
        mission.graceUntil = tag.getLong("grace");
        try {
            mission.state = MissionState.valueOf(tag.getString("state"));
        } catch (IllegalArgumentException ignored) {
            mission.state = MissionState.GRACE;
        }
        return mission;
    }
}
