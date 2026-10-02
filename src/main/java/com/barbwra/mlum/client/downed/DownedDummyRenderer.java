package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.downed.DownedDummy;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * The practice body: the default player model in the default skin, lying where it fell.
 *
 * <p>The entity sits in the sleeping pose, and the living-entity renderer already lays any body in
 * that pose flat on its back - the same way a downed player is drawn - so there is nothing to
 * position by hand here.</p>
 */
@OnlyIn(Dist.CLIENT)
public class DownedDummyRenderer extends LivingEntityRenderer<DownedDummy, PlayerModel<DownedDummy>> {

    public DownedDummyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.4F);
    }

    @Override
    public ResourceLocation getTextureLocation(DownedDummy entity) {
        return DefaultPlayerSkin.getDefaultSkin();
    }

    @Override
    protected boolean shouldShowName(DownedDummy entity) {
        return false;
    }
}
