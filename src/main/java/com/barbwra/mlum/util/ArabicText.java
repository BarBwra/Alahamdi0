package com.barbwra.mlum.util;

/**
 * Runtime Arabic shaping and logical-to-visual reordering.
 *
 * <p><b>Why this exists.</b> Minecraft has no bidi or shaping engine. The mod's own lang files are
 * pre-baked into Arabic Presentation Forms-B, but quest text arrives live from a Skript command in
 * plain logical Arabic, so it has to be shaped at runtime or it renders as disconnected, backwards
 * letters.</p>
 *
 * <p><b>Two separate steps, and the order matters.</b></p>
 * <ol>
 *   <li>{@link #shape(String)} picks the isolated / initial / medial / final form of every letter
 *       from its neighbours and folds lam-alef pairs into their ligature. Output stays in
 *       <i>logical</i> order.</li>
 *   <li>{@link #toVisual(String)} runs the Unicode Bidirectional Algorithm over that logical
 *       string so it can be drawn left to right by a renderer that knows nothing about bidi -
 *       which is what keeps "57/100" reading the right way round inside an Arabic sentence.</li>
 * </ol>
 *
 * <p>For a single line use {@link #display(String)}, which does both. For wrapped text use
 * {@link #shape} first, wrap the <i>logical</i> string, then run {@link #toVisual} on each line -
 * see {@code QuestScreen}. Reversing before wrapping would put the last line first.</p>
 */
public final class ArabicText {

    private ArabicText() {
    }

    /* ------------------------------------------------------------------ tables */

    /**
     * One row per Arabic letter: {base, isolated, final, initial, medial}.
     * A letter with no initial/medial form (0) does not join to the letter that follows it -
     * alef, dal, thal, ra, zain, waw and friends - which is what produces the gaps in real Arabic.
     */
    private static final char[][] FORMS = {
            {'ء', 'ﺀ', 0, 0, 0},                               // hamza
            {'آ', 'ﺁ', 'ﺂ', 0, 0},                        // alef madda
            {'أ', 'ﺃ', 'ﺄ', 0, 0},                        // alef hamza above
            {'ؤ', 'ﺅ', 'ﺆ', 0, 0},                        // waw hamza
            {'إ', 'ﺇ', 'ﺈ', 0, 0},                        // alef hamza below
            {'ئ', 'ﺉ', 'ﺊ', 'ﺋ', 'ﺌ'},          // yeh hamza
            {'ا', 'ﺍ', 'ﺎ', 0, 0},                        // alef
            {'ب', 'ﺏ', 'ﺐ', 'ﺑ', 'ﺒ'},          // beh
            {'ة', 'ﺓ', 'ﺔ', 0, 0},                        // teh marbuta
            {'ت', 'ﺕ', 'ﺖ', 'ﺗ', 'ﺘ'},          // teh
            {'ث', 'ﺙ', 'ﺚ', 'ﺛ', 'ﺜ'},          // theh
            {'ج', 'ﺝ', 'ﺞ', 'ﺟ', 'ﺠ'},          // jeem
            {'ح', 'ﺡ', 'ﺢ', 'ﺣ', 'ﺤ'},          // hah
            {'خ', 'ﺥ', 'ﺦ', 'ﺧ', 'ﺨ'},          // khah
            {'د', 'ﺩ', 'ﺪ', 0, 0},                        // dal
            {'ذ', 'ﺫ', 'ﺬ', 0, 0},                        // thal
            {'ر', 'ﺭ', 'ﺮ', 0, 0},                        // reh
            {'ز', 'ﺯ', 'ﺰ', 0, 0},                        // zain
            {'س', 'ﺱ', 'ﺲ', 'ﺳ', 'ﺴ'},          // seen
            {'ش', 'ﺵ', 'ﺶ', 'ﺷ', 'ﺸ'},          // sheen
            {'ص', 'ﺹ', 'ﺺ', 'ﺻ', 'ﺼ'},          // sad
            {'ض', 'ﺽ', 'ﺾ', 'ﺿ', 'ﻀ'},          // dad
            {'ط', 'ﻁ', 'ﻂ', 'ﻃ', 'ﻄ'},          // tah
            {'ظ', 'ﻅ', 'ﻆ', 'ﻇ', 'ﻈ'},          // zah
            {'ع', 'ﻉ', 'ﻊ', 'ﻋ', 'ﻌ'},          // ain
            {'غ', 'ﻍ', 'ﻎ', 'ﻏ', 'ﻐ'},          // ghain
            {'ـ', 'ـ', 'ـ', 'ـ', 'ـ'},          // tatweel
            {'ف', 'ﻑ', 'ﻒ', 'ﻓ', 'ﻔ'},          // feh
            {'ق', 'ﻕ', 'ﻖ', 'ﻗ', 'ﻘ'},          // qaf
            {'ك', 'ﻙ', 'ﻚ', 'ﻛ', 'ﻜ'},          // kaf
            {'ل', 'ﻝ', 'ﻞ', 'ﻟ', 'ﻠ'},          // lam
            {'م', 'ﻡ', 'ﻢ', 'ﻣ', 'ﻤ'},          // meem
            {'ن', 'ﻥ', 'ﻦ', 'ﻧ', 'ﻨ'},          // noon
            {'ه', 'ﻩ', 'ﻪ', 'ﻫ', 'ﻬ'},          // heh
            {'و', 'ﻭ', 'ﻮ', 0, 0},                        // waw
            {'ى', 'ﻯ', 'ﻰ', 0, 0},                        // alef maksura
            {'ي', 'ﻱ', 'ﻲ', 'ﻳ', 'ﻴ'},          // yeh
    };

