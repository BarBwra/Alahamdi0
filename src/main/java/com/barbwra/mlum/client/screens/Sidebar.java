package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.ClientFactionData;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.mc.UiState;
import com.barbwra.mlum.client.ui.mc.UiText;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * The side panel: the server's name, your money and faction, and how many are online, in a small
 * card on the right edge. J hides and shows it.
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class Sidebar {

    private Sidebar() {
    }

    public static final KeyMapping TOGGLE = new KeyMapping("key.mlum.sidebar", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.mlum");

    private static boolean hidden;
    private static final ScreenKit KIT = new ScreenKit();

    @Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(TOGGLE);
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            while (TOGGLE.consumeClick()) {
                hidden = !hidden;
            }
        }
    }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (hidden || !MlumConfig.sidebar() || mc.player == null || mc.options.hideGui || HudVisibility.hidden()
                || mc.getConnection() == null) {
            return;
        }
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        float pw = 86.0F;
        float x = w - pw - 4.0F;
        float y = h * 0.38F;
        int online = mc.getConnection().getOnlinePlayers().size();
        String faction = ClientFactionData.inFaction() ? UiText.logical(ClientFactionData.factionName()) : "بدون منظمة";
        ScreenKit kit = KIT;
        kit.begin(event.getGuiGraphics());
        try {
            HudPen pen = kit.pen;
            float ph = 54.0F;
            pen.rect(x, y, pw, ph, 0xB80B0D0A);
            pen.rect(x + pw - 1.0F, y, 1.0F, ph, ScreenKit.AMBER);
            pen.text(pen.pixel(MlumConfig.serverName().toUpperCase(java.util.Locale.ROOT), 9.0F, 700), x + pw - 5.0F, y + 10.0F,
                    HudPen.RIGHT, ScreenKit.BONE);
            pen.rect(x + 4.0F, y + 13.5F, pw - 9.0F, 0.5F, ScreenKit.LINE);
            row(pen, x, y + 22.0F, pw, "فلوسك", String.format(java.util.Locale.US, "%,d", UiState.money()), ScreenKit.SAGE);
            row(pen, x, y + 33.0F, pw, "منظمتك", faction, ScreenKit.BONE);
            row(pen, x, y + 44.0F, pw, "المتصلين", String.valueOf(online), ScreenKit.BONE);
        } finally {
            kit.end();
        }
    }

    private static void row(HudPen pen, float x, float y, float pw, String label, String value, int colour) {
        pen.text(pen.kufi(label, 4.8F, 600), x + pw - 5.0F, y, HudPen.RIGHT, ScreenKit.MUTED);
        boolean latin = value.chars().allMatch(c -> c < 128);
        pen.text(latin ? pen.pixel(value, 7.0F, 700) : pen.kufi(value, 4.8F, 700), x + 4.0F, y + 0.5F, HudPen.LEFT, colour);
    }
}
