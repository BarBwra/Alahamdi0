package com.barbwra.mlum.client.ui.layout;

import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;
import com.barbwra.mlum.client.ui.text.UiFont;

import java.util.ArrayList;
import java.util.List;

import static com.barbwra.mlum.client.ui.layout.Node.AUTO;
import static com.barbwra.mlum.client.ui.layout.Node.BASELINE;
import static com.barbwra.mlum.client.ui.layout.Node.BETWEEN;
import static com.barbwra.mlum.client.ui.layout.Node.BLOCK;
import static com.barbwra.mlum.client.ui.layout.Node.CENTER;
import static com.barbwra.mlum.client.ui.layout.Node.COL;
import static com.barbwra.mlum.client.ui.layout.Node.END;
import static com.barbwra.mlum.client.ui.layout.Node.GRID;
import static com.barbwra.mlum.client.ui.layout.Node.LEAF;
import static com.barbwra.mlum.client.ui.layout.Node.ROW;
import static com.barbwra.mlum.client.ui.layout.Node.START;
import static com.barbwra.mlum.client.ui.layout.Node.STRETCH;
import static com.barbwra.mlum.client.ui.layout.Node.TEXT;

/**
 * Lays out a {@link Node} tree the way Chrome laid out the design.
 *
 * <p>Implements the parts of CSS the design uses and nothing else: block flow with sibling margin
 * collapsing and auto margins; flexbox (grow/shrink with freezing, wrap, auto margins, justify and
 * align including baseline); grid with px/fr/auto columns and rows, column spans, right-to-left
 * track order; inline formatting with mixed fonts, Chrome's line-box maths (rounded ascent/descent,
 * floored half-leading) and greedy word wrap; absolute positioning in the padding box.</p>
 */
public final class Layout {

    private Layout() {
    }

    /* ================================================================== inline results */

    static final class Piece {
        Span span;
        String text;
        Shaped shaped;
        Node box;
        float w;
        /** Space before this piece on its line (collapsed whitespace). */
        float gap;
        /** Left edge, CSS px, canvas coordinates. */
        float x;
        /** Whether this word starts a new span (for merging words back into runs). */
        int spanIndex;
    }

    static final class Line {
        final ArrayList<Piece> pieces = new ArrayList<>();
        float width;
        float above;
        float below;
        /** Relative to the content box top. */
        float top;
    }

    /* ================================================================== entry point */

    public static void layout(Node root, float x, float y, float w, float h) {
        place(root, x, y, w, h);
    }

    /** Lays a node out at its own preferred size (height for the given width). */
    public static void layoutAuto(Node root, float x, float y, float w) {
        float width = Node.auto(root.width) ? w : root.width;
        place(root, x, y, width, heightFor(root, width));
    }

    /* ================================================================== helpers */

    static boolean auto(float v) {
        return Float.isNaN(v);
    }

    static float ml(Node n) {
        return auto(n.ml) ? 0.0F : n.ml;
    }

    static float mr(Node n) {
        return auto(n.mr) ? 0.0F : n.mr;
    }

    static float mt(Node n) {
        return auto(n.mt) ? 0.0F : n.mt;
    }

    static float mb(Node n) {
        return auto(n.mb) ? 0.0F : n.mb;
    }

    static float hMar(Node n) {
        return ml(n) + mr(n);
    }

    static float vMar(Node n) {
        return mt(n) + mb(n);
    }

    static float hExtra(Node n) {
        return n.pl + n.pr + n.bl + n.br;
    }

    static float vExtra(Node n) {
        return n.pt + n.pb + n.bt + n.bb;
    }

    static float clampW(Node n, float v) {
        float min = auto(n.minWidth) ? 0.0F : n.minWidth;
        return Math.max(min, Math.min(n.maxWidth, v));
    }

    static float clampH(Node n, float v) {
        float min = auto(n.minHeight) ? 0.0F : n.minHeight;
        return Math.max(min, Math.min(n.maxHeight, v));
    }

    static List<Node> flow(Node n) {
        List<Node> out = new ArrayList<>(n.kids.size());
        for (Node k : n.kids) {
            if (!k.abs) {
                out.add(k);
            }
        }
        return out;
    }

    /* ================================================================== intrinsic widths */

    /** max-content border-box width. */
    public static float prefW(Node n) {
        if (!auto(n.width)) {
            return n.width;
        }
        float c;
        switch (n.kind) {
            case TEXT -> c = textPref(n);
            case LEAF -> c = n.leafW;
            case ROW -> {
                float sum = 0.0F;
                int count = 0;
                for (Node k : flow(n)) {
                    // max-content contribution: the item's own content, whatever its flex-basis
                    sum += (auto(k.width) ? prefW(k) : k.width) + hMar(k);
                    count++;
                }
                c = sum + (count > 1 ? n.gapX * (count - 1) : 0.0F);
            }
            case GRID -> c = gridPrefW(n);
            default -> {
                float m = 0.0F;
                for (Node k : flow(n)) {
                    m = Math.max(m, prefW(k) + hMar(k));
                }
                c = m;
            }
        }
        return clampW(n, c + hExtra(n));
    }

    /** min-content border-box width. */
    public static float minContentW(Node n) {
        if (!auto(n.width)) {
            return n.width;
        }
        float c;
        switch (n.kind) {
            case TEXT -> c = textMin(n);
            case LEAF -> c = n.leafW;
            case ROW -> {
                float sum = 0.0F;
                float max = 0.0F;
                int count = 0;
                for (Node k : flow(n)) {
                    float v = flexMinW(k) + hMar(k);
                    sum += v;
                    max = Math.max(max, v);
                    count++;
                }
                c = n.wrap ? max : sum + (count > 1 ? n.gapX * (count - 1) : 0.0F);
            }
            case GRID -> c = gridMinW(n);
            default -> {
                float m = 0.0F;
                for (Node k : flow(n)) {
                    m = Math.max(m, minContentW(k) + hMar(k));
                }
                c = m;
            }
        }
        return clampW(n, c + hExtra(n));
    }