    /** Lam followed by one of these alef forms becomes a single ligature glyph. */
    private static final char[][] LAM_ALEF = {
            {'آ', 'ﻵ', 'ﻶ'},   // lam + alef madda
            {'أ', 'ﻷ', 'ﻸ'},   // lam + alef hamza above
            {'إ', 'ﻹ', 'ﻺ'},   // lam + alef hamza below
            {'ا', 'ﻻ', 'ﻼ'},   // lam + alef
    };

    private static final char LAM = 'ل';

    /* ------------------------------------------------------------------ shaping */

    private static int formIndex(char c) {
        for (int i = 0; i < FORMS.length; i++) {
            if (FORMS[i][0] == c) {
                return i;
            }
        }
        return -1;
    }

    /** Diacritics are transparent: they never break a join between the letters around them. */
    private static boolean isMark(char c) {
        return (c >= 'ً' && c <= 'ٟ') || c == 'ٰ' || (c >= 'ۖ' && c <= 'ۭ');
    }

    /** True when this letter can connect to the letter after it. */
    private static boolean joinsForward(char c) {
        int i = formIndex(c);
        return i >= 0 && FORMS[i][3] != 0;
    }

    /** True when this letter can connect to the letter before it. */
    private static boolean joinsBackward(char c) {
        int i = formIndex(c);
        return i >= 0 && FORMS[i][2] != 0;
    }

    /**
     * Replaces every Arabic letter with its contextual presentation form.
     * Output stays in logical order - reverse it with {@link #toVisual} only at draw time.
     */
    public static String shape(String input) {
        if (input == null || input.isEmpty() || !containsArabic(input)) {
            return input == null ? "" : input;
        }

        char[] src = input.toCharArray();
        StringBuilder out = new StringBuilder(src.length);

        for (int i = 0; i < src.length; i++) {
            char c = src[i];

            // lam + alef collapses into one glyph, so consume both
            if (c == LAM && i + 1 < src.length) {
                int lig = ligatureIndex(src[i + 1]);
                if (lig >= 0) {
                    boolean connectsBack = hasJoiningPrev(src, i);
                    out.append(connectsBack ? LAM_ALEF[lig][2] : LAM_ALEF[lig][1]);
                    i++;
                    continue;
                }
            }

            int idx = formIndex(c);
            if (idx < 0) {
                out.append(c);
                continue;
            }

            boolean prev = hasJoiningPrev(src, i);
            boolean next = hasJoiningNext(src, i);

            char shaped;
            if (prev && next && FORMS[idx][4] != 0) {
                shaped = FORMS[idx][4];                     // medial
            } else if (prev && FORMS[idx][2] != 0) {
                shaped = FORMS[idx][2];                     // final
            } else if (next && FORMS[idx][3] != 0) {
                shaped = FORMS[idx][3];                     // initial
            } else {
                shaped = FORMS[idx][1];                     // isolated
            }
            out.append(shaped);
        }
        return out.toString();
    }

    private static int ligatureIndex(char alef) {
        for (int i = 0; i < LAM_ALEF.length; i++) {
            if (LAM_ALEF[i][0] == alef) {
                return i;
            }
        }
        return -1;
    }

    /** Looks back past diacritics for a letter that joins forward. */
    private static boolean hasJoiningPrev(char[] src, int i) {
        for (int j = i - 1; j >= 0; j--) {
            if (isMark(src[j])) {
                continue;
            }
            return joinsForward(src[j]);
        }
        return false;
    }

