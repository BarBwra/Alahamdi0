package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumInventory;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * A real entity, rendered into a menu - which is how a vehicle gets to be its actual 3D model
 * instead of a flat picture.
 *
 * <h2>Why not {@code InventoryScreen.renderEntityInInventory}</h2>
 * <p>Vanilla's helper takes a {@code LivingEntity}. A Superb Warfare vehicle is a plain
 * {@link Entity} - it is not alive, it has no pose, no head rotation and no {@code LivingEntity}
 * anywhere in its hierarchy - so that method cannot be handed one at all. This is the same sequence
 * written against {@link EntityRenderDispatcher}, which will render anything.</p>
 *
 * <h2>One entity per type, kept</h2>
 * <p>Vehicles are heavy: a GeckoLib model, several textures, an animation controller. Building one
 * per frame would allocate and discard all of that sixty times a second. They are made once, never
 * added to the world, never ticked, and re-used - so nothing about them can affect the game, and the
 * cost is paid on the first frame the tab is opened.</p>
 *
 * <h2>Nothing here may take the menu down</h2>
 * <p>Another mod's renderer failing is an entirely ordinary thing - a missing texture, a model that
 * wants a level, an animation that expects the entity to have ticked. Every path is wrapped, and a
 * type that throws is remembered as broken and never tried again, so a bad vehicle costs one frame
 * rather than one frame per frame.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class EntityPreview {

    private EntityPreview() {
    }

    private static final Map<ResourceLocation, Entity> CACHE = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> BROKEN = new HashMap<>();

    /** True when this id names an entity type that is installed and has not failed to render. */
    public static boolean available(@Nullable String entityId) {
        return entity(entityId) != null;
    }

    @Nullable
    private static Entity entity(@Nullable String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null || Boolean.TRUE.equals(BROKEN.get(id))) {
            return null;
        }
        Entity cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        if (type == null) {
            BROKEN.put(id, true);
            return null;
        }
        try {
            Entity made = type.create(mc.level);
            if (made == null) {
                BROKEN.put(id, true);
                return null;
            }
            CACHE.put(id, made);
            return made;
        } catch (Throwable refused) {
            MlumInventory.LOGGER.warn("[{}] could not build a preview of {} - falling back to the picture: {}",
                    MlumInventory.MODID, entityId, refused.toString());
            BROKEN.put(id, true);
            return null;
        }
    }

    /**
     * Draws the entity to fill the box, seen from a three-quarter view that turns with the cursor.
     *
     * @return false when there is nothing to draw, so the caller can fall back to a flat image
     */
    public static boolean render(GuiGraphics g, String entityId, int x0, int y0, int x1, int y1,
                                 int mouseX, float spin) {
        Entity entity = entity(entityId);
        if (entity == null || g == null) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        Minecraft mc = Minecraft.getInstance();
        int cx = (x0 + x1) / 2;
        int cy = (y0 + y1) / 2;

        /*
         * Fit by the entity's own size. A pickup is three blocks long and a quad bike is one; a
         * fixed scale would either bury the big ones in the panel edges or leave the small ones as
         * a speck. The 0.62 leaves room for the wheels and the shadowless float.
         */
        float span = Math.max(0.5F, Math.max(entity.getBbWidth(), entity.getBbHeight()));
        float box = Math.min(x1 - x0, (y1 - y0) * 1.6F);
        int scale = Math.max(4, Math.round(box * 0.62F / span));

        // the model turns to follow the pointer, within a quarter turn either side of three-quarters
        float aim = (cx - mouseX) / Math.max(1.0F, (x1 - x0) * 0.5F);
        float yaw = 215.0F + Math.max(-45.0F, Math.min(45.0F, aim * 45.0F)) + spin;

        PoseStack poses = g.pose();
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        boolean ok = true;
        poses.pushPose();
        try {
            poses.translate(cx, cy + (y1 - y0) * 0.22F, 120.0F);
            poses.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            // upright, then tipped forward a little so the roof reads as a roof
            poses.mulPose(Axis.ZP.rotationDegrees(180.0F));
            poses.mulPose(Axis.XP.rotationDegrees(-18.0F));
            poses.mulPose(Axis.YP.rotationDegrees(yaw));

            entity.setYRot(0.0F);
            entity.setXRot(0.0F);
            entity.yRotO = 0.0F;
            entity.xRotO = 0.0F;
            entity.setPos(0.0D, 0.0D, 0.0D);

            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            RenderSystem.runAsFancy(() ->
                    dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poses, buffers, 15728880));
            buffers.endBatch();
        } catch (Throwable broken) {
            ok = false;
            if (id != null) {
                BROKEN.put(id, true);
                CACHE.remove(id);
            }
            MlumInventory.LOGGER.warn("[{}] {} failed to render in the menu - using the picture from now on: {}",
                    MlumInventory.MODID, entityId, broken.toString());
        } finally {
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
            poses.popPose();
        }
        return ok;
    }

    /** The cached entities belong to the level they were made in. */
    public static void clear() {
        CACHE.clear();
        BROKEN.clear();
    }
}
