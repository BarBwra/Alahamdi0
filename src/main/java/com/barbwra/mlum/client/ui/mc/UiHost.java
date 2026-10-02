package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.ui.Hits;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.layout.Layout;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Paint;
import com.barbwra.mlum.client.ui.layout.Painter;
import com.barbwra.mlum.client.ui.text.TextEngine;
import com.barbwra.mlum.client.ui.view.Chrome;
import com.barbwra.mlum.client.ui.view.Css;
import com.barbwra.mlum.client.ui.view.Overlays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * One frame of any menu: fit the design to the window, build the page, lay it out, paint it, and
 * remember what was clickable where.
 *
 * <p>The page is rebuilt every frame from live game state rather than kept and patched. At about a
 * millisecond for the busiest screen that is cheaper than any bookkeeping that could go stale, and
 * it means nothing on screen can ever disagree with the game.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UiHost {

    private UiHost() {
    }

    public static final McCanvas CANVAS = new McCanvas();
    /** What the last painted frame made clickable, in framebuffer pixels. */
    public static final Hits HITS = new Hits();

    /** The mouse in framebuffer pixels, and what is under it, as of the last frame. */
    public static int mouseX;
    public static int mouseY;
    public static String hover;
    public static Hits.Hit hoverHit;

    private static long frames;
    private static long lastFrameAt;

    /**
     * The cursor in framebuffer pixels, read from the window rather than from the GUI-scaled
     * integers a screen's render method receives - those move in steps of the GUI scale, which is
     * three framebuffer pixels at 1080p and too coarse for a 26px attachment mount.
     */
    public static int[] mouseDevice() {
        Minecraft mc = Minecraft.getInstance();
        com.mojang.blaze3d.platform.Window w = mc.getWindow();
        double sx = w.getWidth() / (double) Math.max(1, w.getScreenWidth());
        double sy = w.getHeight() / (double) Math.max(1, w.getScreenHeight());
        return new int[]{(int) Math.floor(mc.mouseHandler.xpos() * sx), (int) Math.floor(mc.mouseHandler.ypos() * sy)};
    }

    /** What the last frame drew under the cursor right now, or null. */
    public static Hits.Hit hitUnderMouse() {
        int[] d = mouseDevice();
        return HITS.at(d[0], d[1]);
    }

    public static void render(GuiGraphics g, UiPage page) {
        UiBoot.ensure();
        Minecraft mc = Minecraft.getInstance();
        int fw = mc.getWindow().getWidth();
        int fh = mc.getWindow().getHeight();
        Px.fit(fw, fh);
        TextEngine.setScale(Px.s);
        Atlas.fitTo(fw);
        TextEngine.tick();
        Atlas.tick();
        long t = UiState.now();
        lastFrameAt = t;
        closedSent = false;
        UiState.frame(t);
        // tells the server a menu is up, for the guard that stops mobs jumping a player who is
        // reading one. Repeated rather than latched, so it expires on its own - see MenuGuard.
        UiState.pingMenuOpen(t);

        int[] mouse = mouseDevice();
        mouseX = mouse[0];
        mouseY = mouse[1];
        hoverHit = HITS.at(mouseX, mouseY);
        hover = hoverHit == null ? null : hoverHit.id;

        Node root = Css.block().size(Px.W, Px.H);
        Node main = page.main(hover);
        int[] jitter = UiState.jitter(t);
        if (main != null) {
            if (jitter != null && (jitter[0] != 0 || jitter[1] != 0)) {
                shake(main, jitter[0], jitter[1]);
            }
            root.add(main);
        }
        if (UiState.dangerDir >= 0) {
            root.add(Overlays.danger(UiState.dangerDir, UiState.dangerBlocks, t, UiState.lite()));
        }
        Node modal = page.modal(hover);
        if (modal != null) {
            Painter before = modal.under;
            modal.under = (c, n) -> {
                // everything painted so far is behind the dialog and no longer clickable
                HITS.cover();
                if (before != null) {
                    before.paint(c, n);
                }
            };
            root.add(modal);
        }
        root.add(Chrome.topBar(UiState.bar(page.tab(), hover, t)));
        root.add(Chrome.footer(true, page.tab() == 0));
        Layout.layout(root, 0, 0, Px.W, Px.H);

        HITS.clear();
        McCanvas c = CANVAS;
        c.begin(g, mouseX, mouseY);
        try {
            Chrome.scrim(c, page.bagScrim(), MlumConfig.scrimStrength());
            Paint.paint(c, root, HITS);
            if (UiState.fxActive(t)) {
                Overlays.staticBand(c, UiState.noise(t), UiState.fxOpacity(t));
            }
            Node ghost = page.ghost(mouseX, mouseY);
            if (ghost != null) {
                c.pushZ(400.0F);
                Overlays.paintFloating(c, ghost, null);
                c.popZ();
            }
            Node toast = UiState.toastNode(t);
            if (toast != null) {
                Overlays.paintFloating(c, toast, null);
            }
        } finally {
            c.end();
        }
        if (++frames % 600 == 0) {
            TextEngine.trim(1200);
        }
    }

    /** The static's shake: the menu body only, the bars stay put - as in the design. */
    private static void shake(Node main, int dx, int dy) {
        Painter under = main.under;
        Painter over = main.over;
        main.under = (c, n) -> {
            c.setOffset(dx, dy);
            if (under != null) {
                under.paint(c, n);
            }
        };
        main.over = (c, n) -> {
            if (over != null) {
                over.paint(c, n);
            }
            c.setOffset(0, 0);
        };
    }

    /**
     * Called every client tick. Once the menus have been shut for half a minute their textures and
     * caches are let go, so the memory is the game's again until the bag next opens.
     */
    public static void idleTick() {
        if (lastFrameAt == 0L) {
            return;
        }
        long idle = UiState.now() - lastFrameAt;
        /*
         * The menus stopped drawing. Note this is the only place that can tell "the player closed
         * everything" from "the player switched tabs" - a tab switch destroys one Screen and builds
         * another, so removed() fires either way, but the frames never stop.
         */
        if (idle > 800L && !closedSent) {
            closedSent = true;
            UiState.menuClosed();
        }
        if (idle > 30_000L) {
            lastFrameAt = 0L;
            Atlas.clear();
            TextEngine.clear();
            McItems.clear();
        }
    }

    private static boolean closedSent;
}
