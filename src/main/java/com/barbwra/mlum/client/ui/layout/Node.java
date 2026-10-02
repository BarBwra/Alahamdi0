package com.barbwra.mlum.client.ui.layout;

import com.barbwra.mlum.client.ui.text.UiFont;

import java.util.ArrayList;

/**
 * One box of a screen, styled with the handful of CSS properties the design actually uses.
 *
 * <p>A deliberately small box model: {@code box-sizing:border-box} everywhere (as the design's
 * {@code *{box-sizing:border-box}}), block / flex row / flex column / grid containers, inline text
 * with mixed runs, absolute positioning inside the parent's padding box. Rows lay out right to left
 * unless {@link #ltr} is set, because the whole design is {@code direction:rtl}.</p>
 *
 * <p>All lengths are CSS pixels. Results ({@link #x}, {@link #y}, {@link #w}, {@link #h}) are the
 * border box in canvas coordinates after {@link Layout#layout}.</p>
 */
public final class Node {

    public static final int BLOCK = 0;
    public static final int ROW = 1;
    public static final int COL = 2;
    public static final int GRID = 3;
    public static final int TEXT = 4;
    public static final int LEAF = 5;

    public static final int START = 0;
    public static final int CENTER = 1;
    public static final int END = 2;
    public static final int STRETCH = 3;
    public static final int BASELINE = 4;
    public static final int BETWEEN = 5;

    public static final int SOLID = 0;
    public static final int DASHED = 1;

    public static final float AUTO = Float.NaN;
    public static final float INF = Float.POSITIVE_INFINITY;

    public int kind;

    /* ---- box ---- */
    public float width = AUTO;
    public float height = AUTO;
    /** AUTO means CSS {@code min-width:auto}: min-content for flex items, 0 elsewhere. */
    public float minWidth = AUTO;
    /** AUTO means CSS {@code min-height:auto}: content height for column flex items, 0 elsewhere. */
    public float minHeight = AUTO;
    public float maxWidth = INF;
    public float maxHeight = INF;
    public float mt;
    public float mr;
    public float mb;
    public float ml;
    public float pt;
    public float pr;
    public float pb;
    public float pl;
    public float bt;
    public float br;
    public float bb;
    public float bl;
    public int borderColor;
    /** Per-side colours, used only when set (top, right, bottom, left). */
    public int[] borderColors;
    public int borderStyle = SOLID;
    public int bg;
    public int[] gradColors;
    public float[] gradStops;
    /** Gradient runs left to right when true, top to bottom when false. */
    public boolean gradH;
    public Painter under;
    public Painter over;
    public float opacity = 1.0F;
    public boolean clip;

    /* ---- as a flex / grid item ---- */
    public float grow;
    public float shrink = 1.0F;
    public float basis = AUTO;
    public int alignSelf = -1;
    public int colSpan = 1;

    /* ---- absolute positioning (relative to the parent's padding box) ---- */
    public boolean abs;
    public float left = AUTO;
    public float right = AUTO;
    public float top = AUTO;
    public float bottom = AUTO;
    /** Percent parts of left/right/top/bottom, as fractions of the containing block. */
    public float leftPct;
    public float rightPct;
    public float topPct;
    public float bottomPct;
    /** width as a fraction of the containing block (absolute boxes only); 0 = unset. */
    public float widthPct;
    /** transform: translate(x%, y%) as fractions of the node's own size. */
    public float shiftX;
    public float shiftY;
    public int z;

    /* ---- as a container ---- */
    public boolean ltr;
    public float gapX;
    public float gapY;
    public int alignItems = STRETCH;
    public int justify = START;
    public boolean wrap;
    /** Grid tracks: positive = px, negative = fr (-1 is 1fr), zero = auto. */
    public float[] cols;
    public float[] rows;
    public final ArrayList<Node> kids = new ArrayList<>();

    /* ---- as text ---- */
    public UiFont font;
    public float size;
    public float lh;
    public int color;
    public boolean rtl = true;
    public int textAlign = START;
    public boolean wrapText;
    public final ArrayList<Span> spans = new ArrayList<>();

    /* ---- as a leaf ---- */
    public float leafW;
    public float leafH;

    /* ---- interaction ---- */
    public String hit;
    public Object hitData;
    /** Free slot for painters: an item, a sprite name, a colour... */
    public Object data;
    /** A name for tooling (the desktop preview dumps tagged boxes to diff against the design). */
    public String tag;

    /* ---- results ---- */
    public float x;
    public float y;
    public float w;
    public float h;
    Node parent;
    ArrayList<Layout.Line> lines;
    float linesFor = Float.NaN;

    public Node(int kind) {
        this.kind = kind;
    }

    /* ==================================================================== fluent setters */

    public Node add(Node child) {
        if (child != null) {
            child.parent = this;
            kids.add(child);
        }
        return this;
    }

    public Node add(Node... children) {
        for (Node c : children) {
            add(c);
        }
        return this;
    }