    /** Looks ahead past diacritics for a letter that joins backward. */
    private static boolean hasJoiningNext(char[] src, int i) {
        // a letter that cannot join forward never connects to what follows
        if (!joinsForward(src[i])) {
            return false;
        }
        for (int j = i + 1; j < src.length; j++) {
            if (isMark(src[j])) {
                continue;
            }
            return joinsBackward(src[j]);
        }
        return false;
    }

    /* -------------------------------------------------------- logical -> visual */

    /*
     * Bidi character classes. The numbers deliberately match Character.DIRECTIONALITY_*, because the
     * JDK already ships the Unicode bidi table and a hand-copied version of it would only go stale.
     */
    private static final byte L = 0, R = 1, AL = 2, EN = 3, ES = 4, ET = 5, AN = 6, CS = 7,
            NSM = 8, BN = 9, B = 10, S = 11, WS = 12, ON = 13;

    private static byte bidiClass(int cp) {
        byte d = Character.getDirectionality(cp);
        if (d >= 14) {
            return BN;      // explicit embedding and isolate controls; rule X9 keeps them out of the way
        }
        return d < 0 ? ON : d;  // unassigned: neutral is the safe guess in the middle of a line
    }

    private static boolean isNeutral(byte type) {
        return type == ON || type == WS || type == B || type == S;
    }

    /** N1 lets numbers push neutrals around exactly as if they were right-to-left letters. */
    private static byte strongSide(byte type) {
        return type == L ? L : R;
    }

