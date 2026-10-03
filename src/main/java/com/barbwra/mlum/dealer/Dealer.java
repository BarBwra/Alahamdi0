package com.barbwra.mlum.dealer;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.admin.Perms;
import com.barbwra.mlum.admin.Staff;
import com.barbwra.mlum.bag.WalletService;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CDealer;
import com.barbwra.mlum.vehicle.VehicleEntry;
import com.barbwra.mlum.vehicle.VehicleGarage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The vehicle dealership.
 *
 * <h2>Opening</h2>
 * <p>Only {@code /mlum dealer open <player>} opens it - from the console, an operator, or a command
 * block at the showroom's door. That starts a <i>session</i> for the player, and nothing can be
 * bought without one, so a modified client cannot shop from anywhere by sending the packet itself.
 * The session ends when the screen closes, the player leaves, or after half an hour.</p>
 *
 * <h2>Paying</h2>
 * <p>The price comes out of the bag's balance ({@link WalletService}), the same number the bag's top
 * bar shows. The money is taken first and given back if the garage then refuses the vehicle, so a
 * failed purchase never costs anything and a successful one is never free.</p>
 *
 * <h2>Privacy</h2>
 * <p>The vehicle on show is drawn only in the buyer's own screen - nothing is spawned in the world -
 * and while someone is browsing, every other client stops drawing them (see {@code ClientDealer}).</p>
 *
 * <h2>Editing</h2>
 * <p>Operators, and any staff rank holding {@code dealer.edit}, edit the stock from the same screen.
 * Every change is checked here again and reaches everyone who has the dealership open at once.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Dealer {

    private Dealer() {
    }

    private static final long SESSION_MS = 30L * 60L * 1000L;
    private static final Map<UUID, Long> SESSIONS = new HashMap<>();

    public static boolean canEdit(ServerPlayer player) {
        return Staff.has(player, Perms.DEALER);
    }

    private static boolean inSession(ServerPlayer player) {
        Long at = SESSIONS.get(player.getUUID());
        return at != null && System.currentTimeMillis() - at < SESSION_MS;
    }

    /* ------------------------------------------------------------------ opening */

    public static void open(ServerPlayer player) {
        SESSIONS.put(player.getUUID(), System.currentTimeMillis());
        send(player, "open", payload(player));
        broadcastBrowsing(player.getServer());
    }

    private static void close(ServerPlayer player) {
        if (SESSIONS.remove(player.getUUID()) != null) {
            broadcastBrowsing(player.getServer());
        }
    }

    /** The stock, and what this player brings to it: money, level, what they already own. */
    private static CompoundTag payload(ServerPlayer player) {
        CompoundTag tag = DealerData.get(player.getServer()).catalog();
        tag.putLong("Balance", WalletService.balance(player));
        tag.putInt("Level", player.experienceLevel);
        tag.putBoolean("Edit", canEdit(player));
        ListTag owned = new ListTag();
        for (VehicleEntry e : VehicleGarage.owned(player)) {
            CompoundTag t = new CompoundTag();
            t.putString("E", e.entityId());
            t.putBoolean("C", e.isConsumable());
            t.putInt("N", e.count());
            owned.add(t);
        }
        tag.put("Owned", owned);
        return tag;
    }

    /** Everyone with the dealership open sees a change the moment it is made. */
    private static void refreshAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (inSession(p)) {
                send(p, "update", payload(p));
            }
        }
    }

    private static void broadcastBrowsing(MinecraftServer server) {
        if (server == null) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (inSession(p)) {
                ids.add(p.getId());
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.put("Ids", new IntArrayTag(ids));
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new S2CDealer("browsing", tag));
    }

    private static void send(ServerPlayer player, String kind, CompoundTag tag) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CDealer(kind, tag));
    }

    private static void result(ServerPlayer player, boolean ok, String message) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Ok", ok);
        tag.putString("Msg", message);
        send(player, "result", tag);
    }

    /* ------------------------------------------------------------------ actions */

    public static void handle(ServerPlayer player, String action, CompoundTag data) {
        if (action.equals("close")) {
            close(player);
            return;
        }
        if (!inSession(player)) {
            return;
        }
        if (action.equals("buy")) {
            buy(player, data.getInt("Id"));
            return;
        }
        if (!canEdit(player)) {
            result(player, false, "ما عندك صلاحية تعدّل المعرض");
            return;
        }
        DealerData d = DealerData.get(player.getServer());
        switch (action) {
            case "cat.save" -> {
                String name = clean(data.getString("Name"), 24);
                if (name.isEmpty()) {
                    result(player, false, "اكتب اسم القسم");
                    return;
                }
                DealerData.Category c = data.getInt("Id") > 0 ? d.category(data.getInt("Id")) : null;
                if (c == null) {
                    d.addCategory(name);
                    result(player, true, "انضاف القسم: " + name);
                } else {
                    c.name = name;
                    d.setDirty();
                    result(player, true, "تعدّل القسم");
                }
            }
            case "cat.delete" -> {
                if (!d.removeCategory(data.getInt("Id"))) {
                    result(player, false, "القسم فيه مركبات - انقلها أو احذفها أول");
                    return;
                }
                result(player, true, "انحذف القسم");
            }
            case "veh.save" -> {
                String entity = data.getString("Entity").trim().toLowerCase(java.util.Locale.ROOT);
                ResourceLocation id = ResourceLocation.tryParse(entity);
                if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
                    result(player, false, "ما لقيت مركبة بهذا الـ id: " + entity);
                    return;
                }
                String name = clean(data.getString("Name"), 40);
                if (name.isEmpty()) {
                    result(player, false, "اكتب اسم المركبة");
                    return;
                }
                int cat = data.getInt("Cat");
                if (d.category(cat) == null) {
                    result(player, false, "اختر قسم للمركبة - سو قسم أول إذا ما فيه");
                    return;
                }
                DealerData.Listing l = data.getInt("Id") > 0 ? d.listing(data.getInt("Id")) : null;
                boolean added = l == null;
                if (added) {
                    l = d.addListing();
                }
                l.entity = id.toString();
                l.name = name;
                l.category = cat;
                l.price = Math.max(0L, Math.min(1_000_000_000_000L, data.getLong("Price")));
                l.level = Math.max(0, Math.min(10_000, data.getInt("Level")));
                l.limited = data.getBoolean("Limited");
                l.count = Math.max(1, Math.min(VehicleEntry.MAX_COUNT, data.getInt("Count")));
                d.setDirty();
                result(player, true, added ? "انضافت المركبة: " + name : "تعدّلت المركبة");
            }
            case "veh.delete" -> {
                if (d.removeListing(data.getInt("Id"))) {
                    result(player, true, "انحذفت المركبة من المعرض");
                }
            }
            case "veh.move" -> d.move(data.getInt("Id"), data.getInt("By") < 0 ? -1 : 1);
            default -> {
                return;
            }
        }
        refreshAll(player.getServer());
    }

    private static void buy(ServerPlayer player, int listingId) {
        DealerData.Listing l = DealerData.get(player.getServer()).listing(listingId);
        if (l == null) {
            result(player, false, "هذي المركبة ما عادت في المعرض");
            return;
        }
        if (player.experienceLevel < l.level) {
            result(player, false, "تحتاج مستوى " + l.level + " - مستواك " + player.experienceLevel);
            return;
        }
        VehicleEntry entry = l.limited
                ? VehicleEntry.consumable(l.entity, l.name, Math.max(1, l.count))
                : VehicleEntry.persistent(l.entity, l.name);
        VehicleEntry existing = VehicleGarage.find(player, l.entity);
        if (existing != null && existing.kind() != entry.kind()) {
            result(player, false, "عندك هذي المركبة بنوع ثاني");
            return;
        }
        if (existing != null && !entry.isConsumable()) {
            result(player, false, "تملك هذي المركبة من قبل");
            return;
        }
        if (!WalletService.take(player, l.price)) {
            result(player, false, "رصيدك ما يكفي");
            return;
        }
        if (!VehicleGarage.give(player, entry)) {
            WalletService.give(player, l.price);
            result(player, false, "كراجك ممتلئ - ما تقدر تملك مركبات أكثر");
            return;
        }
        MlumInventory.LOGGER.info("[{}] dealer: {} bought {} ({}) for {}", MlumInventory.MODID,
                player.getGameProfile().getName(), l.name, l.entity, l.price);
        result(player, true, "مبروك! صارت " + l.name + " لك - تلقاها في قائمة مركباتك");
        send(player, "update", payload(player));
    }

    private static String clean(String raw, int max) {
        String s = raw == null ? "" : raw.replaceAll("[\\p{Cntrl}§]", "").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /* ------------------------------------------------------------------ housekeeping */

    private static int tick;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 400 != 0 || SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (SESSIONS.values().removeIf(at -> now - at >= SESSION_MS)) {
            broadcastBrowsing(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            close(player);
        }
    }

    /** A joining client is told who is browsing right now, so they are hidden from the start. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !SESSIONS.isEmpty()) {
            broadcastBrowsing(player.getServer());
        }
    }
}