    static float flexBaseW(Node k) {
        if (!auto(k.basis)) {
            return k.basis;
        }
        return auto(k.width) ? prefW(k) : k.width;
    }

    /** CSS automatic minimum size of a flex item on the horizontal axis. */
    static float flexMinW(Node k) {
        if (!auto(k.minWidth)) {
            return k.minWidth;
        }
        float content = minContentW(k);
        return auto(k.width) ? content : Math.min(k.width, content);
    }

    /* ================================================================== heights */

    /** Border-box height at border-box width {@code w}. */
    public static float heightFor(Node n, float w) {
        if (!auto(n.height)) {
            return n.height;
        }
        float cw = Math.max(0.0F, w - hExtra(n));
        float c;
        switch (n.kind) {
            case TEXT -> c = textHeight(n, cw);
            case LEAF -> c = n.leafH;
            case BLOCK -> c = blockHeight(n, cw);
            case COL -> c = colHeight(n, cw);
            case ROW -> c = rowHeight(n, cw);
            case GRID -> c = gridHeight(n, cw, Float.NaN);
            default -> c = 0.0F;
        }
        return clampH(n, c + vExtra(n));
    }

    static float blockChildW(Node k, float cw) {
        if (!auto(k.width)) {
            return clampW(k, k.width);
        }
        return clampW(k, cw - hMar(k));
    }

    static float blockHeight(Node n, float cw) {
        float cursor = 0.0F;
        float pending = 0.0F;
        boolean first = true;
        for (Node k : flow(n)) {
            float kw = blockChildW(k, cw);
            float kh = heightFor(k, kw);
            float top = first ? mt(k) : Math.max(pending, mt(k));
            cursor += top + kh;
            pending = mb(k);
            first = false;
        }
        return cursor + pending;
    }

    static float colChildW(Node n, Node k, float cw) {
        if (!auto(k.width)) {
            return clampW(k, k.width);
        }
        int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
        if (al == STRETCH) {
            return clampW(k, cw - hMar(k));
        }
        return clampW(k, Math.min(prefW(k), cw - hMar(k)));
    }

    static float colHeight(Node n, float cw) {
        float sum = 0.0F;
        int count = 0;
        for (Node k : flow(n)) {
            float kw = colChildW(n, k, cw);
            float base = !auto(k.basis) && k.basis > 0.0F ? k.basis : heightFor(k, kw);
            sum += base + vMar(k);
            count++;
        }
        return sum + (count > 1 ? n.gapY * (count - 1) : 0.0F);
    }

    static float rowHeight(Node n, float cw) {
        List<Node> items = flow(n);
        if (items.isEmpty()) {
            return 0.0F;
        }
        List<List<Node>> lines = n.wrap ? breakRow(n, items, cw) : List.of(items);
        float total = 0.0F;
        for (int li = 0; li < lines.size(); li++) {
            List<Node> line = lines.get(li);
            float[] sizes = resolveRow(n, line, cw);
            float lineH = 0.0F;
            for (int i = 0; i < line.size(); i++) {
                Node k = line.get(i);
                lineH = Math.max(lineH, heightFor(k, sizes[i]) + vMar(k));
            }
            // baseline-aligned items can push the line taller
            lineH = Math.max(lineH, baselineLineHeight(n, line, sizes));
            total += lineH;
            if (li > 0) {
                total += n.gapY;
            }
        }
        return total;
    }

    /** Height a row line needs when some of its items align on their baselines. */
    static float baselineLineHeight(Node n, List<Node> line, float[] sizes) {
        float maxAbove = 0.0F;
        float maxBelow = 0.0F;
        boolean any = false;
        for (int i = 0; i < line.size(); i++) {
            Node k = line.get(i);
            int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
            if (al != BASELINE) {
                continue;
            }
            float kh = heightFor(k, sizes[i]);
            place(k, 0.0F, 0.0F, sizes[i], kh);
            float b = baseline(k);
            if (Float.isNaN(b)) {
                b = kh;
            }
            maxAbove = Math.max(maxAbove, mt(k) + b);
            maxBelow = Math.max(maxBelow, kh - b + mb(k));
            any = true;
        }
        return any ? maxAbove + maxBelow : 0.0F;
    }

    /* ================================================================== flex resolution */

    static List<List<Node>> breakRow(Node n, List<Node> items, float cw) {
        List<List<Node>> lines = new ArrayList<>();
        List<Node> cur = new ArrayList<>();
        float used = 0.0F;
        for (Node k : items) {
            float outer = Math.max(flexMinW(k), Math.min(k.maxWidth, flexBaseW(k))) + hMar(k);
            float add = cur.isEmpty() ? outer : n.gapX + outer;
            if (!cur.isEmpty() && used + add > cw + 0.01F) {
                lines.add(cur);
                cur = new ArrayList<>();
                used = outer;
            } else {
                used += add;
            }
            cur.add(k);
        }
        if (!cur.isEmpty()) {
            lines.add(cur);
        }
        return lines;
    }