    /**
     * Reorders one shaped, logical-order line into the visual order the font should draw.
     *
     * <p>This is the Unicode Bidirectional Algorithm (UAX #9) cut down to what quest text needs:
     * the weak rules W1-W7, the neutral rules N1-N2, the implicit rules I1-I2 and then L1, L2 and
     * L4. There are no explicit embedding controls in this data, so only levels 0, 1 and 2 occur
     * and the whole line is a single level run.</p>
     *
     * <p>The rules that matter here are the neutral ones. Punctuation has no direction of its own:
     * a colon between two Arabic words, a full stop ending an Arabic sentence and the brackets
     * around "(57/100)" all belong to the Arabic around them, and only the digits inside actually
     * run the other way. Treating that punctuation as part of a Latin island - the shortcut this
     * method used to take - is what pushed it to the wrong end of the line.</p>
     */
    public static String toVisual(String shaped) {
        if (shaped == null || shaped.isEmpty()) {
            return shaped == null ? "" : shaped;
        }

        int n = shaped.codePointCount(0, shaped.length());
        int[] cp = new int[n];
        byte[] orig = new byte[n];
        for (int i = 0, k = 0; k < n; k++) {
            cp[k] = shaped.codePointAt(i);
            orig[k] = bidiClass(cp[k]);
            i += Character.charCount(cp[k]);
        }

        /*
         * P2/P3 would take the direction of the first strong character. The mod widens that on
         * purpose: any Arabic at all makes the line right-to-left, which is the same test
         * QuestScreen uses to right-align it, so a title like "AK-47 مطلوب" cannot be laid out
         * against the edge it is not aligned to.
         */
        byte para = 0;
        boolean arabicNumber = false;
        for (byte type : orig) {
            if (type == R || type == AL) {
                para = 1;
            } else if (type == AN) {
                arabicNumber = true;
            }
        }
        /*
         * With no Arabic letters and no Arabic-Indic digits every character ends up at level 0, so
         * there is nothing for L2 to reverse and plain English is handed back untouched. The digits
         * have to be part of that test: rule N1 lets a number stand in for a letter, so "٥-٣" still
         * reorders even though it holds nothing strong.
         */
        if (para == 0 && !arabicNumber) {
            return shaped;
        }

        /*
         * X9: formatting controls take no part in resolution, so they sit out the rules below and
         * later ride along at the level of the character in front of them. Unlike a browser we keep
         * them in the output - dropping characters from somebody's quest text would be worse than
         * placing a zero-width one imperfectly.
         */
        int[] pos = new int[n];
        byte[] t = new byte[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (orig[i] != BN) {
                pos[m] = i;
                t[m] = orig[i];
                m++;
            }
        }

        // one run at the paragraph level, so both of its boundaries count as paragraph-direction text
        final byte sos = para == 0 ? L : R;

        byte prevType = sos;
        byte prevStrong = sos;
        for (int i = 0; i < m; i++) {
            if (t[i] == NSM) {
                t[i] = prevType;                            // W1: a mark inherits its base letter
            }
            if (t[i] == EN && prevStrong == AL) {
                t[i] = AN;                                  // W2
            }
            if (t[i] == L || t[i] == R || t[i] == AL) {
                prevStrong = t[i];
            }
            prevType = t[i];
        }

        for (int i = 0; i < m; i++) {
            if (t[i] == AL) {
                t[i] = R;                                   // W3
            }
        }

        // W4: a separator only stays a separator while it is holding two numbers together
        for (int i = 1; i < m - 1; i++) {
            if (t[i] == ES && t[i - 1] == EN && t[i + 1] == EN) {
                t[i] = EN;
            } else if (t[i] == CS && t[i - 1] == t[i + 1] && (t[i - 1] == EN || t[i - 1] == AN)) {
                t[i] = t[i - 1];
            }
        }

        // W5: terminators such as % or $ join the number they are attached to
        for (int i = 0; i < m; i++) {
            if (t[i] != EN) {
                continue;
            }
            for (int j = i - 1; j >= 0 && t[j] == ET; j--) {
                t[j] = EN;
            }
            for (int j = i + 1; j < m && t[j] == ET; j++) {
                t[j] = EN;
            }
        }

        for (int i = 0; i < m; i++) {
            if (t[i] == ET || t[i] == ES || t[i] == CS) {
                t[i] = ON;                                  // W6: the rest are just punctuation
            }
        }

        // W7: digits that belong to English text are English, so "AK-47" does not split apart
        prevStrong = sos;
        for (int i = 0; i < m; i++) {
            if (t[i] == EN && prevStrong == L) {
                t[i] = L;
            }
            if (t[i] == L || t[i] == R) {
                prevStrong = t[i];
            }
        }

        // N1/N2: a run of neutrals takes the direction both its sides agree on, else the paragraph's
        for (int i = 0; i < m; i++) {
            if (!isNeutral(t[i])) {
                continue;
            }
            int end = i;
            while (end + 1 < m && isNeutral(t[end + 1])) {
                end++;
            }
            byte before = i == 0 ? sos : strongSide(t[i - 1]);
            byte after = end == m - 1 ? sos : strongSide(t[end + 1]);
            byte resolved = before == after ? before : ((para & 1) == 1 ? R : L);
            for (int j = i; j <= end; j++) {
                t[j] = resolved;
            }
            i = end;
        }

        // I1/I2: opposite-direction text goes up one level, numbers inside Arabic go up two
        byte[] level = new byte[n];
        for (int i = 0; i < m; i++) {
            byte lv = para;
            if ((para & 1) == 0) {
                if (t[i] == R) {
                    lv++;
                } else if (t[i] != L) {
                    lv += 2;
                }
            } else if (t[i] != R) {
                lv++;
            }
            level[pos[i]] = lv;
        }
        for (int i = 0, carried = para; i < n; i++) {
            if (orig[i] == BN) {
                level[i] = (byte) carried;
            } else {
                carried = level[i];
            }
        }

        /*
         * L1. A tab or a trailing space belongs to the line rather than to the word before it,
         * otherwise a line that ends in Arabic drags its final space over to the far margin and the
         * text no longer lines up with the one above it.
         */
        boolean atLineEnd = true;
        for (int i = n - 1; i >= 0; i--) {
            if (orig[i] == B || orig[i] == S) {
                level[i] = para;
                atLineEnd = true;
            } else if (atLineEnd && (orig[i] == WS || orig[i] == BN)) {
                level[i] = para;
            } else {
                atLineEnd = false;
            }
        }

        // L4, applied while the levels still line up with the original positions
        for (int i = 0; i < n; i++) {
            if ((level[i] & 1) == 1) {
                cp[i] = mirror(cp[i]);
            }
        }

        // L2: reverse every run of level-or-higher characters, highest level first
        int highest = 0;
        int lowestOdd = Integer.MAX_VALUE;
        for (byte lv : level) {
            if (lv > highest) {
                highest = lv;
            }
            if ((lv & 1) == 1 && lv < lowestOdd) {
                lowestOdd = lv;
            }
        }
        for (int lv = highest; lv >= lowestOdd; lv--) {
            int start = -1;
            for (int i = 0; i <= n; i++) {
                if (i < n && level[i] >= lv) {
                    if (start < 0) {
                        start = i;
                    }
                } else if (start >= 0) {
                    reverse(cp, start, i - 1);
                    start = -1;
                }
            }
        }

        StringBuilder out = new StringBuilder(shaped.length());
        for (int c : cp) {
            out.appendCodePoint(c);
        }
        return out.toString();
    }

