package com.barbwra.mlum.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * One vehicle a player owns.
 *
 * <p><b>This is data, not an item.</b> Nothing is ever placed in the player's inventory - the whole
 * garage lives in the player's persistent NBT, and {@code count} is what makes a consumable
 * stackable. That keeps a helicopter out of the player's bag slots and means a vehicle can never be
 * dropped, traded or lost to a full inventory.</p>
 *
 * <p>Only the entity id is stored. The mod owns no vehicle registry - whatever
 * {@code /VehicleMenu give} names is what the player gets, so any vehicle mod works with no compile
 * dependency and no per-mod support code.</p>
 */
public record VehicleEntry(String entityId, String displayName, Kind kind, int count) {

    public static final int MAX_ID = 128;
    public static final int MAX_NAME = 64;
    public static final int MAX_COUNT = 999;

    /**
     * How owning it behaves.
     *
     * <p>{@link #PERSISTENT} is a title deed: it cannot be used up, and while it is not spawned the
     * player can always call it. {@link #CONSUMABLE} is stock: summoning spends one, storing the
     * vehicle puts one back, and losing the vehicle loses it for good.</p>
     */
    public enum Kind {
        PERSISTENT, CONSUMABLE;

        public static Kind byId(String raw) {
            return raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith("c")
                    ? CONSUMABLE : PERSISTENT;
        }
    }

    public VehicleEntry {
        entityId = clamp(entityId, MAX_ID);
        displayName = clamp(displayName, MAX_NAME);
        kind = kind == null ? Kind.PERSISTENT : kind;
        count = kind == Kind.PERSISTENT ? 1 : Math.max(0, Math.min(MAX_COUNT, count));
    }

    public static VehicleEntry persistent(String entityId, String displayName) {
        return new VehicleEntry(entityId, displayName, Kind.PERSISTENT, 1);
    }

    public static VehicleEntry consumable(String entityId, String displayName, int count) {
        return new VehicleEntry(entityId, displayName, Kind.CONSUMABLE, count);
    }

    private static String clamp(String s, int limit) {
        if (s == null) {
            return "";
        }
        return s.length() > limit ? s.substring(0, limit) : s;
    }

    public boolean isConsumable() {
        return kind == Kind.CONSUMABLE;
    }

    /** A consumable with nothing left is not summonable, and is dropped from the garage. */
    public boolean inStock() {
        return kind == Kind.PERSISTENT || count > 0;
    }

    public VehicleEntry withCount(int newCount) {
        return new VehicleEntry(entityId, displayName, kind, newCount);
    }

    @Nullable
    public ResourceLocation location() {
        return ResourceLocation.tryParse(entityId);
    }

    /** Null when the id is malformed or the mod that owns it is not installed. */
    @Nullable
    public EntityType<?> type() {
        ResourceLocation location = location();
        return location == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(location);
    }

    public boolean isAvailable() {
        return type() != null;
    }

    /** Falls back through custom name, entity type name, then the raw id, so a label always exists. */
    public Component label() {
        if (!displayName.isEmpty()) {
            return Component.literal(displayName);
        }
        EntityType<?> type = type();
        return type != null ? type.getDescription() : Component.literal(entityId);
    }

    /* ------------------------------------------------------------------- codec */

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(entityId, MAX_ID);
        buf.writeUtf(displayName, MAX_NAME);
        buf.writeByte(kind.ordinal());
        buf.writeVarInt(count);
    }

    public static VehicleEntry read(FriendlyByteBuf buf) {
        String id = buf.readUtf(MAX_ID);
        String name = buf.readUtf(MAX_NAME);
        Kind kind = Kind.values()[Math.floorMod(buf.readByte(), Kind.values().length)];
        return new VehicleEntry(id, name, kind, buf.readVarInt());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Entity", entityId);
        tag.putString("Name", displayName);
        tag.putString("Kind", kind.name());
        tag.putInt("Count", count);
        return tag;
    }

    public static VehicleEntry load(CompoundTag tag) {
        // entries written before consumables existed have no Kind, and are title deeds
        Kind kind = tag.contains("Kind") ? Kind.byId(tag.getString("Kind")) : Kind.PERSISTENT;
        int count = tag.contains("Count") ? tag.getInt("Count") : 1;
        return new VehicleEntry(tag.getString("Entity"), tag.getString("Name"), kind, count);
    }
}
