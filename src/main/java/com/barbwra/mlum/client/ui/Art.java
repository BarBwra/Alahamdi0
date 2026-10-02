package com.barbwra.mlum.client.ui;

import com.barbwra.mlum.client.ui.text.Raster;

import java.util.HashMap;
import java.util.Map;

/**
 * The design's pixel art, copied cell for cell from the HTML: the palette, the item sprites
 * (the desktop preview draws items with these; the game draws real items instead), the one-colour
 * icons and the ghost silhouettes of empty sockets. Rasters are built once, at 1x, and scaled
 * nearest-neighbour - {@code image-rendering:pixelated}.
 */
public final class Art {

    private Art() {
    }

    private static final Map<Character, Integer> PAL = new HashMap<>();

    static {
        PAL.put('k', 0xFF151713);
        PAL.put('e', 0xFF3D4239);
        PAL.put('g', 0xFF6B7066);
        PAL.put('G', 0xFF9EA397);
        PAL.put('l', 0xFFC8C8B9);
        PAL.put('w', 0xFFEFE9D8);
        PAL.put('r', 0xFFB3362C);
        PAL.put('R', 0xFFDE5F4B);
        PAL.put('b', 0xFF6A4226);
        PAL.put('B', 0xFFA36B3F);
        PAL.put('y', 0xFFE3A236);
        PAL.put('Y', 0xFFFFD27C);
        PAL.put('o', 0xFFA0601C);
        PAL.put('O', 0xFF6E3D0E);
        PAL.put('W', 0xFFFFF8E6);
        PAL.put('n', 0xFF4D8636);
        PAL.put('N', 0xFF84BB55);
        PAL.put('c', 0xFF3B6CA3);
        PAL.put('C', 0xFF78ACDF);
        PAL.put('s', 0xFFD6B28A);
        PAL.put('m', 0xFF7A2632);
        PAL.put('M', 0xFFAD4A57);
        PAL.put('T', 0xFFC7B287);
        PAL.put('t', 0xFF9F8B64);
        PAL.put('h', 0xFF573120);
        PAL.put('H', 0xFF6C3F27);
        PAL.put('a', 0xFFC9987B);
        PAL.put('A', 0xFFB0816A);
        PAL.put('E', 0xFFEFE9E0);
        PAL.put('i', 0xFF3B7A4B);
        PAL.put('q', 0xFF8A5646);
        PAL.put('j', 0xFF8B2A2A);
        PAL.put('J', 0xFFA53737);
        PAL.put('x', 0xFF2A2332);
        PAL.put('X', 0xFF3A3044);
        PAL.put('d', 0xFF88A3B7);
        PAL.put('D', 0xFF6D889C);
        PAL.put('f', 0xFF4A3426);
        PAL.put('F', 0xFF36261B);
    }

