package com.barbwra.mlum.warehouse.data;

import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.market.MarketState;
import com.barbwra.mlum.warehouse.mission.Mission;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The single persistent store: every warehouse, every mission in flight, the market, and the global
 * event clock.
 *
 * <p>Backed by vanilla {@link SavedData} on the overworld, which means it is written by the same
 * autosave that writes the world, on the same schedule, with the same crash guarantees, and it
 * needs no driver, no connection pool and no shaded dependency. The prototype kept all of this in
 * Skript's single flat {@code variables.csv} - one file, one writer, no transactions - where a
 * crash mid-flush was an economy rollback for the whole server.</p>
 *
 * <p><b>Missions are keyed by runner, not by id.</b> A player can only have one delivery in flight,
 * and every lookup the game actually performs starts from a player: did this player who just died
 * have cargo, is this player who just logged in mid-run, does this player who clicked the terminal
 * already have a mission open. Keying by mission id would mean scanning for all of them.</p>
 *
 * <p>Everything here is touched only from the server thread. The write-behind concerns from the
 * original blueprint disappear with SQLite: {@link #setDirty()} is a flag, not I/O.</p>
 */
public final class WarehouseData extends SavedData {

    private static final String FILE = "mwh_warehouses";

    private final Map<UUID, Warehouse> warehouses = new LinkedHashMap<>();
    private final Map<UUID, Mission> missions = new LinkedHashMap<>();
    private MarketState market = new MarketState();
    private long globalEventEndsAt;

    /* --------------------------------------------------------------- lookup */

    public static WarehouseData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(WarehouseData::load, WarehouseData::new, FILE);
    }

    /* ------------------------------------------------------------ warehouses */

    public Warehouse warehouse(UUID owner) {
        return warehouses.get(owner);
    }

    public boolean hasWarehouse(UUID owner) {
        return warehouses.containsKey(owner);
    }

    public Collection<Warehouse> warehouses() {
        return warehouses.values();
    }

    /** Creates and registers a warehouse. Callers gate this on the setup quest, not this method. */
    public Warehouse createWarehouse(UUID owner, String ownerName, long now) {
        Warehouse warehouse = new Warehouse(owner, ownerName, now);
        warehouses.put(owner, warehouse);
        setDirty();
        return warehouse;
    }

    public Warehouse removeWarehouse(UUID owner) {
        Warehouse removed = warehouses.remove(owner);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /* --------------------------------------------------------------- missions */

    public Mission mission(UUID runner) {
        return missions.get(runner);
    }

    public void putMission(Mission mission) {
        missions.put(mission.runner(), mission);
        setDirty();
    }

    public Mission removeMission(UUID runner) {
        Mission removed = missions.remove(runner);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** A snapshot, safe to iterate while the state machine mutates the live map. */
    public List<Mission> activeMissions() {
        return new ArrayList<>(missions.values());
    }

    /* ----------------------------------------------------------------- market */

    public MarketState market() {
        return market;
    }

    /* ----------------------------------------------------------- global event */

    public boolean isEventActive(long now) {
        return globalEventEndsAt > now;
    }

    public long eventEndsAt() {
        return globalEventEndsAt;
    }

    public void startEvent(long endsAt) {
        this.globalEventEndsAt = endsAt;
        setDirty();
    }

    public void stopEvent() {
        this.globalEventEndsAt = 0L;
        setDirty();
    }

    /* -------------------------------------------------------------------- nbt */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag warehouseList = new ListTag();
        for (Warehouse warehouse : warehouses.values()) {
            warehouseList.add(warehouse.save());
        }
        tag.put("warehouses", warehouseList);

        ListTag missionList = new ListTag();
        for (Mission mission : missions.values()) {
            missionList.add(mission.save());
        }
        tag.put("missions", missionList);

        tag.put("market", market.save());
        tag.putLong("eventEnds", globalEventEndsAt);
        return tag;
    }

    public static WarehouseData load(CompoundTag tag) {
        WarehouseData data = new WarehouseData();

        ListTag warehouseList = tag.getList("warehouses", Tag.TAG_COMPOUND);
        for (int i = 0; i < warehouseList.size(); i++) {
            Warehouse warehouse = Warehouse.load(warehouseList.getCompound(i));
            data.warehouses.put(warehouse.owner(), warehouse);
        }

        ListTag missionList = tag.getList("missions", Tag.TAG_COMPOUND);
        for (int i = 0; i < missionList.size(); i++) {
            Mission mission = Mission.load(missionList.getCompound(i));
            data.missions.put(mission.runner(), mission);
        }

        if (tag.contains("market")) {
            data.market = MarketState.load(tag.getCompound("market"));
        }
        data.globalEventEndsAt = tag.getLong("eventEnds");
        return data;
    }
}