    /** Main sizes (border-box widths) of one flex line. */
    static float[] resolveRow(Node n, List<Node> items, float cw) {
        int c = items.size();
        float[] base = new float[c];
        float[] hypo = new float[c];
        float[] min = new float[c];
        float[] max = new float[c];
        float margins = c > 1 ? n.gapX * (c - 1) : 0.0F;
        for (int i = 0; i < c; i++) {
            Node k = items.get(i);
            base[i] = flexBaseW(k);
            min[i] = flexMinW(k);
            max[i] = k.maxWidth;
            hypo[i] = Math.max(min[i], Math.min(max[i], base[i]));
            margins += hMar(k);
        }
        return resolveFlex(items, base, hypo, min, max, cw - margins, true);
    }

    /**
     * CSS "resolve flexible lengths": grow or shrink, freeze min/max violators, repeat.
     * {@code avail} is the space for the items' outer sizes minus margins and gaps.
     */
    static float[] resolveFlex(List<Node> items, float[] base, float[] hypo, float[] min, float[] max,
                               float avail, boolean horizontal) {
        int c = items.size();
        float[] size = new float[c];
        boolean[] frozen = new boolean[c];
        float sumHypo = 0.0F;
        for (float v : hypo) {
            sumHypo += v;
        }
        boolean growing = sumHypo < avail;
        for (int i = 0; i < c; i++) {
            Node k = items.get(i);
            float factor = growing ? k.grow : k.shrink;
            if (factor <= 0.0F || (growing && base[i] > hypo[i]) || (!growing && base[i] < hypo[i])) {
                frozen[i] = true;
                size[i] = hypo[i];
            }
        }
        for (int guard = 0; guard <= c; guard++) {
            boolean all = true;
            for (boolean f : frozen) {
                all &= f;
            }
            if (all) {
                break;
            }
            float used = 0.0F;
            float factors = 0.0F;
            for (int i = 0; i < c; i++) {
                if (frozen[i]) {
                    used += size[i];
                } else {
                    used += base[i];
                    Node k = items.get(i);
                    factors += growing ? k.grow : k.shrink * base[i];
                }
            }
            float free = avail - used;
            float totalViolation = 0.0F;
            float[] target = new float[c];
            for (int i = 0; i < c; i++) {
                if (frozen[i]) {
                    continue;
                }
                Node k = items.get(i);
                float t = base[i];
                if (factors > 0.0F) {
                    float share = growing ? k.grow / factors : k.shrink * base[i] / factors;
                    t = base[i] + free * share;
                }
                float clamped = Math.max(min[i], Math.min(max[i], t));
                totalViolation += clamped - t;
                target[i] = clamped;
            }
            for (int i = 0; i < c; i++) {
                if (frozen[i]) {
                    continue;
                }
                float t = target[i];
                boolean freeze = Math.abs(totalViolation) < 1e-4F
                        || (totalViolation > 0.0F && t <= min[i] + 1e-4F)
                        || (totalViolation < 0.0F && t >= max[i] - 1e-4F);
                if (freeze) {
                    frozen[i] = true;
                    size[i] = t;
                }
            }
        }
        return size;
    }

    /* ================================================================== placement */

    public static void place(Node n, float x, float y, float w, float h) {
        n.x = x;
        n.y = y;
        n.w = w;
        n.h = h;
        float cx = x + n.bl + n.pl;
        float cy = y + n.bt + n.pt;
        float cw = Math.max(0.0F, w - hExtra(n));
        float ch = Math.max(0.0F, h - vExtra(n));
        switch (n.kind) {
            case BLOCK -> placeBlock(n, cx, cy, cw);
            case COL -> placeCol(n, cx, cy, cw, ch);
            case ROW -> placeRow(n, cx, cy, cw, ch);
            case GRID -> placeGrid(n, cx, cy, cw, ch);
            case TEXT -> placeText(n, cx, cy, cw);
            default -> {
            }
        }
        placeAbsolute(n);
    }

    /** Moves an already laid out subtree. */
    public static void shift(Node n, float dx, float dy) {
        n.x += dx;
        n.y += dy;
        for (Node k : n.kids) {
            shift(k, dx, dy);
        }
        if (n.lines != null) {
            for (Line line : n.lines) {
                for (Piece p : line.pieces) {
                    p.x += dx;
                    if (p.box != null) {
                        shift(p.box, dx, dy);
                    }
                }
            }
        }
    }

    static void placeBlock(Node n, float cx, float cy, float cw) {
        float cursor = cy;
        float pending = 0.0F;
        boolean first = true;
        for (Node k : flow(n)) {
            float kw = blockChildW(k, cw);
            float kh = heightFor(k, kw);
            float top = first ? mt(k) : Math.max(pending, mt(k));
            float ky = cursor + top;
            float kx;
            float free = cw - kw - hMar(k);
            boolean autoL = auto(k.ml);
            boolean autoR = auto(k.mr);
            if (autoL && autoR) {
                kx = cx + free / 2.0F;
            } else if (n.ltr) {
                kx = autoL ? cx + free + ml(k) : cx + ml(k);
            } else {
                // right to left: the start edge is the right one
                kx = autoR ? cx + ml(k) : cx + cw - mr(k) - kw;
            }
            place(k, kx, ky, kw, kh);
            cursor = ky + kh;
            pending = mb(k);
            first = false;
        }
    }

