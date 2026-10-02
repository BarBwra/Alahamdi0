package com.barbwra.mlum.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The balance the top bar draws, as last sent by the server.
 *
 * <p>Read-only, like every other client cache here. The number is an account on the server; the
 * client keeps a copy so the wallet can count up to it, and never edits it.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientWallet {

    private ClientWallet() {
    }

    private static long balance;
    private static boolean known;

    public static void accept(long value) {
        balance = Math.max(0L, value);
        known = true;
    }

    public static long balance() {
        return balance;
    }

    /** False until the first packet arrives, so the bar can show a dash rather than a confident 0. */
    public static boolean known() {
        return known;
    }

    /** On disconnect: the next server's balance is not this one's. */
    public static void clear() {
        balance = 0L;
        known = false;
    }
}