    /** Coloured sprites, one palette letter per pixel, '.' transparent. */
    public static final Map<String, String[]> SPR = new HashMap<>();
    static {
        SPR.put("flesh", new String[]{"....mmmm....", "..mmMMMMmm..", ".mMMMMMMMMm.", ".mMMNMMMMMm.", "mMMMMMMMNMMm", "mMMMMMMMMMMm", "mMNMMMMMMMmm", ".mMMMMMNMMm.", ".mmMMMMMMmm.", "...mmmmmm..."});
        SPR.put("money", new String[]{"............", ".nnnnnnnnnn.", ".nNNNNNNNNn.", ".nNnNNNNnNn.", ".nNNNyyNNNn.", ".nNNyNNyNNn.", ".nNNNyyNNNn.", ".nNnNNNNnNn.", ".nNNNNNNNNn.", ".nnnnnnnnnn.", "..kkkkkkkkkk"});
        SPR.put("coin", new String[]{"....OOOO....", "..OOooooOO..", ".OoYYYYYYoO.", ".OYYWWYYyYO.", "OoYWYYYYYyoO", "OoYWYYYYyYoO", "OoYYYYYYyYoO", "OoYYYYYyyYoO", ".OYyYYyyYyO.", ".OoyyyyyyoO.", "..OOooooOO..", "....OOOO...."});
        SPR.put("spark", new String[]{"..W..", "..N..", "WNWNW", "..N..", "..W.."});
        SPR.put("bill", new String[]{"............", ".nnnnnnnnnn.", ".nNNNNNNNNn.", ".nNiNNNNiNn.", ".nNNNiiNNNn.", ".nNNiNNiNNn.", ".nNNNiiNNNn.", ".nNiNNNNiNn.", ".nNNNNNNNNn.", ".nnnnnnnnnn.", "..kkkkkkkkkk"});
        SPR.put("cash", new String[]{"nnnnnnnnnn", "nNNNNNNNNn", "nNNNiiNNNn", "nNNiNNiNNn", "nNNNiiNNNn", "nNNNNNNNNn", "nnnnnnnnnn"});
        SPR.put("medkit", new String[]{"....kkkk....", "....k..k....", ".kkkkkkkkkk.", ".kwwwwwwwwk.", ".kwwwrrwwwk.", ".kwwwrrwwwk.", ".kwrrrrrrwk.", ".kwrrrrrrwk.", ".kwwwrrwwwk.", ".kwwwrrwwwk.", ".kwwwwwwwwk.", ".kkkkkkkkkk."});
        SPR.put("bandage", new String[]{"...kkkkk....", "..kwwwwwk...", ".kwwsssswk..", ".kwskkkswk..", ".kwsk.kswk..", ".kwskkkswk..", ".kwwsssswk..", "..kwwwwwwkkk", "...kkkkkwwwk", "........kkkk"});
        SPR.put("can", new String[]{"...kkkkkk...", "..kGllllGk..", "..kGGGGGGk..", "..kcCCCCck..", "..kyyyyyyk..", "..kyRRRyyk..", "..kyRRRyyk..", "..kyyyyyyk..", "..kcCCCCck..", "..kGGGGGGk..", "...kkkkkk..."});
        SPR.put("water", new String[]{"....kkk....", "....kCk....", "...kkkkk...", "..kCCCCCk..", "..kCwCCCk..", "..kwwwwwk..", "..kCwCCCk..", "..kCCCCck..", "..kCCCCck..", "..kccccck..", "...kkkkk..."});
        SPR.put("apple", new String[]{".....kk.....", "....kn......", "..kkkkkk....", ".kyyyyYYk...", "kyyyyyyYYk..", "kyyyyyyyYk..", "kyyyyyyyyk..", "koyyyyyyyk..", ".koyyyyyk...", "..kkkkkk...."});
        SPR.put("scrap", new String[]{"............", ".....kkkk...", "....kGGGgk..", "..kkGlllGgk.", ".kGGgGGGggk.", ".kGlGgggkk..", "kGllGgkk....", "kGGGgk..kkk.", ".kggk..kGgk.", "..kk...kggk.", "........kk.."});
        SPR.put("circuit", new String[]{"kkkkkkkkkk", "knnnnnnnnk", "knykkNnnnk", "knnnnkNnyk", "knkkknnnnk", "knnnykkkNk", "knNnnnnnnk", "kkkkkkkkkk", ".y.y..y.y."});
        SPR.put("ammo", new String[]{"..y.y.y.y...", "..Y.Y.Y.Y...", "..y.y.y.y...", "..o.o.o.o...", ".kkkkkkkkkk.", ".knnnnnnnnk.", ".knNNNNNNnk.", ".knNkkkkNnk.", ".knNNNNNNnk.", ".knnnnnnnnk.", ".kkkkkkkkkk."});
        SPR.put("pistol", new String[]{"............", ".kkkkkkkkkk.", ".kgggggggggk", ".kGGGGGGGGgk", ".kkkkkggkkkk", ".....kgk....", "....kbbk....", "....kbBk....", "...kbbk.....", "...kbbk.....", "...kkkk....."});
        SPR.put("scope", new String[]{"............", "...kk...kk..", "..kkkkkkkkk.", ".kgggggggggk", "kCgGGGGGGgCk", ".kgggggggggk", "..kkkkkkkkk.", "....kk.kk..."});
        SPR.put("grip", new String[]{".kkkkkkkk.", ".kggggggk.", "..kgGggk..", "..kgGgk...", "..kgGgk...", "..kgGgk...", "..kgggk...", "..kkkkk..."});
        SPR.put("mag", new String[]{"..kkkkk...", "..kyYyk...", "..kgggk...", "..kgGgk...", "...kgGgk..", "...kgGgk..", "....kgGgk.", "....kgggk.", "....kkkkk."});
        SPR.put("muzzle", new String[]{"..........", ".kkkkkkkkk", ".kgggggggk", ".kGGGGGGGk", ".kgggggggk", ".kkkkkkkkk"});
        SPR.put("stock", new String[]{"kkkkkk.....", "kgggggkkkk.", "kgGGGGgggk.", "kggggggggkk", ".kkkgggggGk", "....kkkgggk", ".......kkkk"});
        SPR.put("laser", new String[]{"..........", "..kkkkk...", "..kgggkRRR", "..kGGGk...", "..kkkkk..."});
        SPR.put("helmet", new String[]{"....kkkk....", "..kknnnnkk..", ".knnNNNNnnk.", ".knNNNNNNnk.", "knnnnnnnnnnk", "knnnnnnnnnnk", "kkkkkkkkkkkk", ".k........k."});
        SPR.put("vest", new String[]{"..kk....kk..", "..knk..knk..", ".kknnkknnkk.", ".knnNnnNnnk.", ".knnnnnnnnk.", ".knkkkkkknk.", ".knkyyyyknk.", ".knkkkkkknk.", ".knnnnnnnnk.", ".kkkkkkkkkk."});
        SPR.put("pants", new String[]{".kkkkkkkkkk.", ".kccccccCck.", ".kccccccCck.", ".kcccckcCck.", ".kccck.kcck.", ".kccck.kcck.", ".kccck.kcck.", ".kccck.kcck.", ".kkkkk.kkkk."});
        SPR.put("boots", new String[]{"..kkk..kkk..", "..kbk..kbk..", "..kbk..kbk..", "..kbk..kbk..", ".kbbk.kbbk..", "kbbBkkbbBk..", "kkkkkkkkkk.."});
        SPR.put("shield", new String[]{"kkkkkkkkkk", "kGGGGGGGGk", "kGbbbbbbGk", "kGbBBBBbGk", "kGbBBBBbGk", "kGbbBBbbGk", ".kGbbbbGk.", "..kGbbGk..", "...kGGk...", "....kk...."});
        SPR.put("key", new String[]{".kkkk.......", "kyYYyk......", "kYk.ykkkkkkk", "kyYYykyyyyyk", ".kkkk.kykyk.", "......k.k.k."});
        SPR.put("crate", new String[]{"kkkkkkkkkkkk", "kbbbbbbbbbbk", "kBkBBBBBBkBk", "kbbkBBBBkbbk", "kBBBkBBkBBBk", "kbbBBkkBBbbk", "kBBBkBBkBBBk", "kbbkBBBBkbbk", "kBkBBBBBBkBk", "kbbbbbbbbbbk", "kkkkkkkkkkkk"});
        SPR.put("fuel", new String[]{".....kkk....", "....kkrk....", "..kkkkkkkk..", ".krrrrrrrrk.", ".krRRRRRRrk.", ".krRkkkkRrk.", ".krRRRRRRrk.", ".krRkkkkRrk.", ".krrrrrrrrk.", ".kkkkkkkkkk."});
        SPR.put("machete", new String[]{".........kk.", "........kGlk", ".......kGlk.", "......kGlk..", ".....kGlk...", "....kGlk....", "...kGlk.....", "..kbkk......", ".kbBk.......", "kbBk........", "kkk........."});
        SPR.put("bat", new String[]{"..........kk", ".........kBk", "........kBBk", ".......kBBk.", "......kBBk..", ".....kBBk...", "....kBbk....", "...kbbk.....", "..kbk.......", ".kbk........", "kkk........."});
        SPR.put("rifle", new String[]{".........kkkkk...........k........", ".........kgggk..........kgk.......", "....kkkkkkkkkkkkkkkkkkkkkkkk......", "kkkkgGGGGGGGGGGGGkeeeeeeeeekkkkkkk", "kggggggggggggggggkeGeGeGeGekggggGk", "kgggkkkgggggggggkkeeeeeeeeekkkkkkk", "kgggk.kkkkkggggkkkkkkkkkkkk.......", "kkkkk.kgggk.kgggk.................", "......kgggk..kgggk................", ".....kgggk....kgggk...............", ".....kkkkk....kkkkk..............."});
        SPR.put("tank", new String[]{"...............k.........................", "...............k.........................", "............kkkkkkkkk....................", "..........kkTTTTTTTTTkk..................", ".........kTTTTTTTTTTTTTkkkkkkkkkkkkkkkkkkk", ".........kTTtTTtTTTTTTTkggggggggggggggggGk", ".........kttttttttttttttkkkkkkkkkkkkkkkkkk", "....kkkkkkkkkkkkkkkkkkkkkkkkkk...........", "..kkTTTTTTTTTTTTTTTTTTTTTTTTTTkk.........", ".kTTTTTTTTTTTTTTTTTTTTTTTTTTTTTTk........", ".kttttttttttttttttttttttttttttttk........", "keeeeeeeeeeeeeeeeeeeeeeeeeeeeeeek.......", "kegGgegGgegGgegGgegGgegGgegGgegek.......", "keeeeeeeeeeeeeeeeeeeeeeeeeeeeeeek.......", ".kkkkkkkkkkkkkkkkkkkkkkkkkkkkkkk........"});
        SPR.put("pickup", new String[]{"......kkkkkk............", ".....kCCkCCCk...........", "....kCCCkCCCCkkkkkkkkkk.", "..kkrrrrrrrrrrrrrrrrrrrk", ".krrrrrrrrrrrrrrrrrrrrrk", ".kRRRRRRRRRRRRRRRRRRRRRk", ".kkkkeeekkkkkkkkkkeeekkk", "....keGek........keGek..", ".....kkk..........kkk..."});
        SPR.put("bike", new String[]{"..........kk.....", ".....kkkkkkk.....", "...kkrrrrrk......", "..kgggrrrgggkk...", ".kekk.kgk..kkek..", "kegGek...keGgek..", "keGgek...kegGek..", ".kekk.....kkek..."});
        SPR.put("horse", new String[]{"..............kk..", "............kkBkk.", "...........kBBBBk.", "..........kBBkBkk.", "kk.......kBBBk....", "kBkkkkkkkBBBk.....", ".kBBBBBBBBBBk.....", ".kBBBBBBBBBk......", ".kBkkkkkkBk.......", ".kBk....kBk.......", ".kkk....kkk......."});
        SPR.put("bp_leather", new String[]{"....kkkk....", "...k....k...", "..kkkkkkkk..", ".kBBBBBBBBk.", ".kBbbbbbbBk.", ".kBbkkkkbBk.", ".kBbkyykbBk.", ".kBbkkkkbBk.", ".kBbbbbbbBk.", ".kkkkkkkkkk.", ".kBbbbbbbBk.", ".kBBBBBBBBk.", "..kkkkkkkk.."});
        SPR.put("bp_small", new String[]{"....kkkk....", "...k....k...", "..kkkkkkkk..", ".kggggggggk.", ".kgeeeeeegk.", ".kgekkkkegk.", ".kgekGGkegk.", ".kgekkkkegk.", ".kgeeeeeegk.", ".kkkkkkkkkk.", ".kgeeeeeegk.", ".kggggggggk.", "..kkkkkkkk.."});
        SPR.put("bp_hiking", new String[]{"....kkkk....", "...k....k...", "..kkkkkkkk..", ".kggggggggk.", ".kgeeeeeegk.", ".kgekkkkegk.", ".kgekRRkegk.", ".kgekkkkegk.", ".kgeeeeeegk.", ".kkkkkkkkkk.", ".kgeeeeeegk.", ".kggggggggk.", "..kkkkkkkk.."});
        SPR.put("bp_mil", new String[]{"....kkkk....", "...k....k...", "..kkkkkkkk..", ".keeeeeeeek.", ".keggggggek.", ".kegkkkkgek.", ".kegknnkgek.", ".kegkkkkgek.", ".keggggggek.", ".kkkkkkkkkk.", ".keggggggek.", ".keeeeeeeek.", "..kkkkkkkk.."});
        SPR.put("bp_desert", new String[]{"....kkkk....", "...k....k...", "..kkkkkkkk..", ".kTTTTTTTTk.", ".kTttttttTk.", ".kTtkkkktTk.", ".kTtkooktTk.", ".kTtkkkktTk.", ".kTttttttTk.", ".kkkkkkkkkk.", ".kTttttttTk.", ".kTTTTTTTTk.", "..kkkkkkkk.."});
    }

