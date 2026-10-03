package com.barbwra.mlum.client.hud.field;

import com.barbwra.mlum.client.gui.Anim;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The busy spinner shared by searching and reviving: eight chunky pixel dots in a ring, a bright
 * head with a short glowing tail walking clockwise round it, the rest dim.
 *
 * <pre>
 *        .  o  O          the head and three behind it are lit and glow,
 *      .         O        the other four sit dark
 *        .  o  O
 * </pre>
 *
 * <p>Sized from the screen height rather than the GUI scale, so it reads the same at any setting -
 * a little under a tenth of the screen across. A noise stalls it: the dots stop and turn red.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SearchSpinner {

    private SearchSpinner() {
    }

    private static final int[] LIT = {0xFFFBF3EA, 0xFFF1E2D2, 0xFFE2CDB8, 0xFFCDB49C};
    private static final int DIM = 0xB0503F35;
    private static final int RED = 0xFFE0573A;
    private static final int RED_DIM = 0xB05A2418;

    /**
     * @param cx       centre, GUI pixels
     * @param cy       centre, GUI pixels
     * @param height   screen height, GUI pixels
     * @param since    when the spinner started, {@link Anim#now()} time
     * @param lapMs    one turn of the head
     * @param progress 0..1 for the thin line under it, or below 0 for none
     */
    public static void draw(HudPen pen, float cx, float cy, int height, long since, float lapMs,
                            boolean paused, float progress, int accent) {
        long now = Anim.now();
        float radius = height * 0.045F;
        int cell = Math.max(1, Math.round(height * pen.u * 0.0042F));
        int head = paused || !Anim.enabled() ? 0 : (int) (((now - since) / (lapMs / 8.0F)) % 8);
        for (int i = 0; i < 8; i++) {
            double ang = i / 8.0D * Math.PI * 2.0D - Math.PI / 2.0D;
            float x = cx + (float) Math.cos(ang) * radius;
            float y = cy + (float) Math.sin(ang) * radius;
            int age = (head - i + 8) % 8;
            int colour = age < LIT.length ? (paused ? RED : LIT[age]) : (paused ? RED_DIM : DIM);
            if (age < LIT.length) {
                // the glow: two soft discs behind the lit dots, fading down the tail
                float g = (1.0F - age / (float) LIT.length);
                int halo = paused ? 0xE0573A : 0xFFE8D2;
                Shapes.disc(pen, x, y, cell * 5.2F / pen.u, alpha(halo, 0.10F * g));
                Shapes.disc(pen, x, y, cell * 3.6F / pen.u, alpha(halo, 0.16F * g));
            }
            Shapes.pixelDot(pen, x, y, cell, colour);
        }
        if (progress >= 0.0F) {
            float w = radius * 1.6F;
            float top = cy + radius + cell * 5.0F / pen.u + 4.0F;
            float t = Math.max(1.0F, Math.round(pen.u * 0.6F)) / pen.u;
            pen.rect(cx - w / 2, top, w, t, 0x40000000);
            pen.rect(cx - w / 2, top, w, t, 0x30FFFFFF);
            pen.rect(cx - w / 2, top, w * Mth.clamp(progress, 0.0F, 1.0F), t, paused ? RED : accent);
        }
    }

    private static int alpha(int rgb, float a) {
        return (Mth.clamp(Math.round(a * 255.0F), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }
}
