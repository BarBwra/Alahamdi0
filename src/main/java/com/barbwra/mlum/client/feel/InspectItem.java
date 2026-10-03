package com.barbwra.mlum.client.feel;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.TaczCompat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Hold the inspect key and what is in your hand comes up in front of you and turns slowly, with its
 * name and details under it. Let go and it drops back.
 *
 * <p><b>One key with TACZ.</b> TACZ already has an inspect key (H) for its guns. Two keys on H would
 * show as a conflict, so when TACZ is installed this reads TACZ's own key and only acts when the
 * hand holds something that is not a gun - the gun keeps TACZ's animation. Without TACZ this
 * registers its own key on H.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class InspectItem {

    private InspectItem() {
    }

    private static final String TACZ_INSPECT = "key.tacz.inspect.desc";
    private static KeyMapping own;
    private static KeyMapping tacz;
    private static boolean looked;

    @Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            if (!ModList.get().isLoaded(TaczCompat.MODID)) {
                own = new KeyMapping("key.mlum.inspect", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
                        GLFW.GLFW_KEY_H, "key.categories.mlum");
                event.register(own);
            }
        }
    }

    private static final long RISE_MS = 260L;
    private static boolean held;
    private static long changedAt;
    private static long startedAt;
    private static ItemStack shown = ItemStack.EMPTY;

    private static KeyMapping key() {
        if (own != null) {
            return own;
        }
        if (!looked) {
            looked = true;
            for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
                if (TACZ_INSPECT.equals(k.getName())) {
                    tacz = k;
                    break;
                }
            }
        }
        return tacz;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        KeyMapping key = key();
        boolean want = MlumConfig.inspectItem() && player != null && key != null && key.isDown() && mc.screen == null
                && mc.options.getCameraType().isFirstPerson() && !player.getMainHandItem().isEmpty()
                && !TaczCompat.isGun(player.getMainHandItem());
        if (want && player != null) {
            shown = player.getMainHandItem();
        }
        if (want != held) {
            held = want;
            long now = System.currentTimeMillis();
            // turning back mid-way starts from where it is, not from the far end
            long done = Math.min(RISE_MS, now - changedAt);
            changedAt = now - (RISE_MS - done);
            if (want) {
                startedAt = now;
            }
        }
    }

    /** 0 = in the hand as normal, 1 = held up in front of the eyes. */
    private static float raise() {
        float t = Mth.clamp((System.currentTimeMillis() - changedAt) / (float) RISE_MS, 0.0F, 1.0F);
        float r = held ? t : 1.0F - t;
        return r * r * (3.0F - 2.0F * r);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderHand(RenderHandEvent event) {
        float r = raise();
        Minecraft mc = Minecraft.getInstance();
        if (r <= 0.0F || mc.player == null || shown.isEmpty()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        float seconds = (System.currentTimeMillis() - startedAt) / 1000.0F;
        pose.pushPose();
        pose.translate(Mth.lerp(r, 0.56F, 0.0F), Mth.lerp(r, -0.6F, -0.02F), Mth.lerp(r, -0.72F, -0.62F));
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(r, 0.0F, 14.0F) + (float) Math.sin(seconds * 0.9F) * 4.0F));
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(r, -30.0F, 0.0F) + seconds * 38.0F));
        float scale = Mth.lerp(r, 0.6F, 0.42F);
        pose.scale(scale, scale, scale);
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(mc.player, shown, ItemDisplayContext.FIXED, false,
                pose, event.getMultiBufferSource(), event.getPackedLight());
        pose.popPose();
    }

    /** The name and the item's own details, under it. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float r = raise();
        Minecraft mc = Minecraft.getInstance();
        if (r < 0.6F || mc.player == null || shown.isEmpty() || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        List<Component> lines = shown.getTooltipLines(mc.player, TooltipFlag.NORMAL);
        int max = Math.min(lines.size(), 7);
        int alpha = (int) (Mth.clamp((r - 0.6F) / 0.4F, 0.0F, 1.0F) * 255.0F);
        if (alpha < 8) {
            return;
        }
        int y = (int) (h * 0.72F);
        int widest = 0;
        for (int i = 0; i < max; i++) {
            widest = Math.max(widest, font.width(lines.get(i)));
        }
        int boxW = widest + 16;
        int boxH = max * 11 + 8;
        int x0 = w / 2 - boxW / 2;
        g.fill(x0, y - 4, x0 + boxW, y - 4 + boxH, (alpha * 0xB8 / 255) << 24 | 0x0B0D0A);
        g.fill(x0, y - 4, x0 + boxW, y - 3, (alpha << 24) | 0xF0A93B);
        for (int i = 0; i < max; i++) {
            Component line = lines.get(i);
            int color = i == 0 ? 0xF2EBDD : 0xB8B0A0;
            g.drawString(font, line, w / 2 - font.width(line) / 2, y + i * 11, (alpha << 24) | color, true);
        }
    }
}