    static void placeCol(Node n, float cx, float cy, float cw, float ch) {
        List<Node> items = flow(n);
        int c = items.size();
        if (c == 0) {
            return;
        }
        float[] widths = new float[c];
        float[] base = new float[c];
        float[] hypo = new float[c];
        float[] min = new float[c];
        float[] max = new float[c];
        float margins = c > 1 ? n.gapY * (c - 1) : 0.0F;
        int autoMargins = 0;
        for (int i = 0; i < c; i++) {
            Node k = items.get(i);
            widths[i] = colChildW(n, k, cw);
            float content = auto(k.height) ? heightFor(k, widths[i]) : k.height;
            base[i] = !auto(k.basis) ? k.basis : content;
            // min-height:auto - a column item never shrinks below its content unless told to
            min[i] = auto(k.minHeight) ? content : k.minHeight;
            max[i] = k.maxHeight;
            hypo[i] = Math.max(min[i], Math.min(max[i], base[i]));
            margins += vMar(k);
            if (auto(k.mt)) {
                autoMargins++;
            }
            if (auto(k.mb)) {
                autoMargins++;
            }
        }
        float[] sizes = resolveFlex(items, base, hypo, min, max, ch - margins, false);
        float used = margins;
        for (float s : sizes) {
            used += s;
        }
        float free = ch - used;
        float perAuto = 0.0F;
        if (autoMargins > 0 && free > 0.0F) {
            perAuto = free / autoMargins;
            free = 0.0F;
        }
        float off = 0.0F;
        float between = n.gapY;
        if (free > 0.0F) {
            switch (n.justify) {
                case END -> off = free;
                case CENTER -> off = free / 2.0F;
                case BETWEEN -> {
                    if (c > 1) {
                        between += free / (c - 1);
                    }
                }
                default -> {
                }
            }
        }
        float cursor = cy + off;
        for (int i = 0; i < c; i++) {
            Node k = items.get(i);
            float top = auto(k.mt) ? perAuto : k.mt;
            float bot = auto(k.mb) ? perAuto : k.mb;
            cursor += top;
            int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
            float kw = widths[i];
            float kx;
            float freeX = cw - kw - hMar(k);
            if (al == CENTER) {
                kx = cx + freeX / 2.0F + ml(k);
            } else if (al == END) {
                kx = n.ltr ? cx + cw - mr(k) - kw : cx + ml(k);
            } else {
                kx = n.ltr ? cx + ml(k) : cx + cw - mr(k) - kw;
            }
            place(k, kx, cursor, kw, sizes[i]);
            cursor += sizes[i] + bot + between;
        }
    }

    static void placeRow(Node n, float cx, float cy, float cw, float ch) {
        List<Node> items = flow(n);
        if (items.isEmpty()) {
            return;
        }
        List<List<Node>> lines = n.wrap ? breakRow(n, items, cw) : List.of(items);
        float cursorY = cy;
        for (List<Node> line : lines) {
            int c = line.size();
            float[] sizes = resolveRow(n, line, cw);
            float lineH;
            if (!n.wrap) {
                lineH = ch;
            } else {
                lineH = 0.0F;
                for (int i = 0; i < c; i++) {
                    lineH = Math.max(lineH, heightFor(line.get(i), sizes[i]) + vMar(line.get(i)));
                }
                lineH = Math.max(lineH, baselineLineHeight(n, line, sizes));
            }
            float used = c > 1 ? n.gapX * (c - 1) : 0.0F;
            int autoMargins = 0;
            for (int i = 0; i < c; i++) {
                Node k = line.get(i);
                used += sizes[i] + hMar(k);
                if (auto(k.ml)) {
                    autoMargins++;
                }
                if (auto(k.mr)) {
                    autoMargins++;
                }
            }
            float free = cw - used;
            float perAuto = 0.0F;
            if (autoMargins > 0 && free > 0.0F) {
                perAuto = free / autoMargins;
                free = 0.0F;
            }
            float off = 0.0F;
            float between = n.gapX;
            if (free > 0.0F) {
                switch (n.justify) {
                    case END -> off = free;
                    case CENTER -> off = free / 2.0F;
                    case BETWEEN -> {
                        if (c > 1) {
                            between += free / (c - 1);
                        }
                    }
                    default -> {
                    }
                }
            }
            // main axis: right to left unless ltr
            float[] xs = new float[c];
            if (!n.ltr) {
                float xr = cx + cw - off;
                for (int i = 0; i < c; i++) {
                    Node k = line.get(i);
                    xr -= auto(k.mr) ? perAuto : k.mr;
                    xs[i] = xr - sizes[i];
                    xr = xs[i] - (auto(k.ml) ? perAuto : k.ml) - between;
                }
            } else {
                float xl = cx + off;
                for (int i = 0; i < c; i++) {
                    Node k = line.get(i);
                    xl += auto(k.ml) ? perAuto : k.ml;
                    xs[i] = xl;
                    xl = xs[i] + sizes[i] + (auto(k.mr) ? perAuto : k.mr) + between;
                }
            }
            // cross axis
            float maxAbove = 0.0F;
            boolean anyBaseline = false;
            float[] heights = new float[c];
            for (int i = 0; i < c; i++) {
                Node k = line.get(i);
                int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
                float kh;
                if (al == STRETCH && auto(k.height)) {
                    kh = clampH(k, lineH - vMar(k));
                } else {
                    kh = auto(k.height) ? heightFor(k, sizes[i]) : k.height;
                }
                heights[i] = kh;
                float ky = switch (al) {
                    case CENTER -> cursorY + (lineH - kh - vMar(k)) / 2.0F + mt(k);
                    case END -> cursorY + lineH - kh - mb(k);
                    default -> cursorY + mt(k);
                };
                place(k, xs[i], ky, sizes[i], kh);
                if (al == BASELINE) {
                    float b = baseline(k);
                    if (Float.isNaN(b)) {
                        b = kh;
                    }
                    maxAbove = Math.max(maxAbove, mt(k) + b);
                    anyBaseline = true;
                }
            }
            if (anyBaseline) {
                for (int i = 0; i < c; i++) {
                    Node k = line.get(i);
                    int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
                    if (al != BASELINE) {
                        continue;
                    }
                    float b = baseline(k);
                    if (Float.isNaN(b)) {
                        b = heights[i];
                    }
                    float want = cursorY + maxAbove - b;
                    shift(k, 0.0F, want - k.y);
                }
            }
            cursorY += lineH + n.gapY;
        }
    }

