package com.barbwra.mlum.warehouse.client;

import com.barbwra.mlum.warehouse.client.gui.TerminalScreen;
import com.barbwra.mlum.warehouse.net.TerminalSnapshot;
import net.minecraft.client.Minecraft;

/**
 * The client's copy of the terminal state.
 *
 * <p>Write-only from the network, read-only from the screen. The screen never mutates it and never
 * derives anything the server did not send - if a number is wrong here it is wrong on the server
 * too, which is the failure mode you want.</p>
 */
public final class ClientTerminalData {

    private ClientTerminalData() {
    }

    private static TerminalSnapshot snapshot;

    public static TerminalSnapshot snapshot() {
        return snapshot;
    }

    /**
     * Takes a new snapshot and shows the terminal.
     *
     * <p>Opening only when the screen is not already up is what makes this double as the refresh
     * path: every accepted action sends a fresh snapshot, and the open screen simply starts drawing
     * the new one on its next frame without being torn down and rebuilt.</p>
     */
    public static void accept(TerminalSnapshot incoming) {
        snapshot = incoming;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TerminalScreen)) {
            mc.setScreen(new TerminalScreen());
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof TerminalScreen) {
            mc.setScreen(null);
        }
        snapshot = null;
    }
}