    /** One-colour icons, '#' set, drawn in the current colour. */
    public static final Map<String, String[]> MONO = new HashMap<>();
    static {
        MONO.put("bag", new String[]{"..####..", ".#....#.", "########", "#......#", "#.####.#", "#.#..#.#", "#.####.#", "########"});
        MONO.put("quest", new String[]{"########", "#......#", "#.####.#", "#......#", "#.####.#", "#......#", "#.##...#", "########"});
        MONO.put("rank", new String[]{"...##...", "..####..", ".##..##.", "#......#", "...##...", "..####..", ".##..##.", "#......#"});
        MONO.put("bolt", new String[]{"....###.", "...###..", "..###...", ".######.", "...###..", "..###...", ".###....", ".##....."});
        MONO.put("car", new String[]{"........", "..####..", ".#.##.#.", "########", "########", "##.##.##", ".##..##.", "........"});
        MONO.put("flag", new String[]{"#.......", "#######.", "########", "#######.", "######..", "#.......", "#.......", "#......."});
        MONO.put("pin", new String[]{"..####..", ".##..##.", ".#....#.", ".##..##.", "..####..", "..####..", "...##...", "...##..."});
        MONO.put("check", new String[]{".......#", "......##", ".....##.", "#...##..", "##.##...", ".###....", "..#.....", "........"});
        MONO.put("lock", new String[]{"..####..", ".#....#.", ".#....#.", "########", "###..###", "###..###", "########", "........"});
        MONO.put("warn", new String[]{"...##...", "...##...", "..####..", "..#..#..", ".##..##.", ".######.", "###..###", "########"});
        MONO.put("sort", new String[]{"######", "......", "####..", "......", "##....", "......"});
        MONO.put("steps", new String[]{".##......", "####.....", "####..##.", ".##..####", ".....####", ".##...##.", ".##......", "........", "........."});
        MONO.put("eye", new String[]{"..#####..", ".##...##.", "##..#..##", "#..###..#", "##..#..##", ".##...##.", "..#####..", "........."});
        MONO.put("cleaver", new String[]{"#######..", "#######..", "#######..", "######...", ".....##..", ".....##..", ".....##..", ".....##.."});
        MONO.put("blade", new String[]{"......###", ".....###.", "....###..", "...###...", "#.###....", ".###.....", ".##......", "#..#....."});
        MONO.put("cross", new String[]{"...###...", "...###...", "#########", "#########", "#########", "...###...", "...###...", "........."});
        MONO.put("run", new String[]{"......#..", "......##.", "#########", "......##.", "......#..", ".#####...", ".#...#...", ".#####..."});
        MONO.put("coins", new String[]{"..####...", ".#....#..", ".#.##.#..", ".#....#..", "..####...", "....####.", "...#....#", "...#....#", "....####."});
        MONO.put("heart", new String[]{".##...##.", "####.####", "#########", "#########", ".#######.", "..#####..", "...###...", "....#...."});
        MONO.put("snd", new String[]{"...#.....", "..##..#..", "###.#..#.", "###.#..#.", "###.#..#.", "..##..#..", "...#.....", "........."});
        MONO.put("mute", new String[]{"...#.....", "..##.....", "###.#.#.#", "###.#..#.", "###.#.#.#", "..##.....", "...#.....", "........."});
    }