    /* ------------------------------------------------------------------ grid */

    static float[] tracks(Node n) {
        return n.cols == null ? new float[]{-1.0F} : n.cols;
    }

    static int colCount(Node n) {
        return tracks(n).length;
    }

    /** Assigns each in-flow item a {row, col, span}. Auto-flow by rows, no dense packing. */
    static int[][] gridPlacement(Node n, List<Node> items) {
        int cols = colCount(n);
        int[][] out = new int[items.size()][3];
        int row = 0;
        int col = 0;
        for (int i = 0; i < items.size(); i++) {
            Node k = items.get(i);
            int span = k.colSpan < 0 ? cols : Math.min(cols, Math.max(1, k.colSpan));
            if (col + span > cols) {
                row++;
                col = 0;
            }
            out[i][0] = row;
            out[i][1] = col;
            out[i][2] = span;
            col += span;
            if (col >= cols) {
                row++;
                col = 0;
            }
        }
        return out;
    }

    static float[] columnWidths(Node n, List<Node> items, int[][] at, float cw, boolean minContent) {
        float[] t = tracks(n);
        int cols = t.length;
        float[] out = new float[cols];
        float fixed = cols > 1 ? n.gapX * (cols - 1) : 0.0F;
        float fr = 0.0F;
        for (int c = 0; c < cols; c++) {
            if (t[c] > 0.0F) {
                out[c] = t[c];
                fixed += t[c];
            } else if (t[c] == 0.0F) {
                float m = 0.0F;
                for (int i = 0; i < items.size(); i++) {
                    if (at[i][1] == c && at[i][2] == 1) {
                        Node k = items.get(i);
                        m = Math.max(m, (minContent ? minContentW(k) : prefW(k)) + hMar(k));
                    }
                }
                out[c] = m;
                fixed += m;
            } else {
                fr += -t[c];
            }
        }
        if (fr > 0.0F) {
            float rest = Math.max(0.0F, cw - fixed);
            for (int c = 0; c < cols; c++) {
                if (t[c] < 0.0F) {
                    out[c] = Float.isInfinite(cw) ? 0.0F : rest * (-t[c]) / fr;
                }
            }
        }
        return out;
    }

    static float gridPrefW(Node n) {
        List<Node> items = flow(n);
        int[][] at = gridPlacement(n, items);
        float[] t = tracks(n);
        float sum = t.length > 1 ? n.gapX * (t.length - 1) : 0.0F;
        float frMax = 0.0F;
        float frSum = 0.0F;
        for (int c = 0; c < t.length; c++) {
            float m = 0.0F;
            for (int i = 0; i < items.size(); i++) {
                if (at[i][1] == c && at[i][2] == 1) {
                    m = Math.max(m, prefW(items.get(i)) + hMar(items.get(i)));
                }
            }
            if (t[c] > 0.0F) {
                sum += t[c];
            } else if (t[c] == 0.0F) {
                sum += m;
            } else {
                frMax = Math.max(frMax, m / -t[c]);
                frSum += -t[c];
            }
        }
        return sum + frMax * frSum;
    }

    static float gridMinW(Node n) {
        List<Node> items = flow(n);
        int[][] at = gridPlacement(n, items);
        float[] t = tracks(n);
        float sum = t.length > 1 ? n.gapX * (t.length - 1) : 0.0F;
        for (int c = 0; c < t.length; c++) {
            if (t[c] > 0.0F) {
                sum += t[c];
                continue;
            }
            float m = 0.0F;
            for (int i = 0; i < items.size(); i++) {
                if (at[i][1] == c && at[i][2] == 1) {
                    m = Math.max(m, minContentW(items.get(i)) + hMar(items.get(i)));
                }
            }
            sum += m;
        }
        return sum;
    }

    static float spanWidth(Node n, float[] widths, int col, int span) {
        float w = 0.0F;
        for (int c = col; c < col + span && c < widths.length; c++) {
            w += widths[c];
        }
        return w + n.gapX * (span - 1);
    }

