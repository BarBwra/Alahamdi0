package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.hud.field.SearchSpinner;
import com.barbwra.mlum.network.C2SLootCancel;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CLootSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The search as this client sees it: the server's last word on progress, the spinner, and the
 * hands (see {@link RummageHands}).
 *
 * <p>One right click starts a search; the button does not need holding. A left click calls it off.
 * When a noise has the search stalled the spinner stops and turns red for the length of the stall,
 * so it reads as "something went wrong" without a word on screen.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ClientLootSearch {

    private ClientLootSearch() {
    }

    private static BlockPos pos;
    private static float progress;
    private static boolean fast;
    private static boolean paused;
    private static long startedAt;
    private static final HudPen PEN = new HudPen();

    public static void update(S2CLootSearch msg) {
        switch (msg.state()) {
            case S2CLootSearch.DONE -> {
                LootMarkers.markSearched(msg.pos());
                pos = null;
            }
            case S2CLootSearch.CANCEL -> pos = null;
            default -> {
                if (pos == null || !pos.equals(msg.pos())) {
                    startedAt = Anim.now();
                }
                pos = msg.pos();
                progress = msg.progress();
                fast = msg.fast();
                paused = msg.paused() || msg.state() == S2CLootSearch.NOISE;
            }
        }
    }

    public static BlockPos activePos() {
        return pos;
    }

    public static boolean fast() {
        return fast;
    }

    public static void clear() {
        pos = null;
    }

    /** A left click calls the search off at once, without waiting for the server to notice. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pos == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            pos = null;
            return;
        }
        if (mc.screen == null && mc.options.keyAttack.isDown()) {
            pos = null;
            ModNetwork.CHANNEL.sendToServer(C2SLootCancel.INSTANCE);
        }
    }

    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        if (pos == null || HudVisibility.hidden()) {
            return;
        }
        HudPen pen = PEN;
        pen.begin(graphics);
        try {
            SearchSpinner.draw(pen, width / 2.0F, height / 2.0F + height * 0.075F, height, startedAt,
                    fast ? 420.0F : 800.0F, paused, progress, MlumConfig.fieldHudAccent());
        } finally {
            pen.end();
        }
    }
}
