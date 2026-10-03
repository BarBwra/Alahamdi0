package com.barbwra.mlum.client.feel;

import com.barbwra.mlum.MlumConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Things on the ground lie on the ground. The game floats every dropped item a hand's width up and
 * spins it forever; here a flat item lies flat at its own angle, a block sits on its face, and only
 * something still in the air turns over as it falls.
 *
 * <p>Purely a drawing change: the item entity, its pickup box and its physics are the game's own.
 * With {@code itemPhysics} off in the client config this hands every item straight to the normal
 * renderer.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class FlatItemRenderer extends ItemEntityRenderer {

    private final ItemRenderer items;
    private final RandomSource random = RandomSource.create();

    public FlatItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ItemEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!MlumConfig.itemPhysics()) {
            super.render(entity, yaw, partialTick, pose, buffers, light);
            return;
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            return;
        }
        int seed = entity.getUUID().hashCode();
        random.setSeed(seed);
        BakedModel model = items.getModel(stack, entity.level(), null, seed);
        boolean block = model.isGui3d();
        int copies = copies(stack.getCount());
        float angle = (seed & 0xFFFF) / 65535.0F * 360.0F;
        boolean resting = entity.onGround() && entity.getDeltaMovement().lengthSqr() < 0.01D;
        float tumble = resting ? 0.0F : (entity.getAge() + partialTick) * 18.0F;

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        if (block) {
            // the ground transform lifts a block a sixteenth plus its own half height; this sits it down
            pose.translate(0.0D, resting ? -0.0625D + 0.001D : 0.06D, 0.0D);
            if (!resting) {
                pose.mulPose(Axis.XP.rotationDegrees(tumble));
            }
        } else {
            pose.translate(0.0D, resting ? 0.018D : 0.1D, 0.0D);
            pose.mulPose(Axis.XP.rotationDegrees(90.0F + tumble));
        }
        for (int i = 0; i < copies; i++) {
            pose.pushPose();
            if (i > 0) {
                float dx = (random.nextFloat() * 2.0F - 1.0F) * 0.12F;
                float dy = (random.nextFloat() * 2.0F - 1.0F) * 0.12F;
                if (block) {
                    pose.translate(dx, 0.0F, dy);
                } else {
                    // flat items fan out on the floor, each a hair above the last so none flicker
                    pose.translate(dx, dy, -0.034F * i);
                    pose.mulPose(Axis.ZP.rotationDegrees((random.nextFloat() * 2.0F - 1.0F) * 40.0F));
                }
            }
            items.render(stack, ItemDisplayContext.GROUND, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
            pose.popPose();
        }
        pose.popPose();
    }

    private static int copies(int count) {
        if (count > 48) {
            return 5;
        }
        if (count > 32) {
            return 4;
        }
        if (count > 16) {
            return 3;
        }
        return count > 1 ? 2 : 1;
    }
}