    /** Row heights. {@code ch} NaN while measuring (fr rows then size to content). */
    static float[] rowHeights(Node n, List<Node> items, int[][] at, float[] widths, float ch) {
        int rowsN = 0;
        for (int[] a : at) {
            rowsN = Math.max(rowsN, a[0] + 1);
        }
        float[] spec = n.rows;
        if (spec != null) {
            rowsN = Math.max(rowsN, spec.length);
        }
        float[] out = new float[rowsN];
        for (int i = 0; i < items.size(); i++) {
            Node k = items.get(i);
            float kw = auto(k.width) ? spanWidth(n, widths, at[i][1], at[i][2]) - hMar(k) : k.width;
            float kh = heightFor(k, kw) + vMar(k);
            out[at[i][0]] = Math.max(out[at[i][0]], kh);
        }
        if (spec != null) {
            float fixed = rowsN > 1 ? n.gapY * (rowsN - 1) : 0.0F;
            float fr = 0.0F;
            for (int r = 0; r < rowsN; r++) {
                float s = r < spec.length ? spec[r] : 0.0F;
                if (s > 0.0F) {
                    out[r] = s;
                }
                if (s >= 0.0F) {
                    fixed += out[r];
                } else {
                    fr += -s;
                }
            }
            if (fr > 0.0F && !Float.isNaN(ch)) {
                // equal fr share, but never below any row's own content
                float rest = Math.max(0.0F, ch - fixed);
                float share = rest / fr;
                float content = 0.0F;
                for (int r = 0; r < rowsN; r++) {
                    float s = r < spec.length ? spec[r] : 0.0F;
                    if (s < 0.0F) {
                        content = Math.max(content, out[r] / -s);
                    }
                }
                share = Math.max(share, content);
                for (int r = 0; r < rowsN; r++) {
                    float s = r < spec.length ? spec[r] : 0.0F;
                    if (s < 0.0F) {
                        out[r] = share * -s;
                    }
                }
            } else if (fr > 0.0F) {
                // measuring: fr rows equalise to the tallest content among them
                float content = 0.0F;
                for (int r = 0; r < rowsN; r++) {
                    float s = r < spec.length ? spec[r] : 0.0F;
                    if (s < 0.0F) {
                        content = Math.max(content, out[r] / -s);
                    }
                }
                for (int r = 0; r < rowsN; r++) {
                    float s = r < spec.length ? spec[r] : 0.0F;
                    if (s < 0.0F) {
                        out[r] = content * -s;
                    }
                }
            }
        }
        return out;
    }

    static float gridHeight(Node n, float cw, float ch) {
        List<Node> items = flow(n);
        if (items.isEmpty()) {
            return 0.0F;
        }
        int[][] at = gridPlacement(n, items);
        float[] widths = columnWidths(n, items, at, cw, false);
        float[] heights = rowHeights(n, items, at, widths, ch);
        float sum = heights.length > 1 ? n.gapY * (heights.length - 1) : 0.0F;
        for (float v : heights) {
            sum += v;
        }
        return sum;
    }

    static void placeGrid(Node n, float cx, float cy, float cw, float ch) {
        List<Node> items = flow(n);
        if (items.isEmpty()) {
            return;
        }
        int[][] at = gridPlacement(n, items);
        float[] widths = columnWidths(n, items, at, cw, false);
        float[] heights = rowHeights(n, items, at, widths, ch);
        if (n.rows == null && heights.length > 0) {
            // auto rows stretch to fill a taller container (align-content: normal)
            float used = n.gapY * (heights.length - 1);
            for (float v : heights) {
                used += v;
            }
            if (ch > used + 0.01F) {
                float extra = (ch - used) / heights.length;
                for (int r = 0; r < heights.length; r++) {
                    heights[r] += extra;
                }
            }
        }
        float[] colX = new float[widths.length];
        if (n.ltr) {
            float xl = cx;
            for (int c = 0; c < widths.length; c++) {
                colX[c] = xl;
                xl += widths[c] + n.gapX;
            }
        } else {
            float xr = cx + cw;
            for (int c = 0; c < widths.length; c++) {
                colX[c] = xr - widths[c];
                xr = colX[c] - n.gapX;
            }
        }
        float[] rowY = new float[heights.length];
        float yy = cy;
        for (int r = 0; r < heights.length; r++) {
            rowY[r] = yy;
            yy += heights[r] + n.gapY;
        }
        for (int i = 0; i < items.size(); i++) {
            Node k = items.get(i);
            int row = at[i][0];
            int col = at[i][1];
            int span = at[i][2];
            float areaW = spanWidth(n, widths, col, span);
            // the area's left edge: for rtl the last spanned column is leftmost
            float areaX = n.ltr ? colX[col] : colX[Math.min(widths.length - 1, col + span - 1)];
            float areaH = heights[row];
            float kw = auto(k.width) ? clampW(k, areaW - hMar(k)) : k.width;
            int al = k.alignSelf >= 0 ? k.alignSelf : n.alignItems;
            float kh;
            if (al == STRETCH && auto(k.height)) {
                kh = clampH(k, areaH - vMar(k));
            } else {
                kh = auto(k.height) ? heightFor(k, kw) : k.height;
            }
            float ky = switch (al) {
                case CENTER -> rowY[row] + (areaH - kh - vMar(k)) / 2.0F + mt(k);
                case END -> rowY[row] + areaH - kh - mb(k);
                default -> rowY[row] + mt(k);
            };
            float kx;
            if (auto(k.width) || kw >= areaW - hMar(k) - 0.01F) {
                kx = areaX + ml(k);
            } else if (auto(k.ml) && auto(k.mr)) {
                kx = areaX + (areaW - kw) / 2.0F;
            } else {
                kx = n.ltr ? areaX + ml(k) : areaX + areaW - mr(k) - kw;
            }
            place(k, kx, ky, kw, kh);
        }
    }

    /* ------------------------------------------------------------------ absolute */

