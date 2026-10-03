package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * The pieces the redrawn game screens share: the bag's palette, its panel with the amber corner
 * brackets, its buttons, and a list of where the buttons were drawn so a click can find them.
 *
 * <p>Everything is in GUI pixels through {@link HudPen}, so these screens scale with the GUI Scale
 * setting the same way the vanilla screens they replace do.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ScreenKit {

    public static final int PAGE = 0xFF0B0D0A;
    public static final int PANEL = 0xDB0B0D0A;
    public static final int CARD = 0xE0161A13;
    public static final int LINE = 0xFF2B3026;
    public static final int LINE_SOFT = 0xFF1F231B;
    public static final int BONE = 0xFFECE6D4;
    public static final int SOFT = 0xFFD4CEBA;
    public static final int MUTED = 0xFFA19E8B;
    public static final int FAINT = 0xFF6E6D5F;
    public static final int AMBER = 0xFFF0A93B;
    public static final int AMBER_DIM = 0xFF8A6124;
    public static final int RUST = 0xFFE0613F;
    public static final int SAGE = 0xFF93C46F;
    public static final int INK = 0xFF16120A;

    public final HudPen pen = new HudPen();
    private final List<float[]> rects = new ArrayList<>();
    private final List<String> ids = new ArrayList<>();
    private String lastHover;

    /** Starts a frame: forgets last frame's buttons. */
    public void begin(net.minecraft.client.gui.GuiGraphics g) {
        rects.clear();
        ids.clear();
        pen.begin(g);
    }

    public void end() {
        pen.end();
    }

    public static int alpha(int argb, float a) {
        return (Mth.clamp(Math.round((argb >>> 24) * a), 0, 255) << 24) | (argb & 0xFFFFFF);
    }

    /** The bag's panel: dark fill, a hairline border, the faint top light and two amber brackets. */
    public void panel(float x, float y, float w, float h) {
        pen.rect(x, y, w, h, PANEL);
        pen.rect(x, y, w, 0.5F, LINE);
        pen.rect(x, y + h - 0.5F, w, 0.5F, LINE);
        pen.rect(x, y, 0.5F, h, LINE);
        pen.rect(x + w - 0.5F, y, 0.5F, h, LINE);
        pen.rect(x + 0.5F, y + 0.5F, w - 1.0F, 0.5F, 0x09ECE6D4);
        float len = 5.0F;
        pen.rect(x + w - len, y, len, 0.75F, AMBER_DIM);
        pen.rect(x + w - 0.75F, y, 0.75F, len, AMBER_DIM);
        pen.rect(x, y + h - 0.75F, len, 0.75F, AMBER_DIM);
        pen.rect(x, y + h - len, 0.75F, len, AMBER_DIM);
    }

    /** A panel heading: the amber post and the title, right-aligned the way the bag's are. */
    public void heading(String title, float right, float y) {
        pen.rect(right - 1.5F, y - 5.0F, 1.5F, 6.0F, AMBER);
        pen.text(pen.kufi(title, 6.0F, 700), right - 4.0F, y, HudPen.RIGHT, BONE);
    }

    public static final int FILLED = 0;
    public static final int GHOST = 1;
    public static final int DANGER = 2;

    /** A button, remembered for {@link #hit}. */
    public void button(String id, String label, float x, float y, float w, float h, int style, double mx, double my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        int bg;
        int border;
        int fg;
        switch (style) {
            case GHOST -> {
                bg = hover ? 0x22F0A93B : 0x08FFFFFF;
                border = hover ? AMBER : LINE;
                fg = hover ? AMBER : SOFT;
            }
            case DANGER -> {
                bg = hover ? 0xFFEE7451 : RUST;
                border = bg;
                fg = 0xFF170B07;
            }
            default -> {
                bg = hover ? 0xFFFFC266 : AMBER;
                border = bg;
                fg = INK;
            }
        }
        pen.rect(x, y, w, h, bg);
        pen.rect(x, y, w, 0.5F, border);
        pen.rect(x, y + h - 0.5F, w, 0.5F, border);
        pen.rect(x, y, 0.5F, h, border);
        pen.rect(x + w - 0.5F, y, 0.5F, h, border);
        Shaped t = pen.kufi(label, Math.min(6.5F, h * 0.42F), 700);
        pen.text(t, x + w / 2.0F, y + h / 2.0F + h * 0.16F, HudPen.CENTER, fg);
        rects.add(new float[]{x, y, x + w, y + h});
        ids.add(id);
        if (hover && !id.equals(lastHover)) {
            lastHover = id;
        } else if (!hover && id.equals(lastHover)) {
            lastHover = null;
        }
    }

    /** Which button is at this point, or null. */
    public String hit(double mx, double my) {
        for (int i = rects.size() - 1; i >= 0; i--) {
            float[] r = rects.get(i);
            if (mx >= r[0] && mx < r[2] && my >= r[1] && my < r[3]) {
                return ids.get(i);
            }
        }
        return null;
    }

    public static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F, 0.7F));
    }

    /** A dark vertical wash over the whole screen, heavier at the bottom. */
    public void shade(int width, int height, float strength) {
        pen.vgrad(0, 0, width, height, alpha(0xFF050705, 0.55F * strength), alpha(0xFF050705, 0.88F * strength));
    }

    /** The server's name as a wordmark: big Handjet with a soft glow. */
    public void wordmark(String name, float cx, float baseline, float size) {
        Shaped s = pen.pixel(name.toUpperCase(java.util.Locale.ROOT), size, 700);
        pen.glow(s, cx, baseline, HudPen.CENTER, size * 0.18F, alpha(AMBER, 0.35F));
        pen.text(s, cx, baseline, HudPen.CENTER, BONE);
    }
}
