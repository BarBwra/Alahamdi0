package com.barbwra.mlum.client.gui;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Drawn shapes for vehicles with no configured image.
 *
 * <p>Picked by <b>matching words in the entity id or the display name</b>, not by a table anyone
 * has to maintain. A pack owner who adds {@code somepack:police_helicopter} tomorrow gets the
 * helicopter shape without touching config, and a name nobody recognises still gets a generic
 * chassis rather than an empty box.</p>
 *
 * <p>These are the permanent answer for an undrawn vehicle, not a placeholder - which is why they
 * are shapes rather than a question mark.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VehicleSilhouette {

    private VehicleSilhouette() {
    }

    public static final String[] HELI = {
            "..............................",
            "############################..",
            "..............#...............",
            "..........#########...........",
            ".......###############.......#",
            "......###################....#",
            ".....######################.##",
            ".....#####################.###",
            "......###################..###",
            ".......########.......#####...",
            "........######................",
            "......##########..............",
    };

    public static final String[] TRUCK = {
            "..............................",
            "......########################",
            ".....#########################",
            "####.#########################",
            "#####.########################",
            "##############################",
            "##############################",
            "..###....###..........###.###.",
            "..###....###..........###.###.",
            "...#......#............#...#..",
    };

    public static final String[] VAN = {
            "..............................",
            "........######################",
            ".....#########################",
            "...###########################",
            "..############################",
            ".#############################",
            "##############################",
            "##############################",
            "..###...................###...",
            "...#.....................#....",
    };

    public static final String[] CAR = {
            "..............................",
            "..............................",
            "..........############........",
            ".......###################....",
            "....#########################.",
            "##############################",
            "##############################",
            "..###...................###...",
            "...#.....................#....",
    };

    public static final String[] BIKE = {
            "..............................",
            "...................####.......",
            "..............########........",
            ".........#######..............",
            "......#####...................",
            "...####.......................",
            ".#####...............#####....",
            "#######.............#######...",
            ".#####...............#####....",
            "...#...................#......",
    };

    public static final String[] QUAD = {
            "..............................",
            "..............................",
            "..........########............",
            "......################........",
            "...#####################......",
            "..########################....",
            "..########################....",
            ".####................####.....",
            ".####................####.....",
            "..##..................##......",
    };

    public static final String[] HORSE = {
            "..............................",
            "......................####....",
            "....................######....",
            "...................#####......",
            "......############.###........",
            "....###############..........",
            "...#################.........",
            "...#################.........",
            "...###....####...###.........",
            "...###....####...###.........",
            "...###....####...###.........",
            "....#......##.....#..........",
    };

    /** Turret, gun barrel and road wheels - the three things that say "tank" at any size. */
    public static final String[] TANK = {
            "..............................",
            "..............................",
            ".........#########............",
            "........###########...........",
            "........###################...",
            "......###############.........",
            "..############################",
            ".#############################",
            ".#############################",
            "..##.##.##.##.##.##.##.##.##..",
            "...#..#..#..#..#..#..#..#..#..",
    };

    /** Anything unrecognised: a plain chassis, so an unknown vehicle still reads as a vehicle. */
    public static final String[] GENERIC = {
            "..............................",
            "..............................",
            "..............................",
            "....######################....",
            "..########################....",
            "..########################....",
            "..########################....",
            "...###...............###......",
            "....#.................#.......",
    };

    /**
     * Best shape for a vehicle, from whatever its id and name say about it.
     *
     * <p>Both are searched because a pack may register {@code mod:vehicle_07} and call it
     * "Blackhawk" - the id carries no meaning there and the name carries all of it.</p>
     */
    public static String[] forVehicle(String entityId, String displayName) {
        String hay = ((entityId == null ? "" : entityId) + " "
                + (displayName == null ? "" : displayName)).toLowerCase(Locale.ROOT);

        if (matches(hay, "heli", "chopper", "ah_", "ah6", "uh", "mi_", "z10", "z-10", "blackhawk", "rotor")) {
            return HELI;
        }
        if (matches(hay, "horse", "pony", "mule", "donkey", "camel")) {
            return HORSE;
        }
        if (matches(hay, "bike", "motor", "cycle", "scooter", "moped")) {
            return BIKE;
        }
        if (matches(hay, "quad", "atv", "buggy", "bobcat", "kart")) {
            return QUAD;
        }
        // Checked before the truck rule, which also claims the word "tank" - a designation like
        // m1a2 or l2a6 carries no English word at all, so the model numbers are matched directly.
        if (matches(hay, "tank", "abrams", "m1a", "leopard", "l2a", "t_72", "t72", "t_90", "t90",
                "sph", "artillery", "howitzer", "merkava", "challenger")) {
            return TANK;
        }
        if (matches(hay, "truck", "ural", "lorry", "hauler", "apc", "defender")) {
            return TRUCK;
        }
        if (matches(hay, "van", "hiace", "bus", "minibus", "transit")) {
            return VAN;
        }
        if (matches(hay, "car", "pickup", "humvee", "kodiak", "jeep", "suv", "sedan")) {
            return CAR;
        }
        return GENERIC;
    }

    private static boolean matches(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
