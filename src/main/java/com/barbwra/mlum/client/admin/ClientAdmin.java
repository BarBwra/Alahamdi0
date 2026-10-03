package com.barbwra.mlum.client.admin;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.admin.Perms;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.network.C2SAdmin;
import com.barbwra.mlum.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The admin system on this client: what the server says this player may do, the panel's pages,
 * who is vanished, and the notices it shows (punishments, restarts).
 *
 * <p>Nothing here grants anything. The perms are only used to decide what the panel shows; every
 * button is checked again on the server.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ClientAdmin {

    private ClientAdmin() {
    }

    private static boolean op;
    private static String rank = "";
    private static int rankColor;
    private static final Set<String> PERMS = new LinkedHashSet<>();
    private static final Map<String, CompoundTag> PAGES = new HashMap<>();
    private static int version;
    private static final Set<Integer> VANISHED = new HashSet<>();

    /* the notice for a punished player, and the restart countdown */
    static CompoundTag notice;
    static long noticeAt;
    static int restartSeconds = -1;
    static long restartAt;

    public static final KeyMapping OPEN = new KeyMapping("key.mlum.admin", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, "key.categories.mlum");

    @Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(OPEN);
        }
    }

    public static void receive(String kind, CompoundTag data) {
        switch (kind) {
            case "staff" -> {
                op = data.getBoolean("Op");
                rank = data.getString("Rank");
                rankColor = data.getInt("Color");
                PERMS.clear();
                ListTag list = data.getList("Perms", Tag.TAG_STRING);
                for (int i = 0; i < list.size(); i++) {
                    PERMS.add(list.getString(i));
                }
                version++;
            }
            case "page" -> {
                PAGES.put(data.getString("Page"), data);
                version++;
            }
            case "vanish" -> {
                VANISHED.clear();
                for (int id : data.getIntArray("Ids")) {
                    VANISHED.add(id);
                }
            }
            case "notice" -> {
                notice = data;
                noticeAt = Anim.now();
            }
            case "restart" -> {
                restartSeconds = data.getInt("Seconds");
                restartAt = Anim.now();
            }
            case "ticket-open" -> Minecraft.getInstance().setScreen(new TicketScreen());
            default -> {
            }
        }
    }

    public static boolean op() {
        return op;
    }

    public static boolean staff() {
        return op || !rank.isEmpty();
    }

    public static String rank() {
        return op ? "OP" : rank;
    }

    public static int rankColor() {
        return op ? 0xE5442E : rankColor;
    }

    public static boolean has(String node) {
        if (op) {
            return true;
        }
        for (String p : PERMS) {
            if (Perms.covers(p, node)) {
                return true;
            }
        }
        return false;
    }

    public static CompoundTag page(String name) {
        return PAGES.get(name);
    }

    public static int version() {
        return version;
    }

    public static void ask(String page) {
        ask(page, new CompoundTag());
    }

    public static void ask(String page, CompoundTag extra) {
        CompoundTag tag = extra.copy();
        tag.putString("Page", page);
        send("page", tag);
    }

    public static void send(String action, CompoundTag data) {
        ModNetwork.CHANNEL.sendToServer(new C2SAdmin(action, data));
    }

    /* ================================================================== the world */

    /** A vanished admin is not drawn for anyone who may not see them. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        if (VANISHED.contains(p.getId()) && p != Minecraft.getInstance().player && !has(Perms.VANISH)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (OPEN.consumeClick()) {
            if (mc.screen == null && mc.player != null && staff() && has(Perms.PANEL)) {
                mc.setScreen(new AdminScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        op = false;
        rank = "";
        PERMS.clear();
        PAGES.clear();
        VANISHED.clear();
        notice = null;
        restartSeconds = -1;
    }
}
