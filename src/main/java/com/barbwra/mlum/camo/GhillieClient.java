package com.barbwra.mlum.camo;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The client half of the ghillie suit.
 *
 * <p><b>Drawing.</b> Vanilla keeps drawing armour, held items and every render layer - the Curios
 * backpack on the back among them - on an invisible player. For a player hidden by their suit the
 * whole render is skipped instead and only the shimmer is drawn. Who counts as hidden comes from
 * the server ({@code S2CGhillie}), never from the invisible flag: a potion or another mod making
 * the wearer invisible must not skip the five-second wait.</p>
 *
 * <p><b>The shimmer.</b> Other players do not see nothing: close up, a hidden player is a faint
 * ripple in the air, like heat over a road - the body drawn almost clear, its outline wavering. It
 * fades with distance and is gone past {@link #SHIMMER_RANGE} blocks, and it is several times
 * stronger for someone looking straight at the spot, so it rewards a careful search and not a
 * glance.</p>
 *
 * <p><b>The count.</b> The server decides when you vanish; this runs the same rule locally only so
 * the HUD can show how long is left without waiting on a packet (see {@code FieldHud}).</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class GhillieClient {

    private GhillieClient() {
    }

    private static Ghillie.Settle settle;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        // the bag screen's own portrait is drawn regardless, so you can still see what you wear
        if (HIDDEN_IDS.contains(player.getId()) && !com.barbwra.mlum.client.downed.DownedClientEvents.portrait) {
            event.setCanceled(true);
            if (player instanceof AbstractClientPlayer hidden) {
                shimmer(event, hidden);
            }
        }
    }

    /** Players the server says a suit is hiding. Only these vanish; any other invisibility is vanilla's. */
    private static final java.util.Set<Integer> HIDDEN_IDS = new java.util.HashSet<>();

    public static void receive(int[] ids) {
        HIDDEN_IDS.clear();
        for (int id : ids) {
            HIDDEN_IDS.add(id);
        }
    }

    /** Past this many blocks a hidden player leaves no trace at all. */
    private static final double SHIMMER_RANGE = 6.0D;

    private static void shimmer(RenderPlayerEvent.Pre event, AbstractClientPlayer p) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return;
        }
        float pt = event.getPartialTick();
        float alpha;
        if (p == me) {
            // yourself in third person: always the full shimmer, so you can see what others see
            alpha = 0.16F;
        } else {
            Vec3 eye = me.getEyePosition(pt);
            Vec3 centre = p.getPosition(pt).add(0.0D, p.getBbHeight() * 0.5D, 0.0D);
            double distance = eye.distanceTo(centre);
            if (distance > SHIMMER_RANGE) {
                return;
            }
            double dot = me.getViewVector(pt).dot(centre.subtract(eye).normalize());
            // a glance gets almost nothing; only a stare straight at the spot shows the ripple
            float focus = Mth.clamp((float) ((dot - 0.95D) / 0.045D), 0.0F, 1.0F);
            float near = 1.0F - (float) (distance / SHIMMER_RANGE);
            alpha = near * near * (0.1F + 0.9F * focus) * 0.15F;
        }
        if (alpha < 0.008F) {
            return;
        }
        PlayerRenderer renderer = event.getRenderer();
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            // what LivingEntityRenderer does before drawing the model, without the layers
            float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
            float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
            float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
            pose.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            pose.scale(-1.0F, -1.0F, 1.0F);
            pose.scale(0.9375F, 0.9375F, 0.9375F);
            pose.translate(0.0F, -1.501F, 0.0F);
            float walkSpeed = Math.min(1.0F, p.walkAnimation.speed(pt));
            float walkPos = p.walkAnimation.position(pt);
            model.attackTime = p.getAttackAnim(pt);
            model.riding = p.isPassenger();
            model.young = false;
            model.prepareMobModel(p, walkPos, walkSpeed, pt);
            model.setupAnim(p, walkPos, walkSpeed, p.tickCount + pt, headYaw - bodyYaw, pitch);
            float time = (p.tickCount + pt) / 20.0F;
            VertexConsumer buffer = event.getMultiBufferSource()
                    .getBuffer(RenderType.entityTranslucent(renderer.getTextureLocation(p)));
            // two passes half a wave apart, pale like bent light: the edges waver against each other
            float pulse = 0.85F + 0.15F * (float) Math.sin(time * 3.0D);
            model.renderToBuffer(pose, new Ripple(buffer, time, 0.0D), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    0.86F, 0.92F, 0.96F, alpha * pulse);
            model.renderToBuffer(pose, new Ripple(buffer, time, Math.PI), 0xF000F0, OverlayTexture.NO_OVERLAY,
                    0.86F, 0.92F, 0.96F, alpha * 0.6F * pulse);
        } finally {
            pose.popPose();
        }
    }

    /** Bends every vertex a little sideways, more or less with height and time: heat haze. */
    private static final class Ripple implements VertexConsumer {
        private final VertexConsumer inner;
        private final float time;
        private final double phase;

        Ripple(VertexConsumer inner, float time, double phase) {
            this.inner = inner;
            this.time = time;
            this.phase = phase;
        }

        private double dx(double y) {
            return Math.sin(y * 7.0D + time * 5.0D + phase) * 0.05D;
        }

        private double dz(double y) {
            return Math.cos(y * 6.0D + time * 4.0D + phase) * 0.045D;
        }

        @Override
        public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay,
                           int light, float nx, float ny, float nz) {
            inner.vertex((float) (x + dx(y)), y, (float) (z + dz(y)), r, g, b, a, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            inner.vertex(x + dx(y), y, z + dz(y));
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            inner.color(r, g, b, a);
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            inner.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            inner.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            inner.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            inner.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            inner.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            inner.defaultColor(r, g, b, a);
        }

        @Override
        public void unsetDefaultColor() {
            inner.unsetDefaultColor();
        }
    }

    /** With the field HUD switched off, the ghillie timer still has to show somewhere. */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!MlumConfig.fieldHud()) {
            com.barbwra.mlum.client.hud.field.FieldHud.ghillieOnly(event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        settle = player == null ? null : Ghillie.step(player, settle, player.level().getGameTime());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        settle = null;
        HIDDEN_IDS.clear();
    }

    /** Wearing a full suit right now. */
    public static boolean wearing() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Ghillie.suitOf(player) != null;
    }

    /** Hidden by the suit - what the server last said. */
    public static boolean hidden() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && HIDDEN_IDS.contains(player.getId());
    }

    /** 0..1 of the way there while crouched and still; 0 when not settling. */
    public static float progress() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0.0F : Ghillie.progress(settle, player.level().getGameTime());
    }

    public static boolean settling() {
        return settle != null;
    }
}
