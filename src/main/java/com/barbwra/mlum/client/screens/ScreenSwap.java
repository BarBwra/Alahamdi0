package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.mc.UiText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Random;

/**
 * Puts the redrawn screens in place of the game's own: the ESC menu, the title screen and the
 * crafting table are swapped as they open; the connecting, loading and saving screens are left
 * doing their work and only drawn over.
 *
 * <p>Each one can be turned off on its own in the client config ({@code [screens]}), which brings
 * the vanilla screen straight back.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ScreenSwap {

    private ScreenSwap() {
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        if (screen instanceof PauseScreen && MlumConfig.themedPause()) {
            event.setNewScreen(new MlifePauseScreen());
        } else if (screen != null && screen.getClass() == TitleScreen.class && MlumConfig.themedTitle()) {
            event.setNewScreen(new MlifeTitleScreen());
        } else if (screen != null && screen.getClass() == CraftingScreen.class && MlumConfig.themedCrafting()
                && Minecraft.getInstance().player != null) {
            CraftingScreen cs = (CraftingScreen) screen;
            event.setNewScreen(new MlifeCraftingScreen(cs.getMenu(), Minecraft.getInstance().player.getInventory(), cs.getTitle()));
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        MlifePauseScreen.sessionStarted();
    }

    /* ================================================================== loading screens */

    private static final ScreenKit KIT = new ScreenKit();
    private static Screen tipFor;
    private static String tip = "";

    private static String status(Screen s) {
        if (s instanceof ConnectScreen) {
            return "جاري الاتصال بالسيرفر";
        }
        if (s instanceof ReceivingLevelScreen) {
            return "جاري تحميل العالم";
        }
        if (s instanceof LevelLoadingScreen) {
            return "جاري تجهيز العالم";
        }
        if (s instanceof GenericDirtMessageScreen) {
            return "جاري الحفظ";
        }
        if (s instanceof ProgressScreen) {
            return "جاري التحميل";
        }
        return null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onRender(ScreenEvent.Render.Pre event) {
        Screen s = event.getScreen();
        String status = status(s);
        if (status == null || !MlumConfig.themedLoading()) {
            return;
        }
        event.setCanceled(true);
        if (tipFor != s) {
            tipFor = s;
            List<? extends String> tips = MlumConfig.tips();
            tip = tips.isEmpty() ? "" : tips.get(new Random().nextInt(tips.size()));
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = s.width;
        int h = s.height;
        ScreenKit kit = KIT;
        kit.begin(g);
        try {
            HudPen pen = kit.pen;
            pen.rect(0, 0, w, h, ScreenKit.PAGE);
            pen.vgrad(0, h * 0.5F, w, h * 0.5F, 0x00000000, 0x66000000);
            // faint grid, the bag's slot texture at screen scale
            for (float x = 0; x < w; x += 24.0F) {
                pen.rect(x, 0, 0.5F, h, 0x08FFFFFF);
            }
            for (float y = 0; y < h; y += 24.0F) {
                pen.rect(0, y, w, 0.5F, 0x08FFFFFF);
            }
            float cx = w / 2.0F;
            kit.wordmark(MlumConfig.serverName(), cx, h * 0.42F, 30.0F);
            pen.text(pen.kufi(status, 7.0F, 700), cx, h * 0.42F + 16.0F, HudPen.CENTER, ScreenKit.BONE);
            // an endless bar: a lit stretch sliding along a dim track
            float bw = Math.min(180.0F, w * 0.4F);
            float by = h * 0.42F + 24.0F;
            pen.rect(cx - bw / 2.0F, by, bw, 1.5F, 0x22FFFFFF);
            float t = (System.currentTimeMillis() % 1400L) / 1400.0F;
            float seg = bw * 0.28F;
            float from = Mth.clamp(cx - bw / 2.0F - seg + (bw + seg) * t, cx - bw / 2.0F, cx + bw / 2.0F);
            float to = Mth.clamp(cx - bw / 2.0F + (bw + seg) * t, cx - bw / 2.0F, cx + bw / 2.0F);
            pen.rect(from, by, to - from, 1.5F, ScreenKit.AMBER);
            if (!tip.isEmpty()) {
                pen.text(pen.kufi("نصيحة", 5.0F, 700), cx, h - 30.0F, HudPen.CENTER, ScreenKit.AMBER);
                pen.text(pen.kufi(UiText.logical(tip), 6.0F, 600), cx, h - 20.0F, HudPen.CENTER, ScreenKit.SOFT);
            }
        } finally {
            kit.end();
        }
        // the screen's own buttons (Cancel while connecting) still work and are still drawn
        for (var child : s.children()) {
            if (child instanceof Renderable r) {
                r.render(g, event.getMouseX(), event.getMouseY(), event.getPartialTick());
            }
        }
    }
}
