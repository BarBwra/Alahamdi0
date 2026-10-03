package com.barbwra.mlum.client.loot;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.ClientSkills;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.client.hud.HudVisibility;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.loot.LootRules;
import com.barbwra.mlum.skill.SkillEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Marks on the containers around you - and, for the scout skill, the ones behind the walls.
 *
 * <pre>
 *     seen            looked at, in reach        scout, through a wall
 *   ┌       ┐          ┏━        ━┓               ┌ ─     ─ ┐
 *      [m]               [m] ← right button green
 *   └       ┘          ┗━        ━┛               └ ─     ─ ┘
 * </pre>
 *
 * <p>Every container that is actually in view - on screen <i>and</i> with nothing solid between
 * your eyes and it - gets corner marks and a small pixel mouse showing the right button. Looking
 * straight at one within reach thickens the marks into the HUD colour and lights the right button
 * green. One you have already searched this session is drawn grey. A tall fridge or a double chest
 * is one container with one set of marks around both halves.</p>
 *
 * <p>The scout skill adds the containers you cannot see, within 8, 12 or 16 blocks, as dashed amber
 * marks that breathe slowly, with no mouse - there is nothing to click through a wall. At level 3
 * the server also says which of them are empty, and those are drawn grey.</p>
 *
 * <p>Found by chunk, re-scanned four times a second; the expensive part, the line of sight, is a
 * few rays per container on that scan and not per frame.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class LootMarkers {

    private LootMarkers() {
    }

    private static final int[] SCOUT_RANGE = {8, 12, 16};
    private static final int SCAN_EVERY = 5;

    private static final int BONE = 0xFFECE6D4;
    private static final int FAINT = 0xFF6B6A5C;
    private static final int AMBER = 0xFFF0A93B;

    private record Target(long key, BlockPos pos, BlockPos partner, AABB box, boolean visible, double dist) {
    }

    private static final List<Target> TARGETS = new ArrayList<>();
    private static final Set<Long> SEARCHED = new HashSet<>();
    private static final HudPen PEN = new HudPen();
    private static int scanTick;

    public static void clear() {
        TARGETS.clear();
        SEARCHED.clear();
        ClientScoutInfo.clear();
        WorldProjector.clear();
    }

    /** Called when a search finishes: the container is drawn grey from now on. */
    public static void markSearched(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        SEARCHED.add(LootRules.key(pos, LootRules.partner(mc.level, pos, mc.level.getBlockState(pos))));
    }

    static int scoutRange() {
        int level = ClientSkills.levelOf(SkillEffects.SCOUT);
        return level <= 0 ? 0 : SCOUT_RANGE[Math.min(SCOUT_RANGE.length, level) - 1];
    }

    /* ================================================================== the scan */

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            TARGETS.clear();
            return;
        }
        if (++scanTick % SCAN_EVERY != 0) {
            return;
        }
        scan(mc.level, player);
    }

    private static void scan(Level level, LocalPlayer player) {
        TARGETS.clear();
        int sight = MlumConfig.lootMarkers() ? MlumConfig.lootMarkerRange() : 0;
        int scout = scoutRange();
        int radius = Math.max(sight, scout);
        if (radius <= 0) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        BlockPos origin = player.blockPosition();
        int span = (radius >> 4) + 1;
        int cx = origin.getX() >> 4;
        int cz = origin.getZ() >> 4;
        Set<Long> seen = new HashSet<>();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos pos = be.getBlockPos();
                    double dist = Math.sqrt(pos.distToCenterSqr(eye));
                    if (dist > radius + 1.0D || !LootRules.isLootable(be)) {
                        continue;
                    }
                    BlockState state = be.getBlockState();
                    BlockPos partner = LootRules.partner(level, pos, state);
                    long key = LootRules.key(pos, partner);
                    if (!seen.add(key)) {
                        continue;
                    }
                    AABB box = boxOf(level, pos, state);
                    if (partner != null) {
                        box = box.minmax(boxOf(level, partner, level.getBlockState(partner)));
                    }
                    boolean visible = dist <= sight && inSight(level, player, eye, box, pos, partner);
                    boolean scouted = scout > 0 && dist <= scout;
                    if (visible || scouted) {
                        TARGETS.add(new Target(key, pos.immutable(), partner, box, visible, dist));
                    }
                }
            }
        }
    }

    private static AABB boxOf(Level level, BlockPos pos, BlockState state) {
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
        return box.move(pos);
    }

    /** Nothing solid between the eyes and the container: a ray to its centre, its top and its near face. */
    private static boolean inSight(Level level, LocalPlayer player, Vec3 eye, AABB box, BlockPos pos, BlockPos partner) {
        Vec3 centre = box.getCenter();
        Vec3 top = new Vec3(centre.x, box.maxY - 0.08D, centre.z);
        Vec3 near = new Vec3(Mth.clamp(eye.x, box.minX + 0.05D, box.maxX - 0.05D),
                centre.y, Mth.clamp(eye.z, box.minZ + 0.05D, box.maxZ - 0.05D));
        for (Vec3 to : new Vec3[]{centre, top, near}) {
            BlockHitResult hit = level.clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.MISS) {
                return true;
            }
            BlockPos at = hit.getBlockPos();
            if (at.equals(pos) || at.equals(partner)) {
                return true;
            }
        }
        return false;
    }

    /* ================================================================== drawing */

    public static void render(GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (TARGETS.isEmpty() || HudVisibility.hidden() || mc.screen != null || !WorldProjector.ready()
                || com.barbwra.mlum.client.downed.ClientDowned.selfDowned()) {
            return;
        }
        long now = Anim.now();
        BlockPos focus = null;
        if (mc.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            focus = bhr.getBlockPos();
        }
        BlockPos searching = ClientLootSearch.activePos();
        int accent = MlumConfig.fieldHudAccent();
        int sight = MlumConfig.lootMarkerRange();
        int scout = scoutRange();

        HudPen pen = PEN;
        pen.begin(graphics);
        try {
            float[] p = new float[2];
            for (Target t : TARGETS) {
                float[] rect = screenRect(t.box(), width, height, p);
                if (rect == null) {
                    continue;
                }
                boolean focused = t.visible() && focus != null && (focus.equals(t.pos()) || focus.equals(t.partner()));
                boolean active = searching != null && (searching.equals(t.pos()) || searching.equals(t.partner()));
                if (t.visible()) {
                    float fade = fade(t.dist(), sight);
                    boolean done = SEARCHED.contains(t.key());
                    if (focused || active) {
                        brackets(pen, rect, accent, 1.0F, true, false);
                    } else {
                        brackets(pen, rect, done ? FAINT : BONE, (done ? 0.5F : 0.6F) * fade, false, false);
                    }
                    if (!active) {
                        mouse(pen, rect, focused, done ? 0.4F * fade : (focused ? 1.0F : 0.85F * fade), height);
                    }
                } else {
                    Boolean empty = ClientScoutInfo.isEmpty(t.key());
                    float breathe = Anim.enabled() ? 0.6F + 0.25F * Mth.sin(now / 380.0F) : 0.8F;
                    int colour = Boolean.TRUE.equals(empty) ? FAINT : AMBER;
                    brackets(pen, rect, colour, breathe * fade(t.dist(), scout), false, true);
                }
            }
        } finally {
            pen.end();
        }
    }

    private static float fade(double dist, int range) {
        if (range <= 0) {
            return 1.0F;
        }
        return 1.0F - Mth.clamp((float) ((dist - range * 0.7D) / (range * 0.3D)), 0.0F, 0.85F);
    }

    /** The box's eight corners projected; their bounds on screen, or null when any is behind the camera. */
    private static float[] screenRect(AABB b, int width, int height, float[] p) {
        float x0 = Float.MAX_VALUE;
        float y0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE;
        float y1 = -Float.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? b.minX : b.maxX;
            double y = (i & 2) == 0 ? b.minY : b.maxY;
            double z = (i & 4) == 0 ? b.minZ : b.maxZ;
            if (!WorldProjector.project(x, y, z, width, height, p)) {
                return null;
            }
            x0 = Math.min(x0, p[0]);
            y0 = Math.min(y0, p[1]);
            x1 = Math.max(x1, p[0]);
            y1 = Math.max(y1, p[1]);
        }
        if (x1 < 0 || y1 < 0 || x0 > width || y0 > height || x1 - x0 < 2 || y1 - y0 < 2) {
            return null;
        }
        return new float[]{x0, y0, x1, y1};
    }

    private static int alpha(int argb, float a) {
        int na = Mth.clamp(Math.round((argb >>> 24) * a), 0, 255);
        return (na << 24) | (argb & 0xFFFFFF);
    }

    private static void brackets(HudPen pen, float[] r, int colour, float a, boolean thick, boolean dashed) {
        float w = r[2] - r[0];
        float h = r[3] - r[1];
        float arm = Mth.clamp(Math.min(w, h) * 0.28F, 2.5F, thick ? 10.0F : 8.0F);
        float t = Math.max(1.0F, Math.round(pen.u * (thick ? 0.75F : 0.5F))) / pen.u;
        int c = alpha(colour, a);
        float[][] corners = {{r[0], r[1], 1, 1}, {r[2], r[1], -1, 1}, {r[0], r[3], 1, -1}, {r[2], r[3], -1, -1}};
        for (float[] k : corners) {
            float x = k[0];
            float y = k[1];
            float sx = k[2];
            float sy = k[3];
            arm(pen, sx > 0 ? x : x - arm, sy > 0 ? y : y - t, arm, t, c, dashed, true);
            arm(pen, sx > 0 ? x : x - t, sy > 0 ? y : y - arm, t, arm, c, dashed, false);
        }
    }

    private static void arm(HudPen pen, float x, float y, float w, float h, int c, boolean dashed, boolean across) {
        if (!dashed) {
            pen.rect(x, y, w, h, c);
            return;
        }
        float len = across ? w : h;
        for (float o = 0; o < len; o += 2.0F) {
            float seg = Math.min(1.2F, len - o);
            if (across) {
                pen.rect(x + o, y, seg, h, c);
            } else {
                pen.rect(x, y + o, w, seg, c);
            }
        }
    }

    /**
     * The mouse: a rounded body, the two buttons split by the wheel, the right button green.
     *
     * <p>Fifteen by twenty-two cells, each cell a whole number of framebuffer pixels so the edges
     * stay crisp, sized from the screen height - about a twentieth of it, a quarter more when you
     * are looking straight at the container. It sits on a soft drop shadow so it reads on a white
     * wall as well as in a dark cellar. Looked at, the right button lights up and clicks every so
     * often - the hint is which button to press, so that button is the one that moves.</p>
     */
    private static final String[] MOUSE = {
            "     #####     ",
            "   ##LL|RR##   ",
            "  #LLLLwRRRR#  ",
            " #LLLLLwRRRRR# ",
            " #LLLLLwRRRRR# ",
            "#LLLLLLwRRRRRR#",
            "#LLLLLLwRRRRRR#",
            "#LLLLLL|RRRRRR#",
            "#LLLLLL|RRRRRR#",
            "#-------------#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            "#.............#",
            " #...........# ",
            "  #.........#  ",
            "   #########   ",
    };
    private static final int MOUSE_SPLIT = 9;

    private static void mouse(HudPen pen, float[] r, boolean focused, float a, int screenHeight) {
        int cellPx = Math.max(1, Math.round(screenHeight * pen.u * (focused ? 0.062F : 0.048F) / MOUSE.length));
        float cell = cellPx / pen.u;
        float w = MOUSE[0].length() * cell;
        float h = MOUSE.length * cell;
        float x = Math.round(((r[0] + r[2]) / 2.0F - w / 2.0F) * pen.u) / pen.u;
        float y = Math.round(((r[1] + r[3]) / 2.0F - h / 2.0F) * pen.u) / pen.u;
        long now = Anim.now();
        // a click every 1.4 s while looked at: the button dips for a moment
        boolean pressed = focused && Anim.enabled() && now % 1400L < 140L;
        int green = focused ? (pressed ? 0xFF4E9E36 : 0xFF76D654) : 0xFF5C9648;
        int greenHi = focused ? (pressed ? 0xFF5FB244 : 0xFF97EE77) : 0xFF73B05C;
        int outline = alpha(focused ? 0xFFFAF6EC : 0xFFECE6D4, (focused ? 1.0F : 0.9F) * a);
        int seam = alpha(0xFFECE6D4, 0.55F * a);
        int left = alpha(0xFF343A36, 0.92F * a);
        int wheel = alpha(0xFFD8D3C2, a);
        int shadow = alpha(0xFF000000, 0.35F * a);
        if (focused) {
            // a faint green halo round the right half, the part that matters
            pen.rect(x + w * 0.5F, y - cell, w * 0.5F + cell, h * 0.45F + cell, alpha(0xFF76D654, 0.10F * a));
        }
        for (int row = 0; row < MOUSE.length; row++) {
            String line = MOUSE[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != ' ') {
                    pen.rect(x + (col + 1) * cell, y + (row + 1) * cell, cell, cell, shadow);
                }
            }
        }
        for (int row = 0; row < MOUSE.length; row++) {
            String line = MOUSE[row];
            for (int col = 0; col < line.length(); col++) {
                int c = switch (line.charAt(col)) {
                    case '#' -> outline;
                    case '-', '|' -> seam;
                    case 'w' -> row == 4 ? alpha(0xFF78746A, a) : wheel;
                    case 'L' -> left;
                    case 'R' -> alpha(row <= 1 || (row == 2 && col > 9) ? greenHi : green, (focused ? 0.97F : 0.82F) * a);
                    case '.' -> {
                        // the body darkens towards the palm
                        float t = (row - MOUSE_SPLIT) / (float) (MOUSE.length - MOUSE_SPLIT);
                        int v = Math.round(34 - 10 * t);
                        yield alpha(0xFF000000 | (v << 16) | ((v + 5) << 8) | (v + 2), 0.9F * a);
                    }
                    default -> 0;
                };
                if (c != 0) {
                    pen.rect(x + col * cell, y + row * cell, cell, cell, c);
                }
            }
        }
    }
}
