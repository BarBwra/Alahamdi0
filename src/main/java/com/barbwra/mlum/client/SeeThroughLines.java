package com.barbwra.mlum.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.OptionalDouble;

/**
 * Lines that are drawn even when something is in front of them.
 *
 * <h2>Why this class exists at all</h2>
 * <p>{@link RenderType#lines()} depth-tests. A chest outlined with it is only visible when the chest
 * is already visible - which makes it useless for its one job. The scout skill is supposed to find
 * loot <i>through the wall of the house it is in</i>.</p>
 *
 * <p>The only way to build a render type with the depth test off is from a subclass, because
 * {@code RenderType.create} and the state shards it needs are protected. Nothing is inherited for
 * behaviour here - the class exists purely to be inside the access boundary.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SeeThroughLines extends RenderType {

    /** Never called. {@code RenderType} has no no-arg constructor, so one has to be declared. */
    private SeeThroughLines(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
        throw new UnsupportedOperationException();
    }

    public static final RenderType SCOUT = create("mlum_scout_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    // a fixed width, so the outline does not thin out to nothing at range
                    .setLineState(new LineStateShard(OptionalDouble.of(2.5D)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    // colour only: writing depth would let the outline hide the world behind it
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(NO_DEPTH_TEST)
                    .createCompositeState(false));
}
