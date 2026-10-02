package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.network.C2SLootCancel;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CLootSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The search as this client sees it: the server's last word on progress, and the spinner.
 *
 * <pre>
 *            +            the crosshair
 *          .   o          eight pixel crosses; the bright one walks round
 *        .       O        once every half second, trailing a fade
 *          .   o
 *            .
 *        ━━━━━━━━──────   how far the search has got
 * </pre>
 *
 * <p>When a noise has the search stalled the crosses stop and turn red for the length of the stall,
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

    public static void clear() {
        pos = null;
    }

    /** Letting go of the button ends the search at once, without waiting for the server to notice. */
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
        if (!mc.options.keyUse.isDown()) {
            pos = null;
            ModNetwork.CHANNEL.sendToServer(C2SLootCancel.INSTANCE);
        }
    }

    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        if (pos == null || HudVisibility.hidden()) {
            return;
        }
        long now = Anim.now();
        float cx = width / 2.0F;
        float cy = height / 2.0F + 16.0F;
        int accent = MlumConfig.fieldHudAccent();
        HudPen pen = PEN;
        pen.begin(graphics);
        try {
            float px = Math.max(1.0F, Math.round(pen.u * 0.6F)) / pen.u;
            // one lap per half second; faster still on a fast search
            float lap = fast ? 250.0F : 500.0F;
            int head = paused || !Anim.enabled() ? 0 : (int) (((now - startedAt) / (lap / 8.0F)) % 8);
            for (int i = 0; i < 8; i++) {
                double ang = i / 8.0D * Math.PI * 2.0D - Math.PI / 2.0D;
                float dx = cx + (float) Math.cos(ang) * 7.0F;
                float dy = cy + (float) Math.sin(ang) * 7.0F;
                int age = (head - i + 8) % 8;
                int colour;
                if (paused) {
                    colour = Anim.mix(0xFFD9623F, 0xFF5A2A1C, age / 8.0F);
                } else {
                    colour = switch (age) {
                        case 0 -> 0xFFFFF6E6;
                        case 1 -> 0xFFF3E2C8;
                        case 2 -> 0xFFD9C3A3;
                        case 3 -> 0xFFA38B6E;
                        default -> 0xFF5A4A3C;
                    };
                }
                if (age == 0 && !paused) {
                    pen.rect(dx - px * 2.5F, dy - px * 2.5F, px * 5, px * 5, 0x40FFF0D2);
                }
                pen.rect(dx - px * 1.5F, dy - px * 0.5F, px * 3, px, colour);
                pen.rect(dx - px * 0.5F, dy - px * 1.5F, px, px * 3, colour);
            }
            float barW = 22.0F;
            float barY = cy + 12.0F;
            float bar = Math.max(1.0F, Math.round(pen.u * 0.5F)) / pen.u;
            pen.rect(cx - barW / 2, barY, barW, bar, 0x33FFFFFF);
            pen.rect(cx - barW / 2, barY, barW * Mth.clamp(progress, 0.0F, 1.0F), bar,
                    paused ? 0xFFD9623F : accent);
        } finally {
            pen.end();
        }
    }
}