    static void placeAbsolute(Node n) {
        float px = n.x + n.bl;
        float py = n.y + n.bt;
        float pw = n.w - n.bl - n.br;
        float ph = n.h - n.bt - n.bb;
        for (Node k : n.kids) {
            if (!k.abs) {
                continue;
            }
            float kw;
            if (k.widthPct > 0.0F) {
                kw = pw * k.widthPct;
            } else if (!auto(k.width)) {
                kw = k.width;
            } else if (!auto(k.left) && !auto(k.right)) {
                kw = pw - k.left - k.right - hMar(k);
            } else {
                kw = prefW(k);
            }
            kw = clampW(k, kw);
            float kh;
            if (!auto(k.height)) {
                kh = k.height;
            } else if (!auto(k.top) && !auto(k.bottom)) {
                kh = ph - k.top - k.bottom - vMar(k);
            } else {
                kh = heightFor(k, kw);
            }
            kh = clampH(k, kh);
            float kx;
            if (!auto(k.left)) {
                kx = px + k.left + k.leftPct * pw + ml(k);
            } else if (!auto(k.right)) {
                kx = px + pw - k.right - k.rightPct * pw - mr(k) - kw;
            } else {
                kx = n.ltr ? px + n.pl : px + pw - n.pr - kw;
            }
            float ky;
            if (!auto(k.top)) {
                ky = py + k.top + k.topPct * ph + mt(k);
            } else if (!auto(k.bottom)) {
                ky = py + ph - k.bottom - k.bottomPct * ph - mb(k) - kh;
            } else {
                ky = py + n.pt;
            }
            kx += k.shiftX * kw;
            ky += k.shiftY * kh;
            place(k, kx, ky, kw, kh);
        }
    }

    /* ------------------------------------------------------------------ baseline */

    /** First baseline, measured from the node's border-box top; NaN when it has no text. */
    public static float baseline(Node n) {
        if (n.kind == TEXT) {
            if (n.lines == null || n.lines.isEmpty()) {
                return Float.NaN;
            }
            Line first = n.lines.get(0);
            return n.bt + n.pt + first.top + first.above;
        }
        if (n.kind == LEAF) {
            return Float.NaN;
        }
        for (Node k : n.kids) {
            if (k.abs) {
                continue;
            }
            float b = baseline(k);
            if (!Float.isNaN(b)) {
                return (k.y - n.y) + b;
            }
        }
        return Float.NaN;
    }

    /* ================================================================== inline text */

    private static final class Token {
        Span span;
        int spanIndex;
        String text;
        Node box;
        float w;
        /** Whitespace separated this token from the previous one. */
        boolean spaceBefore;
        /** Whose space it was - the font that measures the gap. */
        Span spaceOwner;
    }

    static float spaceWidth(Span s) {
        if (s == null || s.font == null && s.box != null) {
            return 0.0F;
        }
        return TextEngine.shape(" ", s.font, s.size, s.ls, s.rtl).width;
    }

