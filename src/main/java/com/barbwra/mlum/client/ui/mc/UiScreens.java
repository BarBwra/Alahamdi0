package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.network.C2SOpenMlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * Moving between the six menus without ever passing through "no screen".
 *
 * <p>Dropping to the world between two menus is what used to throw the cursor to the middle of the
 * window on every switch: the game grabs the mouse (and centres it) the moment no screen is open,
 * then releases it (and centres it again) when the next one opens. Here every switch replaces one
 * screen with the next directly, so the cursor stays exactly where the player left it.</p>
 *
 * <p>The bag is the one menu the server owns - it holds real slots - so reaching it is a request,
 * answered a round trip later; the static holds until it arrives. Leaving it tells the server the
 * container is closed without closing the screen on this side.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UiScreens {

    private UiScreens() {
    }

    public static final int TABS = 6;

    public static boolean isOpen(Screen screen) {
        return screen instanceof BagScreen || screen instanceof TabsScreen;
    }

    /** The tab {@code dir} steps away: A is +1, D is -1, wrapping round. */
    public static int step(int tab, int dir) {
        return Math.floorMod(tab + dir, TABS);
    }

    /** +1 for A (and left), -1 for D (and right), 0 for any other key. */
    public static int navDirection(int keyCode, int scanCode) {
        Minecraft mc = Minecraft.getInstance();
        if (keyCode == GLFW.GLFW_KEY_A || keyCode == GLFW.GLFW_KEY_LEFT
                || mc.options.keyLeft.matches(keyCode, scanCode)) {
            return 1;
        }
        if (keyCode == GLFW.GLFW_KEY_D || keyCode == GLFW.GLFW_KEY_RIGHT
                || mc.options.keyRight.matches(keyCode, scanCode)) {
            return -1;
        }
        return 0;
    }

    /** From the menu showing tab {@code from} to tab {@code to}. */
    public static void go(int from, int to) {
        if (to == from) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (to == 3) {
            UiState.seenSkills = true;
        }
        if (to == 0) {
            UiState.startFx(false, true);
            ModNetwork.CHANNEL.sendToServer(C2SOpenMlumInventory.INSTANCE);
            return;
        }
        UiState.startFx(false, false);
        if (mc.screen instanceof TabsScreen tabs) {
            tabs.show(to);
            return;
        }
        LocalPlayer player = mc.player;
        boolean hadContainer = player != null && player.containerMenu != player.inventoryMenu;
        if (hadContainer) {
            player.connection.send(new ServerboundContainerClosePacket(player.containerMenu.containerId));
        }
        mc.setScreen(new TabsScreen(to));
        if (hadContainer) {
            player.containerMenu = player.inventoryMenu;
        }
    }
}
