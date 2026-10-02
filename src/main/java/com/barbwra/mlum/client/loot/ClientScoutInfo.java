package com.barbwra.mlum.client.loot;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * What the server last said about the containers around a level 3 scout: which are empty.
 *
 * <p>A client's copy of a chest never has its contents - they are sent only while it is open - so
 * only the server can say this. It answers every two seconds for the containers in range.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientScoutInfo {

    private ClientScoutInfo() {
    }

    private static Map<Long, Boolean> empty = new HashMap<>();

    public static void set(long[] keys, boolean[] isEmpty) {
        Map<Long, Boolean> next = new HashMap<>();
        for (int i = 0; i < keys.length && i < isEmpty.length; i++) {
            next.put(keys[i], isEmpty[i]);
        }
        empty = next;
    }

    /** TRUE empty, FALSE has something, null not known. */
    public static Boolean isEmpty(long key) {
        return empty.get(key);
    }

    public static void clear() {
        empty = new HashMap<>();
    }
}
