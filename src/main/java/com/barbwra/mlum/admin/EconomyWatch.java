package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.bag.BackpackAccess;
import com.barbwra.mlum.bag.BackpackStore;
import com.barbwra.mlum.bag.WalletService;
import com.barbwra.mlum.compat.ShopCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spots duplication and money that appears from nowhere, and tells staff.
 *
 * <p>Once a minute every online player is weighed: their money, and what everything they carry
 * would sell for at the trader, item by item. Compared with the minute before, a jump larger than
 * the thresholds in the config raises an alert with the name, how much, and which items grew -
 * real trading and looting do not move a few hundred thousand in a minute; a dupe does. Items the
 * trader will not buy are valued at nothing, so nothing has to be listed by hand.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class EconomyWatch {

    private EconomyWatch() {
    }

    public record Alert(long at, String name, String text) {
    }

    private record Sample(long money, double value, Map<ResourceLocation, Integer> counts) {
    }

    private static final int EVERY_TICKS = 1200;
    private static final Map<UUID, Sample> LAST = new HashMap<>();
    private static final Deque<Alert> ALERTS = new ArrayDeque<>();

    public static List<Alert> alerts() {
        return List.copyOf(ALERTS);
    }

    public static void clear() {
        ALERTS.clear();
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % EVERY_TICKS != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isCreative()) {
                continue;
            }
            Sample now = sample(server, player);
            Sample before = LAST.put(player.getUUID(), now);
            if (before == null) {
                continue;
            }
            long moneyGain = now.money - before.money;
            double valueGain = now.value - before.value;
            if (moneyGain >= MlumConfig.moneyAlert()) {
                raise(server, player, "زادت فلوسه {n}" + String.format(java.util.Locale.US, "%,d", moneyGain)
                        + "{/n} في دقيقة");
            }
            if (valueGain >= MlumConfig.itemValueAlert()) {
                raise(server, player, "زادت قيمة اللي معه {n}" + String.format(java.util.Locale.US, "%,d", (long) valueGain)
                        + "{/n} في دقيقة · " + growth(before.counts, now.counts));
            }
        }
    }

    private static Sample sample(MinecraftServer server, ServerPlayer player) {
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        double value = 0.0D;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            value += weigh(server, player.getInventory().getItem(i), counts);
        }
        ItemStack pack = BackpackAccess.worn(player);
        if (!pack.isEmpty()) {
            for (ItemStack s : BackpackStore.contents(pack)) {
                value += weigh(server, s, counts);
            }
        }
        return new Sample(WalletService.balance(player), value, counts);
    }

    private static double weigh(MinecraftServer server, ItemStack s, Map<ResourceLocation, Integer> counts) {
        if (s.isEmpty()) {
            return 0.0D;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        if (id != null) {
            counts.merge(id, s.getCount(), Integer::sum);
        }
        double each = ShopCompat.sellPricePerItem(server, s);
        return each > 0.0D ? each * s.getCount() : 0.0D;
    }

    /** The three items that grew the most, as "id +n". */
    private static String growth(Map<ResourceLocation, Integer> before, Map<ResourceLocation, Integer> now) {
        StringBuilder out = new StringBuilder();
        now.entrySet().stream()
                .map(e -> Map.entry(e.getKey(), e.getValue() - before.getOrDefault(e.getKey(), 0)))
                .filter(e -> e.getValue() > 0)
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .forEach(e -> out.append(out.length() == 0 ? "" : "، ").append(e.getKey().getPath()).append(" +").append(e.getValue()));
        return out.toString();
    }

    private static void raise(MinecraftServer server, ServerPlayer player, String text) {
        String name = player.getGameProfile().getName();
        ALERTS.addFirst(new Alert(System.currentTimeMillis(), name, text));
        while (ALERTS.size() > 100) {
            ALERTS.removeLast();
        }
        Staff.tellStaff(server, Perms.ALERTS, "تنبيه تكرار: {b}" + name + "{/b} · " + text);
        MlumInventory.LOGGER.warn("[{}] economy alert: {} {}", MlumInventory.MODID, name, text);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST.remove(event.getEntity().getUUID());
    }
}
