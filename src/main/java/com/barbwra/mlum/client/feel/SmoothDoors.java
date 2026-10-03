package com.barbwra.mlum.client.feel;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Doors and trapdoors swing instead of snapping.
 *
 * <p><b>How, without touching the game's code.</b> Nearby doors are remembered with whether they are
 * open. The frame one changes, the real block is swapped for air on this client only, and the
 * door's closed model is drawn turning about its hinge from where it was to where it now is. A
 * quarter of a second later the real block is put back. The server never hears of any of it.</p>
 *
 * <p>The hinge is not looked up per door type: it is where the closed shape and the open shape
 * overlap, which is true of every door and trapdoor, modded ones included, as long as they open
 * the vanilla way. Anything else is left alone.</p>
 *
 * <p><b>If the world disagrees.</b> While a door is mid-swing the server may resend its state (the
 * reply to your own click does exactly this). If what arrives is the state being swung to, the air
 * goes back in place and the swing carries on; anything else ends the swing on the spot and the
 * block is left as the server says.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class SmoothDoors {

    private SmoothDoors() {
    }

    private static final int RADIUS = 12;
    private static final int HEIGHT = 6;
    private static final long SWING_MS = 230L;
    /** Re-render this frame on the main thread, and do not poke the neighbours. */
    private static final int FLAGS = 2 | 8 | 16;

    /** Known door positions (the lower half of a door) and whether each was open last we looked. */
    private static final Map<BlockPos, Boolean> KNOWN = new HashMap<>();
    private static final List<Swing> SWINGS = new ArrayList<>();
    private static ClientLevel knownLevel;
    private static int scanTick;

    private static final class Swing {
        final BlockPos pos;
        /** What goes back when the swing ends: the block, and for a door its upper half. */
        final BlockState target;
        final BlockState upper;
        /** Drawn turning: the closed model of each half. */
        final BlockState modelLower;
        final BlockState modelUpper;
        final char axis;
        final double px;
        final double py;
        final double pz;
        final float fromDeg;
        final float toDeg;
        final long start;

        Swing(BlockPos pos, BlockState target, BlockState upper, BlockState modelLower, BlockState modelUpper, char axis,
              double px, double py, double pz, float fromDeg, float toDeg) {
            this.pos = pos;
            this.target = target;
            this.upper = upper;
            this.modelLower = modelLower;
            this.modelUpper = modelUpper;
            this.axis = axis;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.fromDeg = fromDeg;
            this.toDeg = toDeg;
            this.start = System.currentTimeMillis();
        }
    }

    private static boolean swings(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
        }
        // a flooded trapdoor would take its water with it for the length of the swing
        return state.getBlock() instanceof TrapDoorBlock && !state.getValue(BlockStateProperties.WATERLOGGED);
    }

    /* ------------------------------------------------------------------ finding the doors */

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != knownLevel) {
            KNOWN.clear();
            SWINGS.clear();
            knownLevel = mc.level;
        }
        if (mc.level == null || mc.player == null || !MlumConfig.smoothDoors()) {
            return;
        }
        if (++scanTick % 20 != 0) {
            return;
        }
        BlockPos centre = mc.player.blockPosition();
        // forget doors left behind, then pick up any that came into range
        KNOWN.keySet().removeIf(p -> Math.abs(p.getX() - centre.getX()) > RADIUS + 4 || Math.abs(p.getZ() - centre.getZ()) > RADIUS + 4
                || Math.abs(p.getY() - centre.getY()) > HEIGHT + 4);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
                    at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    BlockState state = mc.level.getBlockState(at);
                    if (swings(state) && !KNOWN.containsKey(at)) {
                        KNOWN.put(at.immutable(), state.getValue(BlockStateProperties.OPEN));
                    }
                }
            }
        }
    }

    /** Every frame: has a known door changed, and is any swing over or overruled. */
    private static void check(ClientLevel level) {
        Iterator<Map.Entry<BlockPos, Boolean>> it = KNOWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Boolean> e = it.next();
            BlockPos pos = e.getKey();
            if (swinging(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!swings(state)) {
                it.remove();
                continue;
            }
            boolean open = state.getValue(BlockStateProperties.OPEN);
            if (open != e.getValue()) {
                e.setValue(open);
                start(level, pos, state, open);
            }
        }
        long now = System.currentTimeMillis();
        Iterator<Swing> sw = SWINGS.iterator();
        while (sw.hasNext()) {
            Swing s = sw.next();
            BlockState here = level.getBlockState(s.pos);
            boolean done = now - s.start >= SWING_MS;
            if (!here.isAir()) {
                if (here == s.target && !done) {
                    // the server confirming what is already being shown: keep it hidden
                    level.setBlock(s.pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    if (s.upper != null && level.getBlockState(s.pos.above()) == s.upper) {
                        level.setBlock(s.pos.above(), Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                    continue;
                }
                // overruled: the block is whatever the server says it is now
                restoreUpper(level, s);
                sw.remove();
                continue;
            }
            if (!done && s.upper != null && level.getBlockState(s.pos.above()) == s.upper) {
                level.setBlock(s.pos.above(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            if (done) {
                level.setBlock(s.pos, s.target, FLAGS);
                restoreUpper(level, s);
                sw.remove();
            }
        }
    }

    private static void restoreUpper(ClientLevel level, Swing s) {
        if (s.upper != null && level.getBlockState(s.pos.above()).isAir()) {
            level.setBlock(s.pos.above(), s.upper, FLAGS);
        }
    }

    private static boolean swinging(BlockPos pos) {
        for (Swing s : SWINGS) {
            if (s.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ the hinge */

    private static void start(ClientLevel level, BlockPos pos, BlockState now, boolean opening) {
        BlockState closed = now.setValue(BlockStateProperties.OPEN, false);
        BlockState open = now.setValue(BlockStateProperties.OPEN, true);
        AABB c = bounds(closed.getShape(level, pos));
        AABB o = bounds(open.getShape(level, pos));
        if (c == null || o == null) {
            return;
        }
        AABB hinge = c.intersect(o);
        double ix = hinge.getXsize();
        double iy = hinge.getYsize();
        double iz = hinge.getZsize();
        if (ix < 0 || iy < 0 || iz < 0) {
            return;
        }
        // the hinge runs along whichever side of the overlap is longest
        char axis = iy >= ix && iy >= iz ? 'y' : (ix >= iz ? 'x' : 'z');
        Vec3 p = hinge.getCenter();
        Vec3 cc = c.getCenter().subtract(p);
        Vec3 oc = o.getCenter().subtract(p);
        float sign = distance(rotate(cc, axis, 90.0F), oc) <= distance(rotate(cc, axis, -90.0F), oc) ? 1.0F : -1.0F;
        float openDeg = 90.0F * sign;

        BlockState upper = null;
        BlockState upperModel = null;
        if (now.getBlock() instanceof DoorBlock) {
            BlockState above = level.getBlockState(pos.above());
            if (above.getBlock() == now.getBlock()) {
                upper = above;
                upperModel = above.setValue(BlockStateProperties.OPEN, false);
            }
        }
        SWINGS.add(new Swing(pos, now, upper, closed, upperModel, axis, p.x, p.y, p.z,
                opening ? 0.0F : openDeg, opening ? openDeg : 0.0F));
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        if (upper != null) {
            level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static AABB bounds(VoxelShape shape) {
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static double distance(Vec3 a, Vec3 b) {
        return a.distanceToSqr(b);
    }

    /** The same turn PoseStack applies for Axis.XP/YP/ZP, so the sign picked here is the sign drawn. */
    private static Vec3 rotate(Vec3 v, char axis, float deg) {
        double r = Math.toRadians(deg);
        double cos = Math.cos(r);
        double sin = Math.sin(r);
        return switch (axis) {
            case 'x' -> new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
            case 'z' -> new Vec3(v.x * cos - v.y * sin, v.x * sin + v.y * cos, v.z);
            default -> new Vec3(v.x * cos + v.z * sin, v.y, -v.x * sin + v.z * cos);
        };
    }

    /* ------------------------------------------------------------------ drawing */

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (MlumConfig.smoothDoors()) {
                check(level);
            } else if (!SWINGS.isEmpty()) {
                finishAll(level);
            }
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || SWINGS.isEmpty()) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        long now = System.currentTimeMillis();
        for (Swing s : SWINGS) {
            float t = Math.min(1.0F, (now - s.start) / (float) SWING_MS);
            float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
            float deg = s.fromDeg + (s.toDeg - s.fromDeg) * e;
            pose.pushPose();
            pose.translate(s.pos.getX() - cam.x, s.pos.getY() - cam.y, s.pos.getZ() - cam.z);
            pose.translate(s.px, s.py, s.pz);
            pose.mulPose(switch (s.axis) {
                case 'x' -> Axis.XP.rotationDegrees(deg);
                case 'z' -> Axis.ZP.rotationDegrees(deg);
                default -> Axis.YP.rotationDegrees(deg);
            });
            pose.translate(-s.px, -s.py, -s.pz);
            int light = LevelRenderer.getLightColor(level, s.pos);
            mc.getBlockRenderer().renderSingleBlock(s.modelLower, pose, buffers, light, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, null);
            if (s.modelUpper != null) {
                pose.translate(0.0D, 1.0D, 0.0D);
                mc.getBlockRenderer().renderSingleBlock(s.modelUpper, pose, buffers, LevelRenderer.getLightColor(level, s.pos.above()),
                        OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void finishAll(ClientLevel level) {
        for (Swing s : SWINGS) {
            if (level.getBlockState(s.pos).isAir()) {
                level.setBlock(s.pos, s.target, FLAGS);
            }
            restoreUpper(level, s);
        }
        SWINGS.clear();
    }
}
