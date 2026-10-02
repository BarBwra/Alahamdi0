package com.barbwra.mlum.client.ui.text;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The five font files the design uses: MLUM Kufi 400/600/700 (Noto Kufi Arabic, Arabic and Latin
 * subsets merged into one file per weight) and MLUM Pixel 600/700 (Handjet).
 *
 * <p>Weight matching is CSS's: Kufi asked for 500 gets 400, Pixel asked for anything under 600 gets
 * 600. A font that fails to load leaves {@link #ready()} false and every screen falls back to the
 * game's own font renderer, so a broken file costs looks, never a crash.</p>
 */
public final class Fonts {

    /** Opens a font file by its bare name, e.g. {@code mlum-kufi-400.ttf}. */
    public interface Source {
        InputStream open(String file) throws IOException;
    }

    private static final UiFont[] KUFI = new UiFont[3];   // 400, 600, 700
    private static final UiFont[] PIXEL = new UiFont[2];  // 600, 700
    private static Font fallback;
    private static volatile boolean ready;
    private static Throwable failure;

    private Fonts() {
    }

    public static boolean ready() {
        return ready;
    }

    public static Throwable failure() {
        return failure;
    }

    /** Loads every face. Safe to call more than once; later calls are no-ops once loaded. */
    public static synchronized boolean load(Source source) {
        if (ready) {
            return true;
        }
        try {
            int id = 0;
            int[] kufiWeights = {400, 600, 700};
            for (int i = 0; i < 3; i++) {
                Font f = read(source, "mlum-kufi-" + kufiWeights[i] + ".ttf");
                KUFI[i] = new UiFont(UiFont.KUFI, kufiWeights[i], f, 1282.0F / 1000.0F, 615.0F / 1000.0F, id++);
            }
            int[] pixelWeights = {600, 700};
            for (int i = 0; i < 2; i++) {
                Font f = read(source, "mlum-pixel-" + pixelWeights[i] + ".ttf");
                PIXEL[i] = new UiFont(UiFont.PIXEL, pixelWeights[i], f, 7200.0F / 8160.0F, 1920.0F / 8160.0F, id++);
            }
            fallback = shapingOn(new Font(Font.DIALOG, Font.PLAIN, 1));
            ready = true;
            return true;
        } catch (Throwable t) {
            failure = t;
            ready = false;
            return false;
        }
    }

    private static Font read(Source source, String file) throws Exception {
        try (InputStream in = source.open(file)) {
            if (in == null) {
                throw new IOException("missing font " + file);
            }
            return shapingOn(Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(1.0F));
        }
    }

    private static Font shapingOn(Font font) {
        Map<TextAttribute, Object> attrs = new HashMap<>();
        attrs.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        attrs.put(TextAttribute.LIGATURES, TextAttribute.LIGATURES_ON);
        return font.deriveFont(attrs);
    }

    /** CSS weight matching against 400/600/700. */
    public static UiFont kufi(int weight) {
        if (weight <= 500) {
            return KUFI[0];
        }
        return weight < 700 ? KUFI[1] : KUFI[2];
    }

    /** CSS weight matching against 600/700: everything lighter than 600 resolves up to 600. */
    public static UiFont pixel(int weight) {
        return weight >= 700 ? PIXEL[1] : PIXEL[0];
    }

    static Font fallbackFont() {
        return fallback;
    }
}
