package com.barbwra.mlum.client.hud;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.network.C2SVehicleLock;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.util.ArabicText;
import com.barbwra.mlum.vehicle.VehicleLock;
import com.barbwra.mlum.vehicle.VehicleOwnership;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

/**
 * The lock state, drawn above Superb Warfare's passenger list, and the L key that changes it.
 *
 * <h2>Where it sits</h2>
 * <p>Bottom left, just above the {@code [1] BarBwra} seat rows the vehicle mod draws. That corner is
 * already where "who is in this thing with me" lives, and "who is allowed in" belongs with it - the
 * alternative was a fourth cluster of text somewhere else on an already busy screen.</p>
 *
 * <h2>Read from the entity, not from a packet</h2>
 * <p>The mode is in the vehicle's persistent data, which the server syncs to the client with the
 * entity itself. So there is nothing to send: the HUD reads the same tag the rule reads, and cannot
 * show a state the server does not agree with.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class VehicleLockHud {

    private VehicleLockHud() {
    }

    public static final KeyMapping KEY = new KeyMapping(
            "key.mlum.vehicle_lock", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, "key.categories.mlum");

    @Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(KEY);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS || !KEY.isDown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) {
            return;
        }
        // The server re-checks ownership; asking when you own nothing simply gets a refusal back.
        ModNetwork.CHANNEL.sendToServer(C2SVehicleLock.INSTANCE);
    }

    /* ------------------------------------------------------------------ drawing */

    public static void render(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        /*
         * Read from what the server pushed, NOT from the entity.
         *
         * The mode and the owner stamp live in the vehicle's getPersistentData(), and Forge does not
         * synchronise that tag - the client's copy is empty. Reading it here found no owner and
         * returned before drawing, every frame, which is why this HUD never once appeared.
         */
        com.barbwra.mlum.vehicle.VehicleLock.Mode mode =
                com.barbwra.mlum.client.ClientVehicleLock.mode();
        if (mode == null) {
            return;
        }
        boolean mine = com.barbwra.mlum.client.ClientVehicleLock.isOwner();

        String label = "[" + ArabicText.autoDisplay(mode.label) + "]";
        /*
         * Tuned rather than derived. Superb Warfare positions its seat list through an anchor system
         * built out of Kotlin lambdas, so there is no constant to read - and even if there were,
         * reading it would break the next time that mod moved anything. Configurable instead:
         * clientConfig vehicleLockHudX / vehicleLockHudY.
         */
        int x = com.barbwra.mlum.MlumConfig.lockHudX();
        int y = height - com.barbwra.mlum.MlumConfig.lockHudY();
        graphics.drawString(mc.font, label, x, y, mode.color, true);
        // only the owner is told about the key, because only the owner's press does anything
        if (mine) {
            graphics.drawString(mc.font, "= L", x + mc.font.width(label) + 4, y, 0xA19E8B, true);
        }
    }
}