    public Node span(Span s) {
        spans.add(s);
        return this;
    }

    public Node w(float v) {
        width = v;
        return this;
    }

    public Node h(float v) {
        height = v;
        return this;
    }

    public Node size(float wv, float hv) {
        width = wv;
        height = hv;
        return this;
    }

    public Node minW(float v) {
        minWidth = v;
        return this;
    }

    public Node minH(float v) {
        minHeight = v;
        return this;
    }

    public Node maxW(float v) {
        maxWidth = v;
        return this;
    }

    public Node maxH(float v) {
        maxHeight = v;
        return this;
    }

    /** CSS order: top, right, bottom, left. */
    public Node pad(float t, float r, float b, float l) {
        pt = t;
        pr = r;
        pb = b;
        pl = l;
        return this;
    }

    public Node pad(float v) {
        return pad(v, v, v, v);
    }

    /** Vertical, horizontal. */
    public Node pad(float v, float hz) {
        return pad(v, hz, v, hz);
    }

    public Node mar(float t, float r, float b, float l) {
        mt = t;
        mr = r;
        mb = b;
        ml = l;
        return this;
    }

    public Node border(float width, int argb) {
        bt = br = bb = bl = width;
        borderColor = argb;
        return this;
    }

    public Node border(float t, float r, float b, float l, int argb) {
        bt = t;
        br = r;
        bb = b;
        bl = l;
        borderColor = argb;
        return this;
    }

    public Node dashed() {
        borderStyle = DASHED;
        return this;
    }

    public Node bg(int argb) {
        bg = argb;
        return this;
    }

    /** Top-to-bottom gradient. */
    public Node vgrad(float[] stops, int[] colors) {
        gradStops = stops;
        gradColors = colors;
        gradH = false;
        return this;
    }

    /** Left-to-right gradient. */
    public Node hgrad(float[] stops, int[] colors) {
        gradStops = stops;
        gradColors = colors;
        gradH = true;
        return this;
    }

    public Node under(Painter p) {
        under = p;
        return this;
    }

    public Node over(Painter p) {
        over = p;
        return this;
    }

    public Node opacity(float v) {
        opacity = v;
        return this;
    }

    public Node clip() {
        clip = true;
        return this;
    }

    public Node grow(float v) {
        grow = v;
        return this;
    }

    public Node shrink(float v) {
        shrink = v;
        return this;
    }

    public Node basis(float v) {
        basis = v;
        return this;
    }

    public Node self(int align) {
        alignSelf = align;
        return this;
    }

    /** grid-column span; -1 spans every column ({@code 1/-1}). */
    public Node spanCols(int columns) {
        colSpan = columns;
        return this;
    }

    /** position:absolute inside the parent's padding box; pass AUTO for unset sides. */
    public Node abs(float l, float t, float r, float b) {
        abs = true;
        left = l;
        top = t;
        right = r;
        bottom = b;
        return this;
    }

    /** left:{pct}% (+ px); pass AUTO px to leave the side unset. */
    public Node pct(float leftFrac, float topFrac, float rightFrac, float bottomFrac) {
        leftPct = leftFrac;
        topPct = topFrac;
        rightPct = rightFrac;
        bottomPct = bottomFrac;
        return this;
    }

    public Node translate(float xFrac, float yFrac) {
        shiftX = xFrac;
        shiftY = yFrac;
        return this;
    }

    public Node z(int v) {
        z = v;
        return this;
    }

    public Node ltr() {
        ltr = true;
        return this;
    }

    public Node gap(float v) {
        gapX = gapY = v;
        return this;
    }

    public Node gap(float row, float col) {
        gapY = row;
        gapX = col;
        return this;
    }

    public Node align(int v) {
        alignItems = v;
        return this;
    }

    public Node justify(int v) {
        justify = v;
        return this;
    }

    public Node wrap() {
        wrap = true;
        return this;
    }

    public Node cols(float... tracks) {
        cols = tracks;
        return this;
    }

    public Node rows(float... tracks) {
        rows = tracks;
        return this;
    }

    public Node hit(String id) {
        hit = id;
        return this;
    }

    public Node hit(String id, Object payload) {
        hit = id;
        hitData = payload;
        return this;
    }

    public Node tag(String v) {
        tag = v;
        return this;
    }

    public Node data(Object v) {
        data = v;
        return this;
    }

    public Node textAlign(int v) {
        textAlign = v;
        return this;
    }

    public Node wrapText() {
        wrapText = true;
        return this;
    }

    public Node leaf(float wv, float hv) {
        leafW = wv;
        leafH = hv;
        return this;
    }

    /* ==================================================================== geometry helpers */

    public float right() {
        return x + w;
    }

    public float bottom() {
        return y + h;
    }

    public float cx() {
        return x + w / 2.0F;
    }

    public float cy() {
        return y + h / 2.0F;
    }

    public boolean contains(float px, float py) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    public Node parent() {
        return parent;
    }

    static boolean auto(float v) {
        return Float.isNaN(v);
    }
}