    static String collapse(String t) {
        StringBuilder b = new StringBuilder(t.length());
        boolean ws = false;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == ' ' || ch == '\n' || ch == '\t' || ch == '\r') {
                if (!ws) {
                    b.append(' ');
                }
                ws = true;
            } else {
                b.append(ch);
                ws = false;
            }
        }
        return b.toString();
    }

    /** Splits the spans into tokens: words when wrapping, whole spans when not. */
    static List<Token> tokens(Node n, boolean words) {
        List<Token> out = new ArrayList<>();
        boolean pendingSpace = false;
        Span pendingOwner = null;
        for (int si = 0; si < n.spans.size(); si++) {
            Span s = n.spans.get(si);
            if (s.box != null) {
                Token t = new Token();
                t.span = s;
                t.spanIndex = si;
                t.box = s.box;
                t.w = prefW(s.box) + hMar(s.box);
                t.spaceBefore = pendingSpace;
                t.spaceOwner = pendingOwner;
                out.add(t);
                pendingSpace = false;
                pendingOwner = null;
                continue;
            }
            String text = collapse(s.text == null ? "" : s.text);
            boolean lead = text.startsWith(" ");
            boolean trail = text.endsWith(" ");
            text = text.trim();
            if (text.isEmpty()) {
                if (lead || trail) {
                    pendingSpace = true;
                    if (pendingOwner == null) {
                        pendingOwner = s;
                    }
                }
                continue;
            }
            if (lead && !pendingSpace) {
                pendingSpace = true;
                pendingOwner = s;
            }
            String[] parts = words ? text.split(" ") : new String[]{text};
            for (int p = 0; p < parts.length; p++) {
                Token t = new Token();
                t.span = s;
                t.spanIndex = si;
                t.text = parts[p];
                t.w = TextEngine.shape(parts[p], s.font, s.size, s.ls, s.rtl).width;
                if (p == 0) {
                    t.spaceBefore = pendingSpace;
                    t.spaceOwner = pendingOwner;
                } else {
                    t.spaceBefore = true;
                    t.spaceOwner = s;
                }
                out.add(t);
            }
            pendingSpace = trail;
            pendingOwner = trail ? s : null;
        }
        return out;
    }

    static float textPref(Node n) {
        List<Token> toks = tokens(n, false);
        float w = 0.0F;
        for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);
            if (i > 0 && t.spaceBefore) {
                w += spaceWidth(t.spaceOwner);
            }
            w += t.w;
        }
        return w;
    }

    static float textMin(Node n) {
        if (!n.wrapText) {
            return textPref(n);
        }
        float m = 0.0F;
        for (Token t : tokens(n, true)) {
            m = Math.max(m, t.w);
        }
        return m;
    }

    static float textHeight(Node n, float cw) {
        buildLines(n, cw);
        float hgt = 0.0F;
        for (Line l : n.lines) {
            hgt += l.above + l.below;
        }
        return hgt;
    }

    /** Wraps (if allowed) and measures lines for content width {@code cw}; cached per width. */
    static void buildLines(Node n, float cw) {
        if (n.lines != null && Math.abs(n.linesFor - cw) < 0.001F) {
            return;
        }
        ArrayList<Line> lines = new ArrayList<>();
        List<Token> toks = tokens(n, n.wrapText);
        Line cur = new Line();
        float width = 0.0F;
        for (Token t : toks) {
            float gap = t.spaceBefore ? spaceWidth(t.spaceOwner) : 0.0F;
            if (n.wrapText && !cur.pieces.isEmpty() && width + gap + t.w > cw + 0.01F) {
                cur.width = width;
                lines.add(cur);
                cur = new Line();
                width = 0.0F;
                gap = 0.0F;
            }
            if (cur.pieces.isEmpty()) {
                gap = 0.0F;
            }
            Piece p = new Piece();
            p.span = t.span;
            p.spanIndex = t.spanIndex;
            p.text = t.text;
            p.box = t.box;
            p.w = t.w;
            p.gap = gap;
            cur.pieces.add(p);
            width += gap + t.w;
        }
        cur.width = width;
        lines.add(cur);
        for (Line l : lines) {
            mergeWords(l);
            measureLine(n, l);
        }
        float top = 0.0F;
        for (Line l : lines) {
            l.top = top;
            top += l.above + l.below;
        }
        n.lines = lines;
        n.linesFor = cw;
    }

    /** Joins consecutive words of one span back into a single run, the way the browser shapes it. */
    static void mergeWords(Line line) {
        ArrayList<Piece> merged = new ArrayList<>();
        Piece run = null;
        StringBuilder text = null;
        for (Piece p : line.pieces) {
            boolean joinable = run != null && p.box == null && run.box == null
                    && p.spanIndex == run.spanIndex;
            if (joinable) {
                text.append(p.gap > 0.0F ? " " : "").append(p.text);
                continue;
            }
            if (run != null && run.box == null) {
                run.text = text.toString();
            }
            run = p;
            text = new StringBuilder(p.text == null ? "" : p.text);
            merged.add(p);
        }
        if (run != null && run.box == null) {
            run.text = text.toString();
        }
        float width = 0.0F;
        for (int i = 0; i < merged.size(); i++) {
            Piece p = merged.get(i);
            if (p.box == null) {
                Span s = p.span;
                p.shaped = TextEngine.shape(p.text, s.font, s.size, s.ls, s.rtl);
                p.w = p.shaped.width;
            }
            if (i == 0) {
                p.gap = 0.0F;
            }
            width += p.gap + p.w;
        }
        line.pieces.clear();
        line.pieces.addAll(merged);
        line.width = width;
    }

    /** Chrome's line box: every inline box sits on the baseline; the line spans all of them. */
    static void measureLine(Node n, Line line) {
        float above = 0.0F;
        float below = 0.0F;
        if (n.font != null) {
            float[] ab = inlineBox(n.font, n.size, n.lh);
            above = ab[0];
            below = ab[1];
        }
        for (Piece p : line.pieces) {
            if (p.box != null) {
                float bw = prefW(p.box);
                float bh = heightFor(p.box, bw);
                float b = boxBaseline(p.box, bw, bh);
                above = Math.max(above, b + mt(p.box) + p.span.valign);
                below = Math.max(below, bh - b + mb(p.box) - p.span.valign);
                continue;
            }
            Span s = p.span;
            float[] ab = inlineBox(s.font, s.size, s.lh);
            above = Math.max(above, ab[0] + s.valign);
            below = Math.max(below, ab[1] - s.valign);
        }
        line.above = above;
        line.below = below;
    }

    /** {above, below} the baseline for an inline box: rounded metrics, floored half-leading. */
    public static float[] inlineBox(UiFont font, float size, float lh) {
        int a = font == null ? Math.round(size * 0.9F) : font.ascent(size);
        int d = font == null ? Math.round(size * 0.25F) : font.descent(size);
        float leading = lh - (a + d);
        float halfTop = (float) Math.floor(leading / 2.0F);
        float above = a + halfTop;
        return new float[]{above, lh - above};
    }

    static void placeText(Node n, float cx, float cy, float cw) {
        buildLines(n, cw);
        for (Line line : n.lines) {
            float baseY = cy + line.top + line.above;
            boolean rtl = n.rtl;
            float start;
            int align = n.textAlign;
            if (align == CENTER) {
                start = rtl ? cx + (cw + line.width) / 2.0F : cx + (cw - line.width) / 2.0F;
            } else if ((align == START) == rtl) {
                start = rtl ? cx + cw : cx + cw - line.width;
            } else {
                start = rtl ? cx + line.width : cx;
            }
            if (rtl) {
                float xr = start;
                for (Piece p : line.pieces) {
                    xr -= p.gap;
                    p.x = xr - p.w;
                    xr = p.x;
                    placeInlineBox(p, baseY);
                }
            } else {
                float xl = start;
                for (Piece p : line.pieces) {
                    xl += p.gap;
                    p.x = xl;
                    xl += p.w;
                    placeInlineBox(p, baseY);
                }
            }
        }
    }

    static void placeInlineBox(Piece p, float baseY) {
        if (p.box == null) {
            return;
        }
        float bw = prefW(p.box);
        float bh = heightFor(p.box, bw);
        float b = boxBaseline(p.box, bw, bh);
        place(p.box, p.x + ml(p.box), baseY - p.span.valign - b, bw, bh);
    }

    /**
     * An atomic inline's baseline: its first line of text (inline-flex, inline-block with text),
     * or its bottom edge when it has none (an icon, an empty box).
     */
    static float boxBaseline(Node box, float bw, float bh) {
        place(box, 0.0F, 0.0F, bw, bh);
        float b = baseline(box);
        return Float.isNaN(b) ? bh : b;
    }

    /** Baseline of line {@code i} in canvas coordinates. */
    public static float lineBaseline(Node n, int i) {
        Line l = n.lines.get(i);
        return n.y + n.bt + n.pt + l.top + l.above;
    }

    public static int lineCount(Node n) {
        return n.lines == null ? 0 : n.lines.size();
    }
}
