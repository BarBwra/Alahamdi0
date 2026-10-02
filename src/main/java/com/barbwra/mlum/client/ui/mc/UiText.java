package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.util.ArabicText;
import net.minecraft.ChatFormatting;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.text.Normalizer;

/**
 * Text on its way into the views, cleaned to what the UI's own text engine expects: plain logical
 * Unicode with no formatting codes.
 *
 * <p>The game hands over two kinds of Arabic. Most of it - item names, quest titles, faction names -
 * is ordinary logical text, and passes straight through. Some older strings were baked for the game
 * font: shaped into Presentation Forms and reversed into visual order. Drawing those through a real
 * shaping engine would show them backwards, so they are unbaked here - NFKC folds the presentation
 * forms back to letters and the visual order is undone.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UiText {

    private UiText() {
    }

    public static String logical(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String plain = ChatFormatting.stripFormatting(s);
        if (plain == null) {
            return "";
        }
        if (!hasPresentationForms(plain) || ArabicText.isLogical(plain)) {
            return plain;
        }
        return unreverse(Normalizer.normalize(plain, Normalizer.Form.NFKC));
    }

    static boolean hasPresentationForms(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'ﭐ' && c <= 'ﻼ') {
                return true;
            }
        }
        return false;
    }

    /** Reverses a visual-order line back to logical order, keeping runs of Latin and digits intact. */
    static String unreverse(String visual) {
        char[] out = new StringBuilder(visual).reverse().toString().toCharArray();
        int i = 0;
        while (i < out.length) {
            if (!ltr(out[i])) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < out.length && (ltr(out[j + 1]) || (inner(out[j + 1]) && j + 2 < out.length && ltr(out[j + 2])))) {
                j++;
            }
            for (int a = i, b = j; a < b; a++, b--) {
                char t = out[a];
                out[a] = out[b];
                out[b] = t;
            }
            i = j + 1;
        }
        return new String(out);
    }

    private static boolean ltr(char c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean inner(char c) {
        return c == ',' || c == '.' || c == ':' || c == '/' || c == '-' || c == '_' || c == ' ';
    }
}
