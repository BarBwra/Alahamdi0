package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.compat.SbwCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Who may get into a vehicle: nobody, the organisation, or anybody.
 *
 * <h2>Three states, not two</h2>
 * <p>Superb Warfare has its own {@code Locked} boolean, and a boolean cannot say "my faction may
 * ride but strangers may not" - which is the state that actually matters on a server where people
 * play together. So the mode lives here, in the entity's own Forge persistent data, and SBW's flag
 * is kept in step purely so its padlock icon does not contradict us.</p>
 *
 * <h2>Stored on the vehicle, keyed by the owner</h2>
 * <p>The mode travels with the entity, which is what makes it survive a relog and a chunk unload
 * without a side table to keep in sync. Ownership is already stamped on the entity by
 * {@link VehicleOwnership}, so the two answers come from the same place and cannot drift.</p>
 */
public final class VehicleLock {

    private VehicleLock() {
    }

    private static final String KEY = "MlumLockMode";

    public enum Mode {
        /** The owner alone. What a vehicle is when it is first called. */
        LOCKED("مقفلة", 0xE0613F),
        /** The owner and everyone in their organisation. */
        FACTION("المنظمة فقط", 0xF0A93B),
        /** Anybody who walks up to it. */
        OPEN("مفتوحة", 0x93C46F);

        public final String label;
        public final int color;

        Mode(String label, int color) {
            this.label = label;
            this.color = color;
        }

        public Mode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /* ------------------------------------------------------------------ state */

    public static Mode mode(@Nullable Entity vehicle) {
        if (vehicle == null) {
            return Mode.OPEN;
        }
        CompoundTag tag = vehicle.getPersistentData();
        if (!tag.contains(KEY)) {
            // Anything that was never set is locked. A vehicle that defaulted to open would hand
            // the first one summoned after an update to whoever was standing nearest.
            return Mode.LOCKED;
        }
        int ordinal = tag.getInt(KEY);
        return ordinal < 0 || ordinal >= Mode.values().length ? Mode.LOCKED : Mode.values()[ordinal];
    }

    public static void setMode(Entity vehicle, Mode mode) {
        if (vehicle == null) {
            return;
        }
        vehicle.getPersistentData().putInt(KEY, mode.ordinal());
        /*
         * Superb Warfare's own Locked flag is forced OFF, always.
         *
         * It was briefly mirrored from ours, to keep its padlock icon from contradicting us. That
         * was a mistake and it broke the feature outright: its interact handler is
         *
         *     if (getLocked()) { tell("vehicle.locked"); return FAIL; }
         *
         * with no notion of who owns the thing - so mirroring "locked" onto it shut the owner out
         * of their own vehicle before any of this mod's code was reached. A boolean cannot say
         * "mine may ride, strangers may not", which is the entire reason this class exists. The
         * rule is enforced here; theirs must stay out of the way.
         */
        SbwCompat.setSbwLocked(vehicle, false);
    }

    /**
     * Clears Superb Warfare's flag on a vehicle that may still be carrying it.
     *
     * <p>Repair, not prevention. A vehicle summoned by the build that mirrored the flag is sitting
     * in the world right now with its own lock set, and nothing will ever clear it - its owner
     * cannot even get in to press L. This is called on the way into an interaction, before that
     * mod's handler runs, so those vehicles come back the first time anyone tries the door.</p>
     */
    public static void clearForeignLock(Entity vehicle) {
        SbwCompat.setSbwLocked(vehicle, false);
    }

    /** Turns the key one notch. Returns the new mode. */
    public static Mode cycle(Entity vehicle) {
        Mode next = mode(vehicle).next();
        setMode(vehicle, next);
        return next;
    }

    /* ------------------------------------------------------------------ the rule */

    /**
     * Whether this player may get in.
     *
     * <p>An unclaimed vehicle - one nobody summoned, placed by an operator or spawned by the world -
     * is nobody's, and is left alone entirely. The lock is a rule about <i>owned</i> vehicles, and
     * applying it to the rest would make every vehicle on the map unusable.</p>
     */
    public static boolean mayRide(@Nullable Player player, @Nullable Entity vehicle) {
        if (player == null || vehicle == null) {
            return true;
        }
        UUID owner = VehicleOwnership.ownerOf(vehicle);
        if (owner == null || owner.equals(player.getUUID())) {
            return true;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        return switch (mode(vehicle)) {
            case OPEN -> true;
            case FACTION -> sameFaction(player, owner);
            case LOCKED -> false;
        };
    }

    /** True when both are in the same organisation. False when either is in none. */
    private static boolean sameFaction(Player player, UUID owner) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) {
            return false;
        }
        var data = com.barbwra.mlum.faction.FactionData.get(serverPlayer.getServer());
        if (data == null) {
            return false;
        }
        var mine = data.of(player.getUUID());
        var theirs = data.of(owner);
        return mine != null && theirs != null && mine.id().equals(theirs.id());
    }

    /** Why a refusal happened, as a line for the player. */
    public static String refusal(Entity vehicle) {
        return mode(vehicle) == Mode.FACTION
                ? "هذي المركبة لأعضاء منظمتها بس"
                : "هذي المركبة مقفلة";
    }
}
