package com.barbwra.mlum.client.feel;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.hud.field.Shapes;
import com.barbwra.mlum.client.loot.WorldProjector;
import com.barbwra.mlum.compat.TaczCompat;
import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.lwjgl.glfw.GLFW;

/**
 * F5 over the shoulder. The game's own third-person view sits straight behind the head, which hides
 * whatever you are looking at behind your own skull; this moves the camera out to one shoulder and
 * a little closer, and X swaps the shoulder.
 *
 * <p><b>Where the shots go.</b> Nothing about aiming changes: what you hit, and where a TACZ bullet
 * flies, is still the ray from your eyes. From the shoulder that ray is no longer the middle of the
 * screen, so a small mark is drawn where it actually lands. With a gun in hand the mark is a ring.</p>
 *
 * <p>The camera is moved in {@link ViewportEvent.ComputeCameraAngles}, which the game fires after
 * placing the camera and before drawing anything from it. The position setter is not public, so it
 * is reached by its obfuscated name; if that ever fails the camera is simply left where it was.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ShoulderCamera {

    private ShoulderCamera() {
    }

    public static final KeyMapping SWAP = new KeyMapping("key.mlum.shoulder", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.mlum");

    @Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Keys {
        private Keys() {
        }

        @SubscribeEvent
        public static void onRegister(RegisterKeyMappingsEvent event) {
            event.register(SWAP);
        }
    }

    private static final double RANGE = 160.0D;

    /** +1 right shoulder, -1 left. {@link #side} eases toward it so a swap glides across. */
    private static int target = 1;
    private static double side = 1.0D;
    private static long lastNanos;

    private static Method setPosition;
    private static Field eyeHeight;
    private static Field eyeHeightOld;
    private static boolean broken;

    /** Where the eye ray lands this frame, for the mark. Null when there is nothing to draw. */
    private static Vec3 aimPoint;
    private static boolean aimOnEntity;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        while (SWAP.consumeClick()) {
            target = -target;
        }
    }

    private static boolean active(Minecraft mc) {
        return MlumConfig.shoulderCamera() && mc.player != null && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && !mc.player.isPassenger() && mc.getCameraEntity() == mc.player;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = lastNanos == 0L ? 0.0D : Math.min(0.1D, (now - lastNanos) / 1.0E9D);
        lastNanos = now;
        side += (target - side) * Math.min(1.0D, dt * 9.0D);

        if (broken || !active(mc)) {
            return;
        }
        Camera camera = event.getCamera();
        if (!camera.isDetached()) {
            return;
        }
        LocalPlayer player = mc.player;
        float pt = (float) event.getPartialTick();
        float yaw = event.getYaw();
        float pitch = event.getPitch();
        try {
            resolve();
            double eh = Mth.lerp(pt, eyeHeightOld.getFloat(camera), eyeHeight.getFloat(camera));
            Vec3 eye = new Vec3(Mth.lerp(pt, player.xo, player.getX()), Mth.lerp(pt, player.yo, player.getY()) + eh,
                    Mth.lerp(pt, player.zo, player.getZ()));
            Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
            double yr = Math.toRadians(yaw);
            Vec3 right = new Vec3(-Math.cos(yr), 0.0D, -Math.sin(yr));

            Vec3 shoulder = reach(player, eye, eye.add(right.scale(MlumConfig.shoulderOffset() * side)).add(0.0D, 0.12D, 0.0D));
            Vec3 back = reach(player, shoulder, shoulder.subtract(forward.scale(MlumConfig.shoulderDistance())));
            setPosition.invoke(camera, back);
        } catch (Throwable t) {
            broken = true;
            MlumInventory.LOGGER.warn("Shoulder camera disabled: {}", t.toString());
        }
    }

    /**
     * How far from {@code from} toward {@code to} the camera can go before a wall, probed from the
     * eight corners of a small box the way the game probes its own third-person camera, so the near
     * edge of the view never slips inside a block.
     */
    private static Vec3 reach(Entity entity, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        if (len < 1.0E-4D) {
            return from;
        }
        double best = len;
        for (int i = 0; i < 8; i++) {
            double ox = ((i & 1) * 2 - 1) * 0.1D;
            double oy = ((i >> 1 & 1) * 2 - 1) * 0.1D;
            double oz = ((i >> 2 & 1) * 2 - 1) * 0.1D;
            Vec3 a = from.add(ox, oy, oz);
            Vec3 b = to.add(ox, oy, oz);
            BlockHitResult hit = entity.level().clip(new ClipContext(a, b, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.MISS) {
                best = Math.min(best, hit.getLocation().distanceTo(a));
            }
        }
        return from.add(dir.scale(Math.max(0.0D, best - 0.05D) / len));
    }

    private static void resolve() {
        if (setPosition == null) {
            setPosition = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90581_", Vec3.class);
            eyeHeight = ObfuscationReflectionHelper.findField(Camera.class, "f_90562_");
            eyeHeightOld = ObfuscationReflectionHelper.findField(Camera.class, "f_90563_");
        }
    }

    /* ------------------------------------------------------------------ the aim mark */

    private static final HudPen PEN = new HudPen();

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MlumConfig.aimMarker() || mc.player == null || mc.options.hideGui || mc.screen != null || HudVisibility.hidden()
                || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || mc.getCameraEntity() != mc.player
                || mc.player.isSpectator()) {
            return;
        }
        float pt = event.getPartialTick();
        trace(mc.player, pt);
        if (aimPoint == null) {
            return;
        }
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        float[] at = new float[2];
        if (!WorldProjector.project(aimPoint.x, aimPoint.y, aimPoint.z, w, h, at)) {
            return;
        }
        boolean gun = TaczCompat.isGun(mc.player.getMainHandItem());
        int color = aimOnEntity ? 0xFFE0533A : (gun ? 0xFFF0A93B : 0xE6F2EBDD);
        PEN.begin(event.getGuiGraphics());
        try {
            if (gun) {
                Shapes.ring(PEN, at[0], at[1], 3.4F, 0.9F, 0x80000000);
                Shapes.ring(PEN, at[0], at[1], 3.2F, 0.6F, color);
                Shapes.disc(PEN, at[0], at[1], 0.75F, color);
            } else {
                Shapes.disc(PEN, at[0], at[1], 1.4F, 0x80000000);
                Shapes.disc(PEN, at[0], at[1], 1.0F, color);
            }
        } finally {
            PEN.end();
        }
    }

    /** The eye ray, against blocks and then against anything alive in front of the block. */
    private static void trace(LocalPlayer player, float pt) {
        Vec3 eye = player.getEyePosition(pt);
        Vec3 look = player.getViewVector(pt);
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        double reach = stop.distanceToSqr(eye);
        AABB box = player.getBoundingBox().expandTowards(look.scale(Math.sqrt(reach))).inflate(1.0D);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, stop, box,
                e -> !e.isSpectator() && e.isPickable() && !e.isInvisible(), reach);
        if (entity != null) {
            aimPoint = entity.getLocation();
            aimOnEntity = true;
        } else {
            aimPoint = stop;
            aimOnEntity = false;
        }
    }
}
