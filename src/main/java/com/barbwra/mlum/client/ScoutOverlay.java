package com.barbwra.mlum.client;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.skill.SkillEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * الباحث: the containers around you, outlined through the walls.
 *
 * <h2>Found by chunk, not by block</h2>
 * <p>A 16-block radius is 35,937 positions. Asking the world about each of them, every frame, to
 * find the dozen that are chests would be the most expensive thing this mod does by a wide margin.
 * Minecraft already keeps a map of the block entities in each chunk, and every container is one - so
 * the search is over a handful of chunks and a few dozen entries instead.</p>
 *
 * <p>Even that is only redone twice a second. The result is a short list of positions; drawing it is
 * cheap, and a chest does not move.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ScoutOverlay {

    private ScoutOverlay() {
    }

    /** Radius in blocks per skill level. */
    private static final int[] RANGE = {8, 12, 16};

    private static final List<BlockPos> FOUND = new ArrayList<>();
    private static long scannedAt = Long.MIN_VALUE;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        int level = ClientSkills.levelOf(SkillEffects.SCOUT);
        if (level <= 0) {
            return;
        }
        int range = RANGE[Math.min(RANGE.length, level) - 1];

        long now = mc.level.getGameTime();
        if (now - scannedAt > 10L || scannedAt > now) {
            scannedAt = now;
            scan(mc, player, range);
        }
        if (FOUND.isEmpty()) {
            return;
        }

        // a slow breath rather than a flash - it sits in the corner of the eye for a long time
        float pulse = 0.34F + 0.26F * (float) Math.sin((now + event.getPartialTick()) * 0.12D);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        // through walls, which is the entire point - see SeeThroughLines
        VertexConsumer lines = buffers.getBuffer(SeeThroughLines.SCOUT);

        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPos pos : FOUND) {
            AABB box = new AABB(pos).inflate(0.006D);
            // the amber the rest of the mod uses: f0a93b
            LevelRenderer.renderLineBox(pose, lines, box, 0.941F, 0.663F, 0.231F, pulse);
        }
        pose.popPose();
        buffers.endBatch(SeeThroughLines.SCOUT);
    }

    private static void scan(Minecraft mc, Player player, int range) {
        FOUND.clear();
        BlockPos origin = player.blockPosition();
        int rangeSq = range * range;
        int chunkSpan = (range >> 4) + 1;
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        for (int dx = -chunkSpan; dx <= chunkSpan; dx++) {
            for (int dz = -chunkSpan; dz <= chunkSpan; dz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    /*
                     * No emptiness check. A client's copy of a chest has no contents in it - the
                     * server only sends them while it is open - so isEmpty() is true for every
                     * container in the world here, and filtering on it hid all of them. That was
                     * the bug that made this skill appear to do nothing at all.
                     */
                    if (!(be instanceof Container)) {
                        continue;
                    }
                    BlockPos pos = be.getBlockPos();
                    if (pos.distSqr(origin) <= rangeSq) {
                        FOUND.add(pos.immutable());
                    }
                }
            }
        }
    }

    /** Dropped with everything else on disconnect - the next world's chests are not these. */
    public static void clear() {
        FOUND.clear();
        scannedAt = Long.MIN_VALUE;
    }
}
