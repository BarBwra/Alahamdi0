package com.barbwra.mlum.client.dealer;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.C2SDealer;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the client knows of the dealership: the stock, the buyer, and who else is browsing. */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ClientDealer {

    private ClientDealer() {
    }

    public record Category(int id, String name) {
    }

    public record Listing(int id, String entity, String name, int category, long price, int level, boolean limited,
                          int count, long added) {

        /** On sale for less than a week. */
        public boolean fresh() {
            return added > 0 && System.currentTimeMillis() - added < 7L * 86_400_000L;
        }
    }

    /** What the buyer already holds of an entity type: a deed, or uses left. */
    public record Owned(boolean consumable, int count) {
    }

    public static final List<Category> CATEGORIES = new ArrayList<>();
    public static final List<Listing> LISTINGS = new ArrayList<>();
    public static final Map<String, Owned> OWNED = new HashMap<>();
    public static long balance;
    public static int level;
    public static boolean edit;
    /** Bumped on every update, so the screen can tell its selection may have gone. */
    public static int version;

    private static final Set<Integer> BROWSING = new HashSet<>();

    public static void receive(String kind, CompoundTag tag) {
        switch (kind) {
            case "open" -> {
                read(tag);
                Minecraft.getInstance().setScreen(new DealerScreen());
            }
            case "update" -> read(tag);
            case "result" -> {
                if (Minecraft.getInstance().screen instanceof DealerScreen screen) {
                    screen.result(tag.getBoolean("Ok"), tag.getString("Msg"));
                }
            }
            case "browsing" -> {
                BROWSING.clear();
                for (int id : tag.getIntArray("Ids")) {
                    BROWSING.add(id);
                }
            }
            default -> {
            }
        }
    }

    private static void read(CompoundTag tag) {
        CATEGORIES.clear();
        ListTag cats = tag.getList("Cats", Tag.TAG_COMPOUND);
        for (int i = 0; i < cats.size(); i++) {
            CompoundTag t = cats.getCompound(i);
            CATEGORIES.add(new Category(t.getInt("Id"), t.getString("Name")));
        }
        LISTINGS.clear();
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            LISTINGS.add(new Listing(t.getInt("Id"), t.getString("Entity"), t.getString("Name"), t.getInt("Cat"),
                    t.getLong("Price"), t.getInt("Level"), t.getBoolean("Limited"), t.getInt("Count"), t.getLong("Added")));
        }
        OWNED.clear();
        ListTag owned = tag.getList("Owned", Tag.TAG_COMPOUND);
        for (int i = 0; i < owned.size(); i++) {
            CompoundTag t = owned.getCompound(i);
            OWNED.put(t.getString("E"), new Owned(t.getBoolean("C"), t.getInt("N")));
        }
        balance = tag.getLong("Balance");
        level = tag.getInt("Level");
        edit = tag.getBoolean("Edit");
        version++;
    }

    public static Category category(int id) {
        for (Category c : CATEGORIES) {
            if (c.id() == id) {
                return c;
            }
        }
        return null;
    }

    public static void send(String action, CompoundTag data) {
        ModNetwork.CHANNEL.sendToServer(new C2SDealer(action, data));
    }

    /** Someone else in the showroom is not drawn: browsing is private. */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!BROWSING.isEmpty() && event.getEntity() != Minecraft.getInstance().player
                && BROWSING.contains(event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        BROWSING.clear();
        CATEGORIES.clear();
        LISTINGS.clear();
        OWNED.clear();
    }
}
