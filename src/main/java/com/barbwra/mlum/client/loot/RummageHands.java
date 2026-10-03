package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.downed.DownedClientEvents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Both hands up and rummaging while you search a container or work on a downed player.
 *
 * <p>Whatever was in your hands is put away for the length of it and your two bare arms come up
 * into view - the same pose vanilla uses to hold a map in both hands - and dig: each hand bobs in
 * and out, a half beat behind the other. They rise over a fifth of a second when it starts and
 * drop again when it ends, so nothing pops.</p>
 *
 * <p>Drawn here instead of the normal hands; TACZ's gun model is skipped with them, so a rifle
 * never floats through the cupboard you are going through.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class RummageHands {

    private RummageHands() {
    }

    private static final long RISE_MS = 200L;

    private static boolean wasActive;
    private static long changedAt;

    /** Searching or reviving, on this client, right now. */
    private static boolean busy() {
        return ClientLootSearch.activePos() != null || DownedClientEvents.reviving();
    }

    private static boolean fast() {
        return ClientLootSearch.activePos() != null && ClientLootSearch.fast();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean active = busy();
        long now = Anim.now();
        if (active != wasActive) {
            wasActive = active;
            changedAt = now;
        }
        // 1 = fully up; on the way down after the search it falls back to 0 before the hands return
        float up = Mth.clamp((now - changedAt) / (float) RISE_MS, 0.0F, 1.0F);
        float raise = active ? up : 1.0F - up;
        if (player == null || raise <= 0.0F || player.isInvisible()) {
            return;
        }
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        if (!(renderer instanceof PlayerRenderer arms)) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        float ease = 1.0F - (1.0F - raise) * (1.0F - raise);
        double hz = fast() ? 2.8D : 1.7D;
        double t = Anim.enabled() ? (now / 1000.0D) * Math.PI * 2.0D * hz : 0.0D;

        // vanilla's two-handed map: held low while looking ahead, brought up when looking down
        float tilt = mapTilt(event.getInterpolatedPitch());
        pose.pushPose();
        pose.translate(0.0F, 0.04F + (1.0F - ease) * -1.2F + tilt * -0.5F, -0.72F);
        pose.mulPose(Axis.XP.rotationDegrees(tilt * -85.0F));
        for (HumanoidArm side : HumanoidArm.values()) {
            float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
            double phase = t + (side == HumanoidArm.RIGHT ? 0.0D : Math.PI);
            float dig = (float) Math.sin(phase);
            float sway = (float) Math.cos(phase);
            pose.pushPose();
            // in and out of the container, a little up and down, a little towards the middle
            pose.translate(f * -0.025F * (1.0F + sway), 0.035F * dig, -0.06F * sway);
            pose.mulPose(Axis.XP.rotationDegrees(6.0F * dig));
            pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            // vanilla's two-handed map arm
            pose.mulPose(Axis.YP.rotationDegrees(92.0F));
            pose.mulPose(Axis.XP.rotationDegrees(45.0F));
            pose.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
            pose.translate(f * 0.3F, -1.1F, 0.45F);
            if (side == HumanoidArm.RIGHT) {
                arms.renderRightHand(pose, buffers, light, player);
            } else {
                arms.renderLeftHand(pose, buffers, light, player);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /** Vanilla's {@code calculateMapTilt}: 1 looking straight ahead, 0 from 45 degrees down. */
    private static float mapTilt(float pitch) {
        float f = Mth.clamp(1.0F - pitch / 45.0F + 0.1F, 0.0F, 1.0F);
        return -Mth.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }
}
