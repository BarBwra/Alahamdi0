package com.barbwra.mlum.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * The clickable rectangles of the frame just painted, in device pixels, topmost last.
 *
 * <p>Filled while painting, so what can be clicked is exactly what was drawn - including where the
 * static transition shook it to and whatever a modal covered.</p>
 */
public final class Hits {

    public static final class Hit {
        public final String id;
        public final Object data;
        public final int x0;
        public final int y0;
        public final int x1;
        public final int y1;

        Hit(String id, Object data, int x0, int y0, int x1, int y1) {
            this.id = id;
            this.data = data;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        public boolean contains(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    /** Everything under this (a modal's scrim) swallows clicks from what it covers. */
    private int barrier = -1;

    public void clear() {
        hits.clear();
        barrier = -1;
    }

    public void add(String id, Object data, int x0, int y0, int x1, int y1) {
        hits.add(new Hit(id, data, x0, y0, x1, y1));
    }

    /** Marks everything recorded so far as covered: {@link #at} will not return it. */
    public void cover() {
        barrier = hits.size();
    }

    /** Topmost hit under the point, or null. */
    public Hit at(double x, double y) {
        for (int i = hits.size() - 1; i >= Math.max(0, barrier); i--) {
            Hit h = hits.get(i);
            if (h.contains(x, y)) {
                return h;
            }
        }
        return null;
    }

    /** Topmost hit with an id starting with {@code prefix}. */
    public Hit at(double x, double y, String prefix) {
        for (int i = hits.size() - 1; i >= Math.max(0, barrier); i--) {
            Hit h = hits.get(i);
            if (h.id.startsWith(prefix) && h.contains(x, y)) {
                return h;
            }
        }
        return null;
    }

    public List<Hit> all() {
        return hits;
    }
}
