package com.barbwra.mlum.warehouse.core;

import com.barbwra.mlum.warehouse.WarehouseConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One player's whole operation: their crates, their running assembly lines, their upgrade levels
 * and their lifetime delivery record.
 *
 * <p>This is the aggregate root. Services mutate it, {@code WarehouseData} persists it, and every
 * derived number a player can see - capacity, line count, shelf life - is <b>computed here from
 * upgrade levels</b> rather than stored. The prototype kept a separate {@code amount} counter
 * alongside the list it was supposed to describe, updated it by hand at nine call sites, and every
 * loop in the system used the counter as its bound. One missed update and reads ran off the end of
 * the list forever. There is no counter here; {@code crates.size()} is the count.</p>
 */
public final class Warehouse {

    private final UUID owner;
    private String ownerName;
    private final long createdAt;

    private final Map<UUID, Crate> crates = new LinkedHashMap<>();
    private final Map<UUID, CraftJob> crafts = new LinkedHashMap<>();
    private final Map<UpgradePath, Integer> upgrades = new EnumMap<>(UpgradePath.class);

    /** Lifetime deliveries, indexed [cargo][tier]. Tier 0 is unused so tiers read naturally. */
    private final Map<CargoType, int[]> delivered = new EnumMap<>(CargoType.class);

    private boolean vip;

    /* ------------------------------------------------------------ career log */

    /**
     * One finished run, kept for the player's stats screen.
     *
     * <p>Only the last {@link #RECENT_LIMIT} are retained. A full audit trail belongs in the
     * server log, not in a save file that is read every time a terminal opens.</p>
     */
    public record DeliveryRecord(long at, int crates, int payout, boolean success, String route) {
    }

    public static final int RECENT_LIMIT = 8;

    private long totalEarned;
    private int deliveriesSucceeded;
    private int deliveriesFailed;
    private final List<DeliveryRecord> recent = new ArrayList<>();