    /** The placeholder survivor the preview stands in for the player model. */
    public static final String[] CHAR = {"....hhhhhhhh....", "....hHhhHhhH....", "....hahhhhah....", "....aaaaaaaa....", "....aEiaaiEa....", "....aaaaaaaa....", "....aaaqqaaa....", "....AaaaaaaA....", "jjjjjjxxxxjjjjjj", "jJjjjjxXxxjjjjJj", "jjjjjJxxxxJjjjjj", "jJjjjjxxXxjjjjJj", "jjjjjjxxxxjjjjjj", "jJjjjJxXxxJjjjJj", "jjjjjjxxxxjjjjjj", "jjjjjjxxxxjjjjjj", "xxxxjjxxxxjjxxxx", "aaaajjxxxxjjaaaa", "aaaajjxxxxjjaaaa", "AaAajjjjjjjjAaAa", "....ddddDddd....", "....dDddDdDd....", "....ddddDddd....", "....dddDDddd....", "....dDddDdDd....", "....ddddDddd....", "....dddDDddd....", "....ddddDdDd....", "....dDddDddd....", "....ffffffff....", "....ffffffff....", "....FfffFfff...."};


    private static final Map<String, Raster> CACHE = new HashMap<>();

    public static int width(String[] rows) {
        int w = 0;
        for (String r : rows) {
            w = Math.max(w, r.length());
        }
        return w;
    }

