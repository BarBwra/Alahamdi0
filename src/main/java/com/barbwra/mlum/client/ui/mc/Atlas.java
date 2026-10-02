package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Rasters;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every UI raster lives on the GPU: a few big pages, shelf-packed.
 *
 * <p>One page is one texture, so a screenful of text, sprites and plain rectangles is a handful of
 * draw calls rather than one per string - rectangles too, because each page keeps a small block of
 * white texels that a fill samples and tints. Only the part of a page that changed is uploaded, once
 * per frame, so a counter ticking up costs a few kilobytes rather than the whole page. When the pages
 * are full the least recently drawn one is emptied and reused; its rasters are simply packed again
 * the next time they are drawn.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class Atlas {

    static final class Page {
        final int size;
        final NativeImage image;
        final DynamicTexture texture;
        int shelfY;
        int shelfH;
        int cursorX;
        long lastUsed;
        final List<Raster> residents = new ArrayList<>();
        /** Bounds of what changed since the last upload; empty when minX > maxX. */
        int dirtyX0;
        int dirtyY0;
        int dirtyX1;
        int dirtyY1;
        final float whiteU;
        final float whiteV;

        Page(int size) {
            this.size = size;
            this.image = new NativeImage(NativeImage.Format.RGBA, size, size, true);
            this.texture = new DynamicTexture(image);
            this.texture.setFilter(false, false);
            this.whiteU = (WHITE_AT + WHITE / 2.0F) / size;
            this.whiteV = (WHITE_AT + WHITE / 2.0F) / size;
            clearDirty();
            reset();
        }

        void reset() {
            for (Raster r : residents) {
                r.handle = null;
            }
            residents.clear();
            image.fillRect(0, 0, size, size, 0);
            // the white block every fill samples, then the first shelf starts beside it
            image.fillRect(WHITE_AT, WHITE_AT, WHITE, WHITE, 0xFFFFFFFF);
            shelfY = 0;
            shelfH = WHITE_AT * 2 + WHITE;
            cursorX = WHITE_AT * 2 + WHITE;
            markDirty(0, 0, size, size);
        }

        void markDirty(int x0, int y0, int x1, int y1) {
            dirtyX0 = Math.min(dirtyX0, x0);
            dirtyY0 = Math.min(dirtyY0, y0);
            dirtyX1 = Math.max(dirtyX1, x1);
            dirtyY1 = Math.max(dirtyY1, y1);
        }

        boolean dirty() {
            return dirtyX1 > dirtyX0 && dirtyY1 > dirtyY0;
        }

        void clearDirty() {
            dirtyX0 = Integer.MAX_VALUE;
            dirtyY0 = Integer.MAX_VALUE;
            dirtyX1 = Integer.MIN_VALUE;
            dirtyY1 = Integer.MIN_VALUE;
        }

        void close() {
            texture.close();
        }
    }

    /** Where a raster sits: its page and its pixel rectangle there. */
    static final class Slot {
        final Page page;
        final int x;
        final int y;
        final int w;
        final int h;
        /** Rasters bigger than a page get a texture of their own. */
        final DynamicTexture own;
        final NativeImage ownImage;
        /** {@link Raster#version} of the pixels this slot holds. */
        int version;

        Slot(Page page, int x, int y, int w, int h, DynamicTexture own, NativeImage ownImage, int version) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.own = own;
            this.ownImage = ownImage;
            this.version = version;
        }
    }

    /** Told just before a page is emptied, so a batch still pointing at it can be drawn first. */
    static java.util.function.Consumer<Page> beforeReset;

    private static final int WHITE_AT = 1;
    private static final int WHITE = 4;
    private static final int PAD = 1;
    private static final List<Page> PAGES = new ArrayList<>();
    private static int pageSize = 1024;
    private static int maxPages = 4;
    private static long frame;

    private Atlas() {
    }

    /** Called once when the UI starts: the raster caches tell us when a raster goes away. */
    public static void install() {
        Rasters.setReleaser(Atlas::release);
    }

    public static void tick() {
        frame++;
    }

    /** Sizes pages to the screen: 1024 up to about 1440p, 2048 above. Drops everything on change. */
    public static void fitTo(int deviceWidth) {
        int want = deviceWidth > 2200 ? 2048 : 1024;
        if (want != pageSize) {
            clear();
            pageSize = want;
            maxPages = want > 1024 ? 2 : 4;
        }
    }

    /** Frees every page - on a resize, and when the menus have been closed for a while. */
    public static void clear() {
        for (Page p : PAGES) {
            for (Raster r : p.residents) {
                r.handle = null;
            }
            p.close();
        }
        PAGES.clear();
    }

    public static boolean isEmpty() {
        return PAGES.isEmpty();
    }

    /** A page to sample the white block from; creates the first page if there is none yet. */
    static Page anyPage() {
        if (PAGES.isEmpty()) {
            PAGES.add(new Page(pageSize));
        }
        Page best = PAGES.get(0);
        best.lastUsed = frame;
        return best;
    }

    /** The slot for a raster, packing it the first time it is asked for and refreshing it if it changed. */
    static Slot slotOf(Raster r) {
        if (r.handle instanceof Slot s && (s.own != null || PAGES.contains(s.page))) {
            if (s.version != r.version) {
                refresh(r, s);
            }
            if (s.page != null) {
                s.page.lastUsed = frame;
            }
            return s;
        }
        Slot s = pack(r);
        r.handle = s;
        return s;
    }

    private static void refresh(Raster r, Slot s) {
        if (r.width != s.w || r.height != s.h) {
            return;
        }
        if (s.own != null) {
            copy(r, s.ownImage, 0, 0);
            s.own.upload();
        } else {
            copy(r, s.page.image, s.x, s.y);
            s.page.markDirty(s.x, s.y, s.x + s.w, s.y + s.h);
        }
        s.version = r.version;
    }

    private static Slot pack(Raster r) {
        int w = r.width;
        int h = r.height;
        if (w + PAD * 2 > pageSize || h + PAD * 2 > pageSize) {
            NativeImage img = new NativeImage(NativeImage.Format.RGBA, w, h, true);
            copy(r, img, 0, 0);
            DynamicTexture own = new DynamicTexture(img);
            own.setFilter(false, false);
            return new Slot(null, 0, 0, w, h, own, img, r.version);
        }
        for (Page p : PAGES) {
            Slot s = place(p, r);
            if (s != null) {
                return s;
            }
        }
        Page page;
        if (PAGES.size() < maxPages) {
            page = new Page(pageSize);
            PAGES.add(page);
        } else {
            page = PAGES.get(0);
            for (Page p : PAGES) {
                if (p.lastUsed < page.lastUsed) {
                    page = p;
                }
            }
            evict(page);
        }
        Slot s = place(page, r);
        if (s == null) {
            evict(page);
            s = place(page, r);
        }
        return s;
    }

    private static void evict(Page page) {
        if (beforeReset != null) {
            beforeReset.accept(page);
        }
        page.reset();
    }

    private static Slot place(Page p, Raster r) {
        int w = r.width + PAD * 2;
        int h = r.height + PAD * 2;
        if (p.cursorX + w > p.size) {
            p.shelfY += p.shelfH;
            p.shelfH = 0;
            p.cursorX = 0;
        }
        if (p.shelfY + h > p.size) {
            return null;
        }
        int x = p.cursorX + PAD;
        int y = p.shelfY + PAD;
        copy(r, p.image, x, y);
        p.markDirty(x, y, x + r.width, y + r.height);
        p.cursorX += w;
        p.shelfH = Math.max(p.shelfH, h);
        p.lastUsed = frame;
        p.residents.add(r);
        return new Slot(p, x, y, r.width, r.height, null, null, r.version);
    }

    /** ARGB rows into the image, as the ABGR ints NativeImage stores. */
    private static void copy(Raster r, NativeImage img, int ox, int oy) {
        int[] px = r.argb;
        for (int y = 0; y < r.height; y++) {
            int row = y * r.width;
            for (int x = 0; x < r.width; x++) {
                int c = px[row + x];
                int abgr = (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16);
                img.setPixelRGBA(ox + x, oy + y, abgr);
            }
        }
    }

    /** Uploads the part of a page that changed since its last upload. */
    static void upload(Page p) {
        if (p == null || !p.dirty()) {
            return;
        }
        int x0 = Math.max(0, p.dirtyX0);
        int y0 = Math.max(0, p.dirtyY0);
        int x1 = Math.min(p.size, p.dirtyX1);
        int y1 = Math.min(p.size, p.dirtyY1);
        p.clearDirty();
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        p.texture.bind();
        // level, destination x/y, source x/y, size, no mipmaps, keep the image
        p.image.upload(0, x0, y0, x0, y0, x1 - x0, y1 - y0, false, false);
    }

    static int textureId(Slot s) {
        return s.own != null ? s.own.getId() : s.page.texture.getId();
    }

    static int textureId(Page p) {
        return p.texture.getId();
    }

    /** The texture's width (and height - pages are square) in texels. */
    static int textureWidth(Slot s) {
        return s.own != null ? s.w : s.page.size;
    }

    static int textureHeight(Slot s) {
        return s.own != null ? s.h : s.page.size;
    }

    /** The cache dropped a raster: forget its slot; an own texture is freed at once. */
    static void release(Raster r) {
        if (r.handle instanceof Slot s) {
            if (s.own != null) {
                s.own.close();
            } else if (s.page != null) {
                s.page.residents.remove(r);
            }
        }
    }
}
