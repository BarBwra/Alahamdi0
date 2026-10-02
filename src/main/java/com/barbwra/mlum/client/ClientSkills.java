package com.barbwra.mlum.client;

import com.barbwra.mlum.network.S2CSkills;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/** The last skills list the server sent. */
@OnlyIn(Dist.CLIENT)
public final class ClientSkills {

    private ClientSkills() {
    }

    private static volatile List<S2CSkills.Entry> skills = List.of();
    private static volatile int maxedCap = 3;
    private static volatile long dropCost = 2000L;
    private static volatile int version;

    public static void set(S2CSkills msg) {
        skills = List.copyOf(msg.skills());
        maxedCap = msg.maxedCap();
        dropCost = msg.dropCost();
        version++;
    }

    /** What the server charges to take a skill back to level 0. */
    public static long dropCost() {
        return dropCost;
    }

    /** This player's level in a skill, 0 when they do not have it or the list has not arrived. */
    public static int levelOf(String id) {
        for (S2CSkills.Entry e : skills) {
            if (e.id().equals(id)) {
                return e.level();
            }
        }
        return 0;
    }

    public static List<S2CSkills.Entry> skills() {
        return skills;
    }

    public static int maxedCap() {
        return maxedCap;
    }

    /** Bumped on every packet: a purchase in flight is over when this moves. */
    public static int version() {
        return version;
    }

    public static void clear() {
        skills = List.of();
        maxedCap = 3;
        version++;
    }
}
