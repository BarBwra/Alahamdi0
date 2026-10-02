package com.barbwra.mlum.client.ui.layout;

import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.Draw;
import com.barbwra.mlum.client.ui.Hits;
import com.barbwra.mlum.client.ui.Px;

import java.util.ArrayList;
import java.util.List;

/**
 * Paints a laid-out tree in CSS order: background, border, custom background painter, in-flow
 * children, text, positioned children by z, then the overlay painter. Records every node with a
 * {@link Node#hit} id as a clickable rectangle while it goes.
 */
public final class Paint {

    private Paint() {
    }

    public static void paint(Canvas c, Node n, Hits hits) {
        if (n == null || n.opacity <= 0.0F) {
            return;
        }
        boolean alpha = n.opacity < 0.999F;
        if (alpha) {
            c.pushAlpha(n.opacity);
        }
        int x0 = Px.d(n.x);
        int y0 = Px.d(n.y);
        int x1 = Px.d(n.x + n.w);
        int y1 = Px.d(n.y + n.h);
        if ((n.bg >>> 24) != 0) {
            c.fill(x0, y0, x1, y1, n.bg);
        }
        if (n.gradColors != null) {
            if (n.gradH) {
                Draw.hgrad(c, n.x, n.y, n.w, n.h, n.gradStops, n.gradColors);
            } else {
                Draw.vgrad(c, n.x, n.y, n.w, n.h, n.gradStops, n.gradColors);
            }
        }
        if (n.under != null) {
            n.under.paint(c, n);
        }
        paintBorder(c, n, x0, y0, x1, y1);
        if (n.hit != null && hits != null) {
            hits.add(n.hit, n.hitData, x0, y0, x1, y1);
        }
        if (n.clip) {
            c.pushClip(Px.d(n.x + n.bl), Px.d(n.y + n.bt), Px.d(n.x + n.w - n.br), Px.d(n.y + n.h - n.bb));
        }
        List<Node> positioned = null;
        for (Node k : n.kids) {
            if (k.abs) {
                if (positioned == null) {
                    positioned = new ArrayList<>();
                }
                positioned.add(k);
            } else if (k.z == 0) {
                paint(c, k, hits);
            }
        }
        if (n.kind == Node.TEXT) {
            paintText(c, n, hits);
        }
        // raised in-flow children (z > 0) after their siblings
        for (Node k : n.kids) {
            if (!k.abs && k.z != 0) {
                paint(c, k, hits);
            }
        }
        if (positioned != null) {
            positioned.sort((a, b) -> Integer.compare(a.z, b.z));
            for (Node k : positioned) {
                paint(c, k, hits);
            }
        }
        if (n.clip) {
            c.popClip();
        }
        if (n.over != null) {
            n.over.paint(c, n);
        }
        if (alpha) {
            c.popAlpha();
        }
    }

    static void paintBorder(Canvas c, Node n, int x0, int y0, int x1, int y1) {
        int t = Px.border(n.bt);
        int r = Px.border(n.br);
        int b = Px.border(n.bb);
        int l = Px.border(n.bl);
        if (t + r + b + l == 0) {
            return;
        }
        int[] colors = n.borderColors != null ? n.borderColors
                : new int[]{n.borderColor, n.borderColor, n.borderColor, n.borderColor};
        float cssW = Math.max(Math.max(n.bt, n.bb), Math.max(n.bl, n.br));
        Draw.border(c, x0, y0, x1, y1, t, r, b, l, colors, n.borderStyle == Node.DASHED, cssW);
    }

    static void paintText(Canvas c, Node n, Hits hits) {
        if (n.lines == null) {
            return;
        }
        for (Layout.Line line : n.lines) {
            float base = n.y + n.bt + n.pt + line.top + line.above;
            for (Layout.Piece p : line.pieces) {
                if (p.box != null) {
                    paint(c, p.box, hits);
                    continue;
                }
                Span s = p.span;
                float by = base - s.valign;
                if (s.glowBlur > 0.0F) {
                    Draw.glowText(c, p.shaped, p.x, by, s.glowBlur, s.glowColor);
                }
                switch (s.kind) {
                    case Span.GRADIENT -> Draw.gradientText(c, p.shaped, p.x, by, s.gradTop, s.gradBottom,
                            s.gradStops, s.gradColors);
                    case Span.SHADOW -> Draw.shadowText(c, p.shaped, p.x, by, s.color, s.shadowColor,
                            s.shadowDx, s.shadowDy);
                    default -> Draw.text(c, p.shaped, p.x, by, s.color);
                }
            }
        }
    }
}