    public static int[] dims(String name) {
        String[] rows = SPR.get(name);
        return rows == null ? new int[]{1, 1} : new int[]{width(rows), rows.length};
    }

    /** A coloured sprite at 1x. */
    public static Raster sprite(String name) {
        return CACHE.computeIfAbsent("s:" + name, k -> build(SPR.get(name), false, false));
    }

    public static Raster character() {
        return CACHE.computeIfAbsent("char", k -> build(CHAR, false, false));
    }

    /** A one-colour icon at 1x, white, to be tinted. */
    public static Raster mono(String name) {
        return CACHE.computeIfAbsent("m:" + name, k -> build(MONO.get(name), true, false));
    }

    /**
     * An empty socket's silhouette: {@code filter:grayscale(1) brightness(1.7)}, baked in. The 15%
     * opacity is left to the tint so the same raster serves every alpha.
     */
    public static Raster ghost(String name) {
        return CACHE.computeIfAbsent("g:" + name, k -> build(SPR.get(name), false, true));
    }

    private static Raster build(String[] rows, boolean mono, boolean ghost) {
        if (rows == null) {
            return new Raster(0, 0, new int[0], mono, 0, 0);
        }
        int w = width(rows);
        int h = rows.length;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < r.length(); x++) {
                char ch = r.charAt(x);
                if (ch == '.') {
                    continue;
                }
                int c;
                if (mono) {
                    c = 0xFFFFFFFF;
                } else {
                    Integer p = PAL.get(ch);
                    c = p == null ? 0xFFFF00FF : p;
                    if (ghost) {
                        c = ghostColour(c);
                    }
                }
                px[y * w + x] = c;
            }
        }
        return new Raster(w, h, px, mono, 0, 0);
    }

    /** grayscale(1) then brightness(1.7), in sRGB, as Chrome applies CSS filter functions. */
    static int ghostColour(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        int v = (int) Math.min(255, Math.round(l * 1.7));
        return 0xFF000000 | (v << 16) | (v << 8) | v;
    }

    /**
     * A radial alpha falloff for rarity glows: full at the centre, gone at {@code edge} of the
     * radius - {@code radial-gradient(circle, c, transparent 68%)} with the radius normalised to 1.
     */
    public static Raster radial(float edge) {
        String key = "r:" + edge;
        return CACHE.computeIfAbsent(key, k -> {
            int size = 128;
            int[] px = new int[size * size];
            float c = (size - 1) / 2.0F;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x - c) / c;
                    float dy = (y - c) / c;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float a = Math.max(0.0F, 1.0F - d / edge);
                    px[y * size + x] = (Math.round(a * 255.0F) << 24) | 0xFFFFFF;
                }
            }
            return new Raster(size, size, px, true, 0, 0);
        });
    }

    /** Drops every raster (the painter frees its copies through {@code Rasters.release}). */
    public static void clear() {
        for (Raster r : CACHE.values()) {
            com.barbwra.mlum.client.ui.text.Rasters.release(r);
        }
        CACHE.clear();
    }
}
