package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.bag.BackpackAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A copy of what every player carried at each of their last ten deaths, so a death to a bug or a
 * lag spike can be undone from the admin panel.
 *
 * <p>Taken at the moment of death, before anything drops: the whole inventory, armour, offhand and
 * the worn backpack with its contents. Restoring hands a copy back - into the inventory where there
 * is room, at their feet where there is not - and marks that death as restored, so it cannot be
 * paid out twice.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DeathArchive extends SavedData {

    private static final String FILE = "mlum_deaths";
    private static final int KEEP = 10;

    public static final class Death {
        public final long at;
        public final String cause;
        public final String place;
        public final List<ItemStack> items;
        public boolean restored;

        Death(long at, String cause, String place, List<ItemStack> items, boolean restored) {
            this.at = at;
            this.cause = cause;
            this.place = place;
            this.items = items;
            this.restored = restored;
        }
    }

    private final Map<UUID, List<Death>> deaths = new HashMap<>();

    public static DeathArchive get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(DeathArchive::load, DeathArchive::new, FILE);
    }

    public List<Death> of(UUID player) {
        return deaths.getOrDefault(player, List.of());
    }

    /** Lowest and not cancelled: a real death, after the downed system has had its say. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        List<ItemStack> items = new ArrayList<>();
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                items.add(s.copy());
            }
        }
        ItemStack pack = BackpackAccess.worn(player);
        if (!pack.isEmpty()) {
            items.add(pack.copy());
        }
        if (items.isEmpty()) {
            return;
        }
        String cause = event.getSource().getLocalizedDeathMessage(player).getString();
        String place = player.level().dimension().location().getPath() + " "
                + player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
        DeathArchive archive = get(player.server);
        List<Death> list = archive.deaths.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        list.add(0, new Death(System.currentTimeMillis(), cause, place, items, false));
        while (list.size() > KEEP) {
            list.remove(list.size() - 1);
        }
        archive.setDirty();
    }

    /** Hands death {@code index} back to its owner. False when already restored or not there. */
    public boolean restore(ServerPlayer owner, int index) {
        List<Death> list = deaths.get(owner.getUUID());
        if (list == null || index < 0 || index >= list.size() || list.get(index).restored) {
            return false;
        }
        Death d = list.get(index);
        for (ItemStack s : d.items) {
            ItemStack copy = s.copy();
            if (!owner.getInventory().add(copy) && !copy.isEmpty()) {
                owner.drop(copy, false);
            }
        }
        d.restored = true;
        setDirty();
        return true;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag all = new ListTag();
        for (Map.Entry<UUID, List<Death>> e : deaths.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Id", e.getKey());
            ListTag ds = new ListTag();
            for (Death d : e.getValue()) {
                CompoundTag t = new CompoundTag();
                t.putLong("At", d.at);
                t.putString("Cause", d.cause);
                t.putString("Place", d.place);
                t.putBoolean("Restored", d.restored);
                ListTag items = new ListTag();
                for (ItemStack s : d.items) {
                    items.add(s.save(new CompoundTag()));
                }
                t.put("Items", items);
                ds.add(t);
            }
            p.put("Deaths", ds);
            all.add(p);
        }
        tag.put("Players", all);
        return tag;
    }

    public static DeathArchive load(CompoundTag tag) {
        DeathArchive a = new DeathArchive();
        ListTag all = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < all.size(); i++) {
            CompoundTag p = all.getCompound(i);
            List<Death> list = new ArrayList<>();
            ListTag ds = p.getList("Deaths", Tag.TAG_COMPOUND);
            for (int k = 0; k < ds.size(); k++) {
                CompoundTag t = ds.getCompound(k);
                List<ItemStack> items = new ArrayList<>();
                ListTag it = t.getList("Items", Tag.TAG_COMPOUND);
                for (int j = 0; j < it.size(); j++) {
                    ItemStack s = ItemStack.of(it.getCompound(j));
                    if (!s.isEmpty()) {
                        items.add(s);
                    }
                }
                list.add(new Death(t.getLong("At"), t.getString("Cause"), t.getString("Place"), items, t.getBoolean("Restored")));
            }
            a.deaths.put(p.getUUID("Id"), list);
        }
        return a;
    }
}
