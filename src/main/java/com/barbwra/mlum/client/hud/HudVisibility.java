package com.barbwra.mlum.client.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The one place that decides whether a HUD widget should draw this frame.
 *
 * <p><b>The bug this fixes.</b> Every HUD in the mod - the level bar, the safe-area popup, the
 * firearm card, the warehouse mission compass - independently guarded on
 * {@code minecraft.screen != null}. That is not what vanilla does: vanilla's own HUD keeps drawing
 * behind an open screen, which is why hearts and the hotbar stay put when you open chat or press
 * escape. Ours all vanished and came back, on every one of those, because <i>any</i> screen at all
 * satisfied that test - the chat box, the pause menu, an advancement page.</p>
 *
 * <p>The guard existed for a real reason, though. The mod's own screens fill the display with a
 * deliberately see-through backdrop, so a HUD left drawing underneath one shows through it as
 * clutter. The fix is to name that case instead of catching every screen: hide for the mod's
 * full-screen interfaces, and behave exactly like vanilla for everything else.</p>
 *
 * <p>Matching by class name rather than by {@code instanceof} keeps this class free of imports from
 * every screen package in the mod, and means a new screen only has to be named here rather than
 * wired in. The cost is a string compare per widget per frame, against a set of six.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HudVisibility {

    private HudVisibility() {
    }

    /** The mod's own full-screen interfaces. Anything else is treated the way vanilla treats it. */
    private static final String[] FULLSCREEN = {
            "com.barbwra.mlum.client.ui.mc.BagScreen",
            "com.barbwra.mlum.client.ui.mc.TabsScreen",
            "com.barbwra.mlum.warehouse.client.gui.TerminalScreen",
    };

    /**
     * True when no HUD widget should draw.
     *
     * <p>Covers F1, spectator mode, and having no world - plus the mod's own full-screen menus.
     * Deliberately <b>not</b> chat, the pause menu, or any other screen: those leave the vanilla
     * HUD up, and ours should match.</p>
     */
    public static boolean hidden() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return true;
        }
        if (minecraft.options.hideGui || minecraft.player.isSpectator()) {
            return true;
        }
        return coversScreen(minecraft.screen);
    }

    private static boolean coversScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        for (String fullscreen : FULLSCREEN) {
            if (name.equals(fullscreen)) {
                return true;
            }
        }
        return false;
    }
}
