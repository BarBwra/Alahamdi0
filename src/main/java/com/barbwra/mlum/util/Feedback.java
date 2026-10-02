package com.barbwra.mlum.util;

import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CToast;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Tells a player how an action went, in the menu's own toast.
 *
 * <p>Markup: {@code {b}bold{/b}} and {@code {n}1,500{/n}} for numbers, logical Arabic otherwise.
 * The client shows it as a toast while a menu is open and as a chat line when none is.</p>
 */
public final class Feedback {

    private Feedback() {
    }

    public static void ok(ServerPlayer player, String markup) {
        send(player, markup, false);
    }

    public static void bad(ServerPlayer player, String markup) {
        send(player, markup, true);
    }

    public static void send(ServerPlayer player, String markup, boolean bad) {
        if (player == null || player.connection == null || markup == null) {
            return;
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CToast(markup, bad));
    }

    /** {@code 24,350} - the way every number in the menus is written. */
    public static String num(long value) {
        return "{n}" + String.format(java.util.Locale.US, "%,d", value) + "{/n}";
    }
}
