package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.hud.field.SearchSpinner;
import com.barbwra.mlum.client.ui.text.Shaped;
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

    /**
     * Under the spinner: a Shift keycap and what it does, so nobody has to be told the fast search
     * exists. Held, it turns to a warning - fast, but something may fall.
     */
    private static void shiftHint(HudPen pen, float cx, float y) {
        Shaped key = pen.pixel("SHIFT", 5.0F, 700);
        Shaped label = fast ? pen.kufi("سريع · ممكن يطيح شي ويسمعونك", 5.0F, 600)
                : pen.kufi("اضغط للتفتيش الأسرع", 5.0F, 600);
        float kw = pen.width(key) + 6.0F;
        float gap = 4.0F;
        float total = kw + gap + pen.width(label);
        float x = cx - total / 2.0F;
        int edge = fast ? 0xFFD9623F : 0xFFECE6D4;
        // the keycap: a dark key with a light rim and a heavier bottom edge
        pen.rect(x, y - 6.0F, kw, 8.0F, 0xCC0F120D);
        pen.rect(x, y - 6.0F, kw, 0.5F, edge);
        pen.rect(x, y + 1.5F, kw, 0.5F, edge);
        pen.rect(x, y - 6.0F, 0.5F, 8.0F, edge);
        pen.rect(x + kw - 0.5F, y - 6.0F, 0.5F, 8.0F, edge);
        pen.rect(x + 0.5F, y + 1.0F, kw - 1.0F, 0.5F, 0x66000000);
        pen.text(key, x + kw / 2.0F, y, HudPen.CENTER, edge);
        pen.shadowed(label, x + kw + gap, y + 0.5F, HudPen.LEFT, fast ? 0xFFD9623F : 0xFFA19E8B);
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
            shiftHint(pen, width / 2.0F, height / 2.0F + height * 0.075F + height * 0.045F + 22.0F);
        } finally {
            pen.end();
        }
    }
}
