package com.barbwra.mlum.client.ui.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.text.Bidi;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes, measures and rasterises UI text with the design's own font files.
 *
 * <p>Java's layout engine is HarfBuzz underneath, the same shaper Chrome uses, so Arabic joins,
 * ligatures and kerning come out glyph for glyph the same as the page. What Chrome adds on top -
 * and what this class copies - is the rounding: every glyph advance to a whole CSS pixel. Text is
 * given to this class in <b>logical</b> order, as typed; bidi and shaping happen here.</p>
 *
 * <p>Everything is cached. A screen asks for the same few hundred strings every frame, so after
 * the first frame this is a hash lookup per string.</p>
 */
public final class TextEngine {

    /** Measures text with the game font when the UI fonts are unavailable. */
    public interface FallbackMeasure {
        float width(String text, float size, int weight, boolean pixel);
    }

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final int MAX_SHAPED = 1500;

    private static float scale = 1.0F;
    private static FallbackMeasure fallbackMeasure = (t, s, w, p) -> t.length() * s * 0.55F;
    private static long clock;

    private static final LinkedHashMap<String, Shaped> CACHE = new LinkedHashMap<>(1024, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Shaped> eldest) {
            if (size() > MAX_SHAPED) {
                eldest.getValue().releaseRasters();
                return true;
            }
            return false;
        }
    };

    private TextEngine() {
    }

    public static void setFallbackMeasure(FallbackMeasure measure) {
        if (measure != null) {
            fallbackMeasure = measure;
        }
    }

    /** Device pixels per CSS pixel. Changing it drops every cached shape and raster. */
    public static void setScale(float s) {
        if (Math.abs(s - scale) > 1e-4F) {
            clear();
            scale = s;
        }
    }

    public static float scale() {
        return scale;
    }

    public static void clear() {
        for (Shaped s : CACHE.values()) {
            s.releaseRasters();
        }
        CACHE.clear();
    }

    /** Bumped once per frame by the painter; lets rasters report how recently they were drawn. */
    public static void tick() {
        clock++;
    }

    public static long now() {
        return clock;
    }

    /* ------------------------------------------------------------------ shaping */

    /**
     * Shapes one line. {@code rtl} is the paragraph direction - the design's whole screen is
     * {@code direction:rtl}, except spans marked {@code direction:ltr} such as every number.
     */
    public static Shaped shape(String text, UiFont font, float size, float spacing, boolean rtl) {
        if (text == null) {
            text = "";
        }
        boolean fontsOk = Fonts.ready() && font != null;
        int weight = font == null ? 400 : font.weight;
        boolean pixel = font != null && font.isPixel();
        String key = (fontsOk ? font.id : -1) + "|" + size + "|" + spacing + "|" + (rtl ? 'r' : 'l') + "|" + text;
        Shaped cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Shaped made = fontsOk ? build(text, font, size, spacing, rtl)
                : new Shaped(text, null, size, spacing, rtl, weight, pixel,
                fallbackMeasure.width(text, size, weight, pixel) + spacing * text.length(),
                Math.round(size * 0.9F), Math.round(size * 0.25F), scale, null, null);
        CACHE.put(key, made);
        return made;
    }

    private static final class Sub {
        final int start;
        final int limit;
        final Font font;

        Sub(int start, int limit, Font font) {
            this.start = start;
            this.limit = limit;
            this.font = font;
        }
    }

    private static Shaped build(String text, UiFont font, float size, float spacing, boolean rtl) {
        int ascent = font.ascent(size);
        int descent = font.descent(size);
        if (text.isEmpty()) {
            return new Shaped(text, font, size, spacing, rtl, font.weight, font.isPixel(), 0.0F,
                    ascent, descent, scale, new GlyphVector[0], new float[0]);
        }
        float dev = size * scale;
        Font primary = font.base.deriveFont(dev);
        Font second = font.isPixel() ? Fonts.kufi(font.weight).base.deriveFont(dev) : null;
        Font last = Fonts.fallbackFont() == null ? null : Fonts.fallbackFont().deriveFont(dev);

        char[] chars = text.toCharArray();
        Bidi bidi = new Bidi(chars, 0, null, 0, chars.length,
                rtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT);
        int count = bidi.getRunCount();
        byte[] levels = new byte[count];
        Object[] order = new Object[count];
        for (int r = 0; r < count; r++) {
            int start = bidi.getRunStart(r);
            int limit = bidi.getRunLimit(r);
            int level = bidi.getRunLevel(r);
            List<Sub> subs = splitByFont(chars, start, limit, primary, second, last);
            if ((level & 1) != 0) {
                java.util.Collections.reverse(subs);
            }
            levels[r] = (byte) level;
            order[r] = new Object[]{subs, level};
        }
        Bidi.reorderVisually(levels, 0, order, 0, count);

        List<GlyphVector> vectors = new ArrayList<>();
        List<Float> xs = new ArrayList<>();
        float cssPen = 0.0F;
        for (Object o : order) {
            Object[] pair = (Object[]) o;
            @SuppressWarnings("unchecked")
            List<Sub> subs = (List<Sub>) pair[0];
            int level = (Integer) pair[1];
            int flags = (level & 1) != 0 ? Font.LAYOUT_RIGHT_TO_LEFT : Font.LAYOUT_LEFT_TO_RIGHT;
            for (Sub sub : subs) {
                GlyphVector gv = sub.font.layoutGlyphVector(FRC, chars, sub.start, sub.limit, flags);
                float w = roundAdvances(gv, spacing);
                vectors.add(gv);
                xs.add(cssPen * scale);
                cssPen += w;
            }
        }
        float[] runX = new float[xs.size()];
        for (int i = 0; i < runX.length; i++) {
            runX[i] = xs.get(i);
        }
        return new Shaped(text, font, size, spacing, rtl, font.weight, font.isPixel(), cssPen,
                ascent, descent, scale, vectors.toArray(new GlyphVector[0]), runX);
    }

    /** Splits [start, limit) into runs each of which one font can display. */
    private static List<Sub> splitByFont(char[] chars, int start, int limit, Font primary, Font second, Font last) {
        List<Sub> out = new ArrayList<>();
        int runStart = start;
        Font runFont = null;
        int i = start;
        while (i < limit) {
            int cp = Character.codePointAt(chars, i, limit);
            int len = Character.charCount(cp);
            Font f = pick(cp, primary, second, last);
            if (runFont == null) {
                runFont = f;
            } else if (f != runFont && !isNeutralJoiner(cp)) {
                out.add(new Sub(runStart, i, runFont));
                runStart = i;
                runFont = f;
            }
            i += len;
        }
        if (runFont != null && runStart < limit) {
            out.add(new Sub(runStart, limit, runFont));
        }
        return out;
    }

    private static boolean isNeutralJoiner(int cp) {
        return cp == 0x200C || cp == 0x200D;
    }

    private static Font pick(int cp, Font primary, Font second, Font last) {
        if (cp == ' ' || primary.canDisplay(cp)) {
            return primary;
        }
        if (second != null && second.canDisplay(cp)) {
            return second;
        }
        return last != null ? last : primary;
    }

    /**
     * Rounds every glyph advance to a whole CSS pixel and adds letter-spacing after each glyph,
     * then moves the glyphs to match. Marks (zero-advance glyphs) ride along with their base.
     * Returns the run's width in CSS pixels.
     */
    private static float roundAdvances(GlyphVector gv, float spacing) {
        int n = gv.getNumGlyphs();
        if (n == 0) {
            return 0.0F;
        }
        float[] pos = gv.getGlyphPositions(0, n + 1, null);
        boolean[] mark = new boolean[n];
        for (int i = 0; i < n; i++) {
            GlyphMetrics m = gv.getGlyphMetrics(i);
            mark[i] = Math.abs(m.getAdvanceX()) < 1e-4F;
        }
        // pen advance of each base glyph = distance to the next base glyph (or the end)
        float[] adv = new float[n];
        int prevBase = -1;
        for (int i = 0; i < n; i++) {
            if (mark[i]) {
                continue;
            }
            if (prevBase >= 0) {
                adv[prevBase] = pos[i * 2] - pos[prevBase * 2];
            }
            prevBase = i;
        }
        if (prevBase >= 0) {
            adv[prevBase] = pos[n * 2] - pos[prevBase * 2];
        }

        float[] nx = new float[n];
        float cum = 0.0F;
        for (int i = 0; i < n; i++) {
            if (mark[i]) {
                continue;
            }
            nx[i] = cum * scale;
            cum += Math.round(adv[i] / scale) + spacing;
        }
        for (int i = 0; i < n; i++) {
            if (!mark[i]) {
                continue;
            }
            int base = baseOf(i, pos, adv, mark, n);
            nx[i] = base < 0 ? pos[i * 2] : pos[i * 2] + (nx[base] - pos[base * 2]);
        }
        for (int i = 0; i < n; i++) {
            gv.setGlyphPosition(i, new Point2D.Float(nx[i], pos[i * 2 + 1]));
        }
        gv.setGlyphPosition(n, new Point2D.Float(cum * scale, pos[n * 2 + 1]));
        return cum;
    }

    /** The base glyph whose advance box a mark sits over; the nearest base when it sits over none. */
    private static int baseOf(int markIndex, float[] pos, float[] adv, boolean[] mark, int n) {
        float x = pos[markIndex * 2];
        int nearest = -1;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (mark[i]) {
                continue;
            }
            float left = pos[i * 2];
            float right = left + adv[i];
            if (x >= left - 0.01F && x < right + 0.01F) {
                return i;
            }
            float d = Math.min(Math.abs(x - left), Math.abs(x - right));
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        return nearest;
    }

    /* --------------------------------------------------------------- measuring */

    public static float width(String text, UiFont font, float size, float spacing, boolean rtl) {
        return shape(text, font, size, spacing, rtl).width;
    }

    /**
     * Shortens {@code text} with an ellipsis until it fits {@code max} CSS pixels. Arabic loses
     * words from its logical end, which is the visual left, the same place a browser cuts.
     */
    public static String fit(String text, UiFont font, float size, float spacing, boolean rtl, float max) {
        if (text == null || text.isEmpty() || width(text, font, size, spacing, rtl) <= max) {
            return text == null ? "" : text;
        }
        String ell = "…";
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            String candidate = text.substring(0, mid).trim() + ell;
            if (width(candidate, font, size, spacing, rtl) <= max) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo <= 0 ? ell : text.substring(0, lo).trim() + ell;
    }

    /* ------------------------------------------------------------- rasterising */

    /** White glyphs with coverage in alpha, drawable in any colour. */
    public static Raster mask(Shaped s) {
        if (s.fallback()) {
            return null;
        }
        if (s.mask == null) {
            s.mask = render(s, Color.WHITE, null, 0.0F, 0.0F, true);
        }
        s.mask.lastUsed = clock;
        return s.mask;
    }

    /**
     * Glyphs filled with a vertical gradient - {@code background-clip:text}. {@code topCss} and
     * {@code bottomCss} are where the gradient box starts and ends, relative to the baseline.
     */
    public static Raster gradient(Shaped s, float topCss, float bottomCss, float[] stops, int[] argb) {
        if (s.fallback()) {
            return null;
        }
        StringBuilder key = new StringBuilder("g").append(topCss).append(',').append(bottomCss);
        for (int i = 0; i < stops.length; i++) {
            key.append(',').append(stops[i]).append(':').append(Integer.toHexString(argb[i]));
        }
        Raster r = styled(s, key.toString());
        if (r == null) {
            Color[] colors = new Color[argb.length];
            for (int i = 0; i < argb.length; i++) {
                colors[i] = new Color(argb[i], true);
            }
            r = render(s, null, new GradientSpec(topCss * s.scale, bottomCss * s.scale, stops, colors),
                    0.0F, 0.0F, false);
            putStyled(s, key.toString(), r);
        }
        r.lastUsed = clock;
        return r;
    }

    /** Solid glyphs over a hard offset shadow, baked together - {@code text-shadow: dx dy 0 c}. */
    public static Raster shadowed(Shaped s, int argb, int shadowArgb, float dxCss, float dyCss) {
        if (s.fallback()) {
            return null;
        }
        String key = "s" + Integer.toHexString(argb) + ',' + Integer.toHexString(shadowArgb) + ',' + dxCss + ',' + dyCss;
        Raster r = styled(s, key);
        if (r == null) {
            r = render(s, new Color(argb, true), null, dxCss * s.scale, dyCss * s.scale, false,
                    new Color(shadowArgb, true));
            putStyled(s, key, r);
        }
        r.lastUsed = clock;
        return r;
    }

    /**
     * {@code text-shadow: 0 0 blur color}: the glyphs' coverage blurred by a Gaussian of standard
     * deviation blur/2 (what Chrome uses), in the given colour. Drawn under the text itself.
     */
    public static Raster glow(Shaped s, float blurCss, int argb) {
        if (s.fallback()) {
            return null;
        }
        String key = "glow" + blurCss + ',' + Integer.toHexString(argb);
        Raster r = styled(s, key);
        if (r == null) {
            Raster m = render(s, Color.WHITE, null, 0.0F, 0.0F, true);
            float sigma = blurCss / 2.0F * s.scale;
            int pad = (int) Math.ceil(sigma * 3.0F);
            int w = m.width + pad * 2;
            int h = m.height + pad * 2;
            float[] a = new float[w * h];
            for (int y = 0; y < m.height; y++) {
                for (int x = 0; x < m.width; x++) {
                    a[(y + pad) * w + x + pad] = (m.argb[y * m.width + x] >>> 24) / 255.0F;
                }
            }
            int[] boxes = boxes(sigma, 3);
            float[] tmp = new float[w * h];
            for (int b : boxes) {
                boxH(a, tmp, w, h, (b - 1) / 2);
                boxV(tmp, a, w, h, (b - 1) / 2);
            }
            int rgb = argb & 0xFFFFFF;
            float alpha = ((argb >>> 24) & 0xFF) / 255.0F;
            int[] px = new int[w * h];
            for (int i = 0; i < px.length; i++) {
                int al = Math.round(Math.min(1.0F, a[i]) * alpha * 255.0F);
                px[i] = (al << 24) | rgb;
            }
            r = new Raster(w, h, px, false, m.originX + pad, m.baseline + pad);
            putStyled(s, key, r);
        }
        r.lastUsed = clock;
        return r;
    }

    /** Box widths for a 3-pass box blur approximating a Gaussian. */
    private static int[] boxes(float sigma, int n) {
        double wIdeal = Math.sqrt((12 * sigma * sigma / n) + 1);
        int wl = (int) Math.floor(wIdeal);
        if (wl % 2 == 0) {
            wl--;
        }
        int wu = wl + 2;
        double mIdeal = (12 * sigma * sigma - n * wl * wl - 4 * n * wl - 3 * n) / (-4.0 * wl - 4);
        long m = Math.round(mIdeal);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = i < m ? wl : wu;
        }
        return out;
    }

    private static void boxH(float[] src, float[] dst, int w, int h, int r) {
        float k = 1.0F / (r + r + 1);
        for (int y = 0; y < h; y++) {
            int o = y * w;
            float acc = 0.0F;
            for (int x = -r; x <= r; x++) {
                acc += x >= 0 && x < w ? src[o + x] : 0.0F;
            }
            for (int x = 0; x < w; x++) {
                dst[o + x] = acc * k;
                int add = x + r + 1;
                int sub = x - r;
                acc += (add < w ? src[o + add] : 0.0F) - (sub >= 0 ? src[o + sub] : 0.0F);
            }
        }
    }

    private static void boxV(float[] src, float[] dst, int w, int h, int r) {
        float k = 1.0F / (r + r + 1);
        for (int x = 0; x < w; x++) {
            float acc = 0.0F;
            for (int y = -r; y <= r; y++) {
                acc += y >= 0 && y < h ? src[y * w + x] : 0.0F;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = acc * k;
                int add = y + r + 1;
                int sub = y - r;
                acc += (add < h ? src[add * w + x] : 0.0F) - (sub >= 0 ? src[sub * w + x] : 0.0F);
            }
        }
    }

    private static Raster styled(Shaped s, String key) {
        return s.styled == null ? null : s.styled.get(key);
    }

    private static void putStyled(Shaped s, String key, Raster r) {
        if (s.styled == null) {
            s.styled = new java.util.HashMap<>();
        }
        s.styled.put(key, r);
    }

    private static final class GradientSpec {
        final float top;
        final float bottom;
        final float[] stops;
        final Color[] colors;

        GradientSpec(float top, float bottom, float[] stops, Color[] colors) {
            this.top = top;
            this.bottom = bottom;
            this.stops = stops;
            this.colors = colors;
        }
    }

    private static Raster render(Shaped s, Color color, GradientSpec gradient, float sdx, float sdy, boolean mask) {
        return render(s, color, gradient, sdx, sdy, mask, null);
    }

    private static Raster render(Shaped s, Color color, GradientSpec gradient, float sdx, float sdy,
                                 boolean mask, Color shadow) {
        Rectangle total = null;
        for (int i = 0; i < s.runs.length; i++) {
            Rectangle b = s.runs[i].getPixelBounds(FRC, s.runX[i], 0.0F);
            if (b.isEmpty()) {
                continue;
            }
            total = total == null ? b : total.union(b);
            if (shadow != null) {
                Rectangle sb = s.runs[i].getPixelBounds(FRC, s.runX[i] + sdx, sdy);
                total = total.union(sb);
            }
        }
        if (total == null) {
            return new Raster(0, 0, new int[0], mask, 0, 0);
        }
        int pad = 1;
        int w = total.width + pad * 2;
        int h = total.height + pad * 2;
        int originX = -total.x + pad;
        int baseline = -total.y + pad;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // Glyphs are filled as outlines rather than drawn through the glyph cache: the outline
            // fill is Skia's result (what the design was seen in), the cache is FreeType's, and the
            // two disagree visibly on the pixel face, whose glyphs are stacks of overlapping squares.
            if (shadow != null) {
                g.setColor(shadow);
                for (int i = 0; i < s.runs.length; i++) {
                    g.fill(s.runs[i].getOutline(originX + s.runX[i] + sdx, baseline + sdy));
                }
            }
            Paint paint = color;
            if (gradient != null) {
                paint = new LinearGradientPaint(new Point2D.Float(0.0F, baseline + gradient.top),
                        new Point2D.Float(0.0F, baseline + gradient.bottom), gradient.stops, gradient.colors);
            }
            g.setPaint(paint);
            for (int i = 0; i < s.runs.length; i++) {
                g.fill(s.runs[i].getOutline(originX + s.runX[i], baseline));
            }
        } finally {
            g.dispose();
        }
        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
        return new Raster(w, h, data, mask, originX, baseline);
    }

    /** Frees rasters nobody has drawn for {@code frames} frames. The shapes themselves stay. */
    public static void trim(long frames) {
        long cutoff = clock - frames;
        Iterator<Shaped> it = CACHE.values().iterator();
        while (it.hasNext()) {
            Shaped s = it.next();
            if (s.mask != null && s.mask.lastUsed < cutoff) {
                Rasters.release(s.mask);
                s.mask = null;
            }
            if (s.styled != null) {
                s.styled.values().removeIf(r -> {
                    if (r.lastUsed < cutoff) {
                        Rasters.release(r);
                        return true;
                    }
                    return false;
                });
            }
        }
    }
}