    private static void reverse(int[] a, int from, int to) {
        for (int i = from, j = to; i < j; i++, j--) {
            int tmp = a[i];
            a[i] = a[j];
            a[j] = tmp;
        }
    }

    /** Brackets and quotes point the other way once the run around them is reversed. */
    private static int mirror(int c) {
        return switch (c) {
            case '(' -> ')';
            case ')' -> '(';
            case '[' -> ']';
            case ']' -> '[';
            case '{' -> '}';
            case '}' -> '{';
            case '<' -> '>';
            case '>' -> '<';
            case '«' -> '»';
            case '»' -> '«';
            case '‹' -> '›';
            case '›' -> '‹';
            default -> c;
        };
    }

    /* -------------------------------------------------------------- normalising */

    /**
     * Strips the حركات and folds أ آ إ down to a plain ا.
     *
     * <p><b>Why this is a display change and not a data change.</b> Nothing on disk is rewritten -
     * a faction is still named with whatever the player typed, and a search still matches what they
     * stored. This runs on the way to the screen, so the game's own text and another mod's item
     * names are treated identically without either being edited.</p>
     *
     * <p><b>Both encodings are handled.</b> Raw text carries the marks in the base Arabic block
     * (U+064B..U+0652 and friends) and the hamza-carrying alefs at U+0622/0623/0625. The mod's lang
     * files are baked into Presentation Forms-B, where those same letters are different codepoints
     * again - and the ل+أ ligatures are a third set. Folding only the base block would leave every
     * pre-baked string untouched, which is most of the UI. Both ranges are mapped here.</p>
     *
     * <p>Idempotent: running it over an already folded string changes nothing, which is what lets it
     * sit at the top of every entry point without anyone tracking whether it has run.</p>
     */
    public static String fold(String input) {
        if (input == null || input.isEmpty()) {
            return input == null ? "" : input;
        }

        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            // the حركات - isMark already spans U+064B..U+065F, U+0670 and U+06D6..U+06ED
            if (isMark(c)) {
                continue;
            }

            /*
             * The presentation forms below look identical to their base-block twins in an editor,
             * so this switch is verified by the standalone harness rather than by reading it - see
             * the class note on compiling this file on its own.
             */
            switch (c) {
                // base block: madda, hamza above, hamza below -> bare alef
                case 'آ', 'أ', 'إ' -> out.append('ا');
                // presentation forms, isolated -> isolated alef
                case 'ﺁ', 'ﺃ', 'ﺇ' -> out.append('ﺍ');
                // presentation forms, final -> final alef
                case 'ﺂ', 'ﺄ', 'ﺈ' -> out.append('ﺎ');
                // lam-alef ligatures, isolated -> plain lam-alef
                case 'ﻵ', 'ﻷ', 'ﻹ' -> out.append('ﻻ');
                // lam-alef ligatures, final
                case 'ﻶ', 'ﻸ', 'ﻺ' -> out.append('ﻼ');
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /* ---------------------------------------------------------------- shortcuts */

    /** Shape and reorder in one go. Correct for a single line only. */
    public static String display(String input) {
        return toVisual(shape(fold(input)));
    }

    /**
     * True when the string still holds <b>unshaped</b> Arabic, i.e. it has not been pre-baked.
     *
     * <p>The mod's own lang files are baked at build time into Presentation Forms-B and reversed
     * into visual order, so running {@link #display} over one again would reverse it a second time
     * and hand back nonsense. Text arriving at runtime - a quest title, an item name from another
     * mod - is raw logical Arabic and does need it. This is how the two are told apart: only raw
     * text still contains characters from the base Arabic block.</p>
     */
    public static boolean isLogical(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '؀' && c <= 'ۿ') {
                return true;
            }
        }
        return false;
    }

    /**
     * Shapes and reorders only what needs it. Safe to call on anything, baked or not.
     *
     * <p>The fold runs on both branches. A pre-baked lang string never reaches {@link #display}, so
     * folding inside that method alone would leave every translated label still carrying its marks -
     * and those are the strings most of the UI is built from.</p>
     */
    public static String autoDisplay(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return isLogical(input) ? display(input) : fold(input);
    }

    public static boolean containsArabic(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= '؀' && c <= 'ۿ') || (c >= 'ﭐ' && c <= '﻿')) {
                return true;
            }
        }
        return false;
    }
}