    public Warehouse(UUID owner, String ownerName, long createdAt) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.createdAt = createdAt;
        for (CargoType type : CargoType.values()) {
            delivered.put(type, new int[6]);
        }
    }

    /* -------------------------------------------------------------- identity */

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwnerName(String name) {
        if (name != null && !name.isBlank()) {
            this.ownerName = name;
        }
    }

    public long createdAt() {
        return createdAt;
    }

    public boolean isVip() {
        return vip;
    }

    public void setVip(boolean value) {
        this.vip = value;
    }

    /* --------------------------------------------------------------- upgrades */

    public int upgradeLevel(UpgradePath path) {
        return upgrades.getOrDefault(path, 0);
    }

    public void setUpgradeLevel(UpgradePath path, int level) {
        upgrades.put(path, Math.max(0, Math.min(UpgradePath.MAX_LEVEL, level)));
    }

    public int capacity() {
        return WarehouseConfig.capacityFor(upgradeLevel(UpgradePath.LOGISTICS));
    }

    public int lines() {
        return WarehouseConfig.linesFor(upgradeLevel(UpgradePath.INDUSTRY));
    }

    public int shelfLifeDays() {
        return WarehouseConfig.shelfDaysFor(upgradeLevel(UpgradePath.LOGISTICS));
    }

    /* ----------------------------------------------------------------- crates */

    public Collection<Crate> crates() {
        return crates.values();
    }

    public Crate crate(UUID id) {
        return crates.get(id);
    }

    public int crateCount() {
        return crates.size();
    }

    public boolean hasSpace() {
        return crates.size() < capacity();
    }

    public void addCrate(Crate crate) {
        crates.put(crate.id(), crate);
    }

    public Crate removeCrate(UUID id) {
        return crates.remove(id);
    }

    /** Replaces a crate in place, keeping its identity. Used to flip the reserved flag. */
    public void replaceCrate(Crate crate) {
        if (crates.containsKey(crate.id())) {
            crates.put(crate.id(), crate);
        }
    }

    public List<Crate> spoiled(long now) {
        List<Crate> out = new ArrayList<>();
        for (Crate crate : crates.values()) {
            if (crate.isSpoiled(now)) {
                out.add(crate);
            }
        }
        return out;
    }

    /* ----------------------------------------------------------------- crafts */

    public Collection<CraftJob> crafts() {
        return crafts.values();
    }

    public CraftJob craft(UUID id) {
        return crafts.get(id);
    }

    public int craftCount() {
        return crafts.size();
    }

    public boolean hasFreeLine() {
        return crafts.size() < lines();
    }

    public void addCraft(CraftJob job) {
        crafts.put(job.id(), job);
    }

    public CraftJob removeCraft(UUID id) {
        return crafts.remove(id);
    }

    /* ------------------------------------------------------------ delivery log */

    public void recordDelivery(CargoType cargo, int tier) {
        if (tier >= 1 && tier <= 5) {
            delivered.get(cargo)[tier]++;
        }
    }

    /** Deliveries of {@code cargo} across the given tiers. This is what gates the upgrade tree. */
    public int deliveredCount(CargoType cargo, List<Integer> tiers) {
        int[] byTier = delivered.get(cargo);
        int total = 0;
        for (int tier : tiers) {
            if (tier >= 1 && tier <= 5) {
                total += byTier[tier];
            }
        }
        return total;
    }

    public int deliveredCount(CargoType cargo, int tier) {
        return tier >= 1 && tier <= 5 ? delivered.get(cargo)[tier] : 0;
    }

    public void setDeliveredCount(CargoType cargo, int tier, int value) {
        if (tier >= 1 && tier <= 5) {
            delivered.get(cargo)[tier] = Math.max(0, value);
        }
    }

    public void resetDeliveries() {
        for (CargoType type : CargoType.values()) {
            delivered.put(type, new int[6]);
        }
    }

    /* ------------------------------------------------------------ career log */

    public long totalEarned() {
        return totalEarned;
    }

    public int deliveriesSucceeded() {
        return deliveriesSucceeded;
    }

    public int deliveriesFailed() {
        return deliveriesFailed;
    }

    /** Newest first, so the stats screen reads top-down without reversing anything. */
    public List<DeliveryRecord> recent() {
        return List.copyOf(recent);
    }

    /** Success rate 0..1, or 0 when nothing has been attempted. */
    public float successRate() {
        int total = deliveriesSucceeded + deliveriesFailed;
        return total == 0 ? 0.0F : (float) deliveriesSucceeded / total;
    }

    public void recordRun(long at, int crates, int payout, boolean success, String route) {
        if (success) {
            deliveriesSucceeded++;
            totalEarned += payout;
        } else {
            deliveriesFailed++;
        }
        recent.add(0, new DeliveryRecord(at, crates, success ? payout : 0, success, route));
        while (recent.size() > RECENT_LIMIT) {
            recent.remove(recent.size() - 1);
        }
    }

    /* -------------------------------------------------------------------- nbt */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", owner);
        tag.putString("name", ownerName == null ? "" : ownerName);
        tag.putLong("created", createdAt);
        tag.putBoolean("vip", vip);

        ListTag crateList = new ListTag();
        for (Crate crate : crates.values()) {
            crateList.add(crate.save());
        }
        tag.put("crates", crateList);

        ListTag craftList = new ListTag();
        for (CraftJob job : crafts.values()) {
            craftList.add(job.save());
        }
        tag.put("crafts", craftList);

        CompoundTag upgradeTag = new CompoundTag();
        for (Map.Entry<UpgradePath, Integer> entry : upgrades.entrySet()) {
            upgradeTag.putInt(entry.getKey().id(), entry.getValue());
        }
        tag.put("upgrades", upgradeTag);

        CompoundTag statsTag = new CompoundTag();
        for (CargoType type : CargoType.values()) {
            statsTag.putIntArray(type.id(), delivered.get(type));
        }
        tag.put("delivered", statsTag);

        tag.putLong("earned", totalEarned);
        tag.putInt("runsOk", deliveriesSucceeded);
        tag.putInt("runsFail", deliveriesFailed);

        ListTag recentList = new ListTag();
        for (DeliveryRecord record : recent) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("at", record.at());
            entry.putInt("crates", record.crates());
            entry.putInt("payout", record.payout());
            entry.putBoolean("ok", record.success());
            entry.putString("route", record.route());
            recentList.add(entry);
        }
        tag.put("recent", recentList);

        return tag;
    }

    public static Warehouse load(CompoundTag tag) {
        Warehouse warehouse = new Warehouse(
                tag.getUUID("owner"),
                tag.getString("name"),
                tag.getLong("created"));
        warehouse.vip = tag.getBoolean("vip");

        ListTag crateList = tag.getList("crates", Tag.TAG_COMPOUND);
        for (int i = 0; i < crateList.size(); i++) {
            Crate crate = Crate.load(crateList.getCompound(i));
            warehouse.crates.put(crate.id(), crate);
        }

        ListTag craftList = tag.getList("crafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < craftList.size(); i++) {
            CraftJob job = CraftJob.load(craftList.getCompound(i));
            warehouse.crafts.put(job.id(), job);
        }

        CompoundTag upgradeTag = tag.getCompound("upgrades");
        for (UpgradePath path : UpgradePath.values()) {
            if (upgradeTag.contains(path.id())) {
                warehouse.upgrades.put(path, upgradeTag.getInt(path.id()));
            }
        }

        CompoundTag statsTag = tag.getCompound("delivered");
        for (CargoType type : CargoType.values()) {
            int[] raw = statsTag.getIntArray(type.id());
            int[] target = warehouse.delivered.get(type);
            System.arraycopy(raw, 0, target, 0, Math.min(raw.length, target.length));
        }

        warehouse.totalEarned = tag.getLong("earned");
        warehouse.deliveriesSucceeded = tag.getInt("runsOk");
        warehouse.deliveriesFailed = tag.getInt("runsFail");

        ListTag recentList = tag.getList("recent", Tag.TAG_COMPOUND);
        for (int i = 0; i < recentList.size(); i++) {
            CompoundTag entry = recentList.getCompound(i);
            warehouse.recent.add(new DeliveryRecord(
                    entry.getLong("at"), entry.getInt("crates"), entry.getInt("payout"),
                    entry.getBoolean("ok"), entry.getString("route")));
        }

        return warehouse;
    }
}
