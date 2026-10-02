package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Turns a point in the world into a point on the HUD.
 *
 * <h2>Why the markers are drawn on the HUD and not in the world</h2>
 * <p>The scout skill used to outline containers with lines drawn into the world, and players with a
 * shader pack saw nothing at all - shader packs replace the world's render passes, and lines a mod
 * adds into them are among the first things lost. Drawing the markers on the HUD instead, at the
 * screen position of each container, sidesteps every graphics setting and shader pack there is.</p>
 *
 * <p>All it needs is the camera's matrices for this frame, which are copied out of the level render
 * as soon as the sky is drawn - the earliest stage, so a pack that suppresses later stages still
 * leaves them - and again after particles.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class WorldProjector {

    private WorldProjector() {
    }

    private static final Matrix4f VIEW = new Matrix4f();
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Vector4f SCRATCH = new Vector4f();
    private static Vec3 camera = Vec3.ZERO;
    private static boolean ready;

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_SKY && stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        VIEW.set(event.getPoseStack().last().pose());
        PROJECTION.set(event.getProjectionMatrix());
        camera = event.getCamera().getPosition();
        ready = true;
    }

    public static boolean ready() {
        return ready;
    }

    public static Vec3 camera() {
        return camera;
    }

    /**
     * Where a world point lands on the HUD, in GUI pixels, written into {@code out}.
     *
     * @return false when the point is behind the camera (or too close to it to place)
     */
    public static boolean project(double x, double y, double z, int guiW, int guiH, float[] out) {
        if (!ready) {
            return false;
        }
        SCRATCH.set((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z), 1.0F);
        SCRATCH.mul(VIEW);
        SCRATCH.mul(PROJECTION);
        if (SCRATCH.w <= 0.05F) {
            return false;
        }
        float nx = SCRATCH.x / SCRATCH.w;
        float ny = SCRATCH.y / SCRATCH.w;
        out[0] = (nx * 0.5F + 0.5F) * guiW;
        out[1] = (1.0F - (ny * 0.5F + 0.5F)) * guiH;
        return true;
    }

    public static void clear() {
        ready = false;
    }
}
