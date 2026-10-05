package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Every vehicle a player owns, and the one they currently have out.
 *
 * <p>Ownership is granted only by {@code /VehicleMenu} - there is no item, no crafting, no drop.
 * The whole garage is data in the player's persistent NBT, which is what lets a consumable stack
 * without ever occupying an inventory slot.</p>
 *
 * <p><b>Consumables are spent on summon and returned on store.</b> Nothing else gives one back, so
 * a vehicle that is destroyed, or that the player walks away from and never stores, is simply gone
 * - the count was already decremented when it was called.</p>
 *
 * <p>The summoned entity is tracked by UUID rather than by a reference, so it survives a relog and
 * a restart: the mod looks the UUID up across the loaded levels and forgets it if the entity is
 * gone.</p>
 */
public final class VehicleGarage {

    private VehicleGarage() {
    }

    private static final String KEY_OWNED = "mlum:vehicles";
    private static final String KEY_ACTIVE = "mlum:vehicle_active";
    private static final String KEY_ACTIVE_ENTRY = "mlum:vehicle_active_entry";
    private static final String KEY_COOLDOWN = "mlum:vehicle_cooldown";

    private static CompoundTag persistentRoot(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /* ------------------------------------------------------------------ owned */

    public static List<VehicleEntry> owned(Player player) {
        CompoundTag persisted = persistentRoot(player);
        if (!persisted.contains(KEY_OWNED, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = persisted.getList(KEY_OWNED, Tag.TAG_COMPOUND);
        List<VehicleEntry> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            VehicleEntry entry = VehicleEntry.load(list.getCompound(i));
            if (!entry.entityId().isEmpty()) {
                out.add(entry);
            }
        }
        return out;
    }

    /**
     * The id of the vehicle currently spawned, or empty.
     *
     * <p>Needed by {@link #store} for one specific case: summoning your <i>last</i> consumable takes
     * the count to zero, and dropping a zero-count entry there would delete the row while the
     * vehicle was still out - leaving no Store button, and no way to get it back. A spent entry is
     * kept for exactly as long as it is the one on the ground.</p>
     */
    public static String activeEntryId(Player player) {
        CompoundTag tag = persistentRoot(player);
        if (!tag.contains(KEY_ACTIVE_ENTRY, Tag.TAG_COMPOUND)) {
            return "";
        }
        return tag.getCompound(KEY_ACTIVE_ENTRY).getString("Entity");
    }

    private static void store(Player player, List<VehicleEntry> entries) {
        String active = activeEntryId(player);
        ListTag list = new ListTag();
        for (VehicleEntry entry : entries) {
            if (entry.inStock() || entry.entityId().equalsIgnoreCase(active)) {
                list.add(entry.save());
            }
        }
        persistentRoot(player).put(KEY_OWNED, list);
    }

    /** Drops spent consumables once nothing is holding them open. */
    private static void prune(Player player) {
        List<VehicleEntry> entries = new ArrayList<>(owned(player));
        entries.removeIf(entry -> !entry.inStock());
        ListTag list = new ListTag();
        for (VehicleEntry entry : entries) {
            list.add(entry.save());
        }
        persistentRoot(player).put(KEY_OWNED, list);
    }

    @Nullable
    public static VehicleEntry find(Player player, String entityId) {
        for (VehicleEntry entry : owned(player)) {
            if (entry.entityId().equalsIgnoreCase(entityId)) {
                return entry;
            }
        }
        return null;
    }

    public static boolean owns(Player player, String entityId) {
        return find(player, entityId) != null;
    }

    /**
     * Adds stock. A persistent grant the player already holds is refused; a consumable grant adds
     * to the count, which is how {@code give consumable ... 5} tops someone up.
     */
    public static boolean give(ServerPlayer player, VehicleEntry incoming) {
        List<VehicleEntry> entries = new ArrayList<>(owned(player));

        for (int i = 0; i < entries.size(); i++) {
            VehicleEntry existing = entries.get(i);
            if (!existing.entityId().equalsIgnoreCase(incoming.entityId())) {
                continue;
            }
            if (existing.kind() != incoming.kind()) {
                return false;   // changing a deed into stock, or back, needs an explicit take first
            }
            if (!incoming.isConsumable()) {
                return false;   // already owns the deed
            }
            entries.set(i, existing.withCount(existing.count() + incoming.count()));
            store(player, entries);
            sync(player);
            return true;
        }

        if (entries.size() >= MlumConfig.maxOwnedVehicles()) {
            return false;
        }
        entries.add(incoming);
        store(player, entries);
        sync(player);
        return true;
    }

    public static boolean take(ServerPlayer player, String entityId) {
        List<VehicleEntry> entries = new ArrayList<>(owned(player));
        boolean removed = entries.removeIf(entry -> entry.entityId().equalsIgnoreCase(entityId));
        if (removed) {
            store(player, entries);
            sync(player);
        }
        return removed;
    }

    public static void clear(ServerPlayer player) {
        persistentRoot(player).remove(KEY_OWNED);
        sync(player);
    }

    /* --------------------------------------------------------- the active one */

    @Nullable
    public static UUID activeId(Player player) {
        CompoundTag tag = persistentRoot(player);
        return tag.hasUUID(KEY_ACTIVE) ? tag.getUUID(KEY_ACTIVE) : null;
    }

    /** Searches every loaded level, because the player may have left the one they summoned in. */
    @Nullable
    public static Entity findActive(ServerPlayer player) {
        UUID id = activeId(player);
        if (id == null || player.getServer() == null) {
            return null;
        }
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(id);
            if (entity != null && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private static void clearActive(Player player) {
        CompoundTag tag = persistentRoot(player);
        tag.remove(KEY_ACTIVE);
        tag.remove(KEY_ACTIVE_ENTRY);
    }

    /**
     * Forgets the vehicle that was out, because it no longer exists.
     *
     * <p>Called when the entity is destroyed, not when it merely stops being loaded - see
     * {@link VehicleDestruction}. Before this, "is one out?" was answered by whether a UUID had been
     * written down, so a plane that blew up left the record behind forever: the garage went on
     * saying a vehicle was out, the Store button had nothing to store, and <b>no further vehicle
     * could ever be summoned</b>.</p>
     */
    public static void onDestroyed(ServerPlayer player) {
        if (activeId(player) == null) {
            return;
        }
        clearActive(player);
        // The cooldown is deliberately left running. Losing a vehicle should not be a faster way to
        // get the next one out than putting it away properly.
        sync(player);
        com.barbwra.mlum.util.Feedback.bad(player, "انهدمت مركبتك");
    }

    /** Removes the spawned vehicle without returning any stock. Used by admin take and clearall. */
    public static boolean despawnActive(ServerPlayer player) {
        Entity active = findActive(player);
        clearActive(player);
        if (active == null) {
            return false;
        }
        active.ejectPassengers();
        active.discard();
        return true;
    }

    /**
     * Puts the vehicle away properly. A consumable comes back into stock here, and only here.
     *
     * <p>If the entity is already gone - destroyed, or cleaned up while the chunk was unloaded -
     * there is nothing to return, which is exactly the "not stored properly" case.</p>
     */
    public static boolean storeActive(ServerPlayer player) {
        Entity active = findActive(player);
        CompoundTag tag = persistentRoot(player);
        VehicleEntry returned = tag.contains(KEY_ACTIVE_ENTRY, Tag.TAG_COMPOUND)
                ? VehicleEntry.load(tag.getCompound(KEY_ACTIVE_ENTRY))
                : null;

        clearActive(player);
        if (active == null) {
            // destroyed, or gone with an unloaded chunk - a consumable spent on it is simply lost
            prune(player);
            sync(player);
            return false;
        }
        active.ejectPassengers();
        active.discard();

        if (returned != null && returned.isConsumable()) {
            returnToStock(player, returned);
        } else {
            prune(player);
            sync(player);
        }
        return true;
    }

    /**
     * Puts one consumable back after a successful store.
     *
     * <p>Deliberately not {@link #give}: that enforces the ownership cap, and refusing a vehicle
     * that is coming <i>back</i> would destroy it - the entity has already been discarded by the
     * time this runs.</p>
     */
    private static void returnToStock(ServerPlayer player, VehicleEntry returned) {
        List<VehicleEntry> entries = new ArrayList<>(owned(player));
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).entityId().equalsIgnoreCase(returned.entityId())) {
                entries.set(i, entries.get(i).withCount(entries.get(i).count() + 1));
                store(player, entries);
                sync(player);
                return;
            }
        }
        entries.add(returned.withCount(1));
        store(player, entries);
        sync(player);
    }

    /* --------------------------------------------------------------- summoning */

    /** Why a summon was refused, so the screen can say something specific. */
    public enum Result {
        OK, NOT_OWNED, UNKNOWN_ENTITY, COMBAT_LOCKED, COOLDOWN, NO_SPACE,
        WRONG_DIMENSION, BLOCKED_ZONE, ALREADY_OUT, FAILED
    }

    public static Result summon(ServerPlayer player, String entityId) {
        VehicleEntry entry = find(player, entityId);
        if (entry == null || !entry.inStock()) {
            return Result.NOT_OWNED;
        }
        // One at a time, and refusing beats replacing: silently despawning the vehicle already out
        // would destroy it with no refund, and with a zero summon cooldown two fast clicks would
        // spend two units for one vehicle.
        if (activeId(player) != null) {
            return Result.ALREADY_OUT;
        }
        if (CombatTracker.isLocked(player)) {
            return Result.COMBAT_LOCKED;
        }
        if (cooldownTicks(player) > 0L) {
            return Result.COOLDOWN;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return Result.FAILED;
        }

        Result place = checkPlace(player, level);
        if (place != Result.OK) {
            return place;
        }

        EntityType<?> type = entry.type();
        if (type == null) {
            return Result.UNKNOWN_ENTITY;
        }

        // everything that can fail happens before the old vehicle is touched; the entity is made
        // first (not yet in the world) so the spot is found from its real size, not the type's
        Entity entity = type.create(level);
        if (entity == null) {
            return Result.FAILED;
        }
        Vec3 spot = findSpawnSpot(player, level, entity);
        if (spot == null) {
            return Result.NO_SPACE;
        }
        entity.moveTo(spot.x, spot.y, spot.z, player.getYRot(), 0.0F);

        // Tame, saddle, dye and upgrade before the entity is in the world, so a horse is never
        // briefly wild and never briefly rideable by whoever is standing closest.
        MountSetup.onSummon(player, entity);
        // Stamped before the entity is in the world, so there is never a tick in which it exists
        // unclaimed and the nearest player could take the wheel.
        VehicleOwnership.claim(player, entity);
        // Locked to its owner, and with a full tank. Both before it exists, for the same reason as
        // the claim. setMode also forces the vehicle mod's own blind lock off - see VehicleLock.
        VehicleLock.setMode(entity, VehicleLock.Mode.LOCKED);
        if (MlumConfig.infiniteVehicleEnergy()) {
            com.barbwra.mlum.compat.SbwCompat.refuel(entity);
        }

        if (!level.addFreshEntity(entity)) {
            return Result.FAILED;
        }

        // The active record is written FIRST and the count spent second. store() keeps a
        // zero-count consumable alive only while it is the active one, so doing this the other way
        // round deleted the row of the very last unit and left the vehicle out with no Store button.
        CompoundTag tag = persistentRoot(player);
        tag.putUUID(KEY_ACTIVE, entity.getUUID());
        tag.put(KEY_ACTIVE_ENTRY, entry.withCount(1).save());
        tag.putLong(KEY_COOLDOWN, level.getGameTime() + (long) MlumConfig.summonCooldownSeconds() * 20L);

        if (entry.isConsumable()) {
            List<VehicleEntry> entries = new ArrayList<>(owned(player));
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).entityId().equalsIgnoreCase(entry.entityId())) {
                    entries.set(i, entries.get(i).withCount(entries.get(i).count() - 1));
                    break;
                }
            }
            store(player, entries);
        }
        return Result.OK;
    }

    /**
     * Where a vehicle may be called: the allowed dimensions, and outside every blacklisted sphere.
     *
     * <p>Measured from the <b>player</b>, not the spawn spot, so standing just outside a safe zone
     * and aiming the vehicle into it does not work.</p>
     */
    public static Result checkPlace(ServerPlayer player, ServerLevel level) {
        String dimension = level.dimension().location().toString();
        if (!MlumConfig.summonDimensions().contains(dimension)) {
            return Result.WRONG_DIMENSION;
        }
        for (MlumConfig.SummonZone zone : MlumConfig.summonBlacklist()) {
            if (zone.contains(player.getX(), player.getY(), player.getZ())) {
                return Result.BLOCKED_ZONE;
            }
        }
        return Result.OK;
    }

    public static long cooldownTicks(ServerPlayer player) {
        CompoundTag tag = persistentRoot(player);
        if (!tag.contains(KEY_COOLDOWN)) {
            return 0L;
        }
        return Math.max(0L, tag.getLong(KEY_COOLDOWN) - player.level().getGameTime());
    }

    /**
     * Walks outwards from just in front of the player looking for room for the vehicle's own
     * bounding box, so a car never spawns inside a wall or on the player's head.
     */
    @Nullable
    /**
     * Somewhere beside the player for the whole vehicle, never on them.
     *
     * <p>The footprint is the entity's own box - a tank's is several blocks wide, and a model often
     * reaches past its box - so the centre is kept at least half the vehicle's diagonal plus a
     * two-block margin from the player, and the box (with a block of headroom) must be clear of
     * blocks, of the player, and of anyone else standing there. Right and left of where the player
     * faces are tried first, then ahead, the diagonals and behind, moving out a block at a time.</p>
     */
    private static Vec3 findSpawnSpot(ServerPlayer player, ServerLevel level, Entity entity) {
        double width = Math.max(1.0D, entity.getBbWidth());
        double height = Math.max(1.0D, entity.getBbHeight());
        double half = width / 2.0D;
        double reach = half * Math.sqrt(2.0D) + player.getBbWidth() / 2.0D + 2.0D;
        AABB keepOut = player.getBoundingBox().inflate(1.5D, 2.0D, 1.5D);
        float yaw = player.getYRot();
        // degrees off the facing: right, left, ahead, the diagonals ahead, behind them, behind
        float[] turns = {90.0F, -90.0F, 0.0F, 45.0F, -45.0F, 135.0F, -135.0F, 180.0F};
        for (double distance = reach; distance <= reach + 8.0D; distance += 1.0D) {
            for (float turn : turns) {
                Vec3 dir = Vec3.directionFromRotation(0.0F, yaw + turn);
                Vec3 target = player.position().add(dir.scale(distance));
                BlockPos base = BlockPos.containing(target);
                for (int dy = 2; dy >= -3; dy--) {
                    double y = base.getY() + dy;
                    AABB box = new AABB(target.x - half, y, target.z - half, target.x + half, y + height, target.z + half);
                    if (box.intersects(keepOut)) {
                        continue;
                    }
                    boolean supported = !level.noCollision(box.move(0.0D, -0.2D, 0.0D));
                    boolean clear = level.noCollision(box.expandTowards(0.0D, 1.0D, 0.0D));
                    if (supported && clear && level.getEntities(entity, box.inflate(0.5D),
                            e -> e instanceof net.minecraft.world.entity.LivingEntity).isEmpty()) {
                        return new Vec3(target.x, y, target.z);
                    }
                }
            }
        }
        return null;
    }

    /* -------------------------------------------------------------------- sync */

    public static void sync(ServerPlayer player) {
        ModNetwork.sendVehicles(player, owned(player),
                activeId(player) != null,
                CombatTracker.isLocked(player),
                cooldownTicks(player),
                placeBlockKey(player),
                activeId(player) != null ? activeEntryId(player) : "");
    }

    /**
     * The translation key for whichever placement rule currently forbids a summon, or empty.
     *
     * <p>Sent with every sync so the button can grey out and say why. The client cannot work this
     * out - it does not know the allowed dimensions or the blacklisted zones - and without it the
     * button looked enabled in the Nether and inside every safe zone.</p>
     */
    private static String placeBlockKey(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return "";
        }
        return switch (checkPlace(player, level)) {
            case WRONG_DIMENSION -> "gui.mlum.vehicle.fail.wrong_dimension";
            case BLOCKED_ZONE -> "gui.mlum.vehicle.fail.blocked_zone";
            default -> "";
        };
    }
}
