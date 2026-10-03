package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.client.gui.ArtSize;
import com.barbwra.mlum.client.hud.TaczGunHud;
import com.barbwra.mlum.client.ui.Canvas;
import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.text.Shaped;
import com.barbwra.mlum.client.ui.text.TextEngine;
import com.barbwra.mlum.util.ArabicText;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The game's {@link Canvas}: everything the views paint, in framebuffer pixels.
 *
 * <p><b>One batch, one draw call, most frames.</b> Text, sprites, glows and plain rectangles all go
 * into a single position-texture-colour buffer against the atlas page - a rectangle samples the
 * page's white block and takes its colour from the vertices - so a whole screen is a few draws
 * rather than one per string. The batch is flushed only when it has to be: a different texture, a
 * clip change, or a game item or entity that Minecraft draws itself.</p>
 *
 * <p><b>Pixels are the framebuffer's.</b> The pose is scaled by 1/guiScale for the frame, so every
 * coordinate a view hands over lands on a real pixel - which is what keeps 1px lines at 1px and text
 * exactly as sharp as it was rasterised, at any GUI scale.</p>
 *
 * <p>Depth testing is off for everything this batches, so painter's order holds: whatever is drawn
 * later is on top, including over game items. Items still write depth, which is why the carried
 * item is pushed nearer with {@link #pushZ}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class McCanvas implements Canvas {

    private final BufferBuilder buf = new BufferBuilder(0x20000);
    private GuiGraphics g;
    private boolean building;
    private int batchTexture = -1;
    private Atlas.Page batchPage;

    private float m00, m01, m10, m11, m22, m30, m31, m32;

    private float alpha = 1.0F;
    private final float[] alphas = new float[64];
    private int alphaDepth;

    private int ox;
    private int oy;

    private final int[] clips = new int[4 * 64];
    private int clipDepth;

    private float z;
    private final float[] zs = new float[32];
    private int zDepth;

    private int fbW;
    private int fbH;
    private int mouseX;
    private int mouseY;
    private boolean frameOpen;

    /* ================================================================== frame */

    /** Starts a frame on this GuiGraphics. Every draw after this is in framebuffer pixels. */
    public void begin(GuiGraphics graphics, int mouseDeviceX, int mouseDeviceY) {
        this.g = graphics;
        Minecraft mc = Minecraft.getInstance();
        this.fbW = mc.getWindow().getWidth();
        this.fbH = mc.getWindow().getHeight();
        this.mouseX = mouseDeviceX;
        this.mouseY = mouseDeviceY;
        float inv = (float) (1.0D / mc.getWindow().getGuiScale());
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // uniform, so game items drawn inside keep their normals (and their shading) intact
        pose.scale(inv, inv, inv);
        Matrix4f m = pose.last().pose();
        m00 = m.m00();
        m01 = m.m01();
        m10 = m.m10();
        m11 = m.m11();
        m22 = m.m22();
        m30 = m.m30();
        m31 = m.m31();
        m32 = m.m32();
        alpha = 1.0F;
        alphaDepth = 0;
        ox = 0;
        oy = 0;
        clipDepth = 0;
        z = 0.0F;
        zDepth = 0;
        batchTexture = -1;
        batchPage = null;
        frameOpen = true;
        Atlas.beforeReset = page -> {
            if (page == batchPage) {
                flush();
            }
        };
    }

    /** Draws what is left and puts the render state back the way the game expects it. */
    public void end() {
        if (!frameOpen) {
            return;
        }
        flush();
        if (clipDepth > 0) {
            RenderSystem.disableScissor();
            clipDepth = 0;
        }
        g.pose().popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        Atlas.beforeReset = null;
        frameOpen = false;
        g = null;
    }

    /* ================================================================== batching */

    private void use(int texture, Atlas.Page page) {
        if (building && texture == batchTexture) {
            return;
        }
        flush();
        batchTexture = texture;
        batchPage = page;
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        building = true;
    }

    private void flush() {
        if (!building) {
            return;
        }
        building = false;
        if (batchPage != null) {
            Atlas.upload(batchPage);
        }
        BufferBuilder.RenderedBuffer rendered = buf.end();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, batchTexture);
        BufferUploader.drawWithShader(rendered);
    }

    private void vertex(float x, float y, float u, float v, int argb) {
        double px = m00 * x + m10 * y + m30;
        double py = m01 * x + m11 * y + m31;
        buf.vertex(px, py, m22 * z + m32).uv(u, v)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF).endVertex();
    }

    /** Four corners, top-left / bottom-left / bottom-right / top-right, as the game winds its GUI quads. */
    private void quad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1,
                      int tl, int tr, int br, int bl) {
        vertex(x0, y0, u0, v0, tl);
        vertex(x0, y1, u0, v1, bl);
        vertex(x1, y1, u1, v1, br);
        vertex(x1, y0, u1, v0, tr);
    }

    private int fade(int argb) {
        if (alpha >= 0.999F) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private Atlas.Page whitePage() {
        return batchPage != null ? batchPage : Atlas.anyPage();
    }

    /* ================================================================== Canvas */

    @Override
    public void fill(int x0, int y0, int x1, int y1, int argb) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        int c = fade(argb);
        if ((c >>> 24) == 0) {
            return;
        }
        Atlas.Page p = whitePage();
        use(Atlas.textureId(p), p);
        quad(x0 + ox, y0 + oy, x1 + ox, y1 + oy, p.whiteU, p.whiteV, p.whiteU, p.whiteV, c, c, c, c);
    }

    @Override
    public void gradient(int x0, int y0, int x1, int y1, int tl, int tr, int br, int bl) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        Atlas.Page p = whitePage();
        use(Atlas.textureId(p), p);
        quad(x0 + ox, y0 + oy, x1 + ox, y1 + oy, p.whiteU, p.whiteV, p.whiteU, p.whiteV,
                fade(tl), fade(tr), fade(br), fade(bl));
    }

    @Override
    public void raster(Raster r, float u0, float v0, float u1, float v1, int x0, int y0, int x1, int y1, int tint) {
        if (r == null || r.isEmpty() || x1 <= x0 || y1 <= y0) {
            return;
        }
        int c = fade(tint);
        if ((c >>> 24) == 0) {
            return;
        }
        r.lastUsed = TextEngine.now();
        Atlas.Slot s = Atlas.slotOf(r);
        if (s == null) {
            return;
        }
        use(Atlas.textureId(s), s.page);
        float tw = Atlas.textureWidth(s);
        float th = Atlas.textureHeight(s);
        float su0 = (s.x + u0 * s.w) / tw;
        float sv0 = (s.y + v0 * s.h) / th;
        float su1 = (s.x + u1 * s.w) / tw;
        float sv1 = (s.y + v1 * s.h) / th;
        quad(x0 + ox, y0 + oy, x1 + ox, y1 + oy, su0, sv0, su1, sv1, c, c, c, c);
    }

    @Override
    public void item(Object item, int x0, int y0, int x1, int y1, int tint) {
        if (item instanceof ItemStack stack) {
            drawStack(stack, x0, y0, x1, y1, tint);
        } else if (item instanceof ResourceLocation texture) {
            texture(texture, x0, y0, x1, y1, tint, true);
        } else if (item instanceof McImage image) {
            image(image, x0, y0, x1, y1, tint);
        } else if (item instanceof Raster r) {
            containRaster(r, x0, y0, x1, y1, tint);
        }
    }

    private void drawStack(ItemStack stack, int x0, int y0, int x1, int y1, int tint) {
        if (stack == null || stack.isEmpty() || g == null) {
            return;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        // a firearm in a wide box shows its flat artwork, the way the design shows the rifle
        if (w > h * 1.3F) {
            ResourceLocation art = TaczGunHud.hudTexture(stack, false);
            if (art != null) {
                texture(art, x0, y0, x1, y1, tint, true);
                return;
            }
        }
        // whole-number scales only: a 16px icon at 3x is crisp, at 2.6x it is mush
        int n = Math.max(1, Math.round(Math.min(w, h) / 16.0F));
        int size = 16 * n;
        int x = x0 + ox + (w - size) / 2;
        int y = y0 + oy + (h - size) / 2;
        flush();
        PoseStack pose = g.pose();
        PoseStack.Pose top = pose.last();
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(n, n, n);
        int c = fade(tint);
        boolean tinted = c != 0xFFFFFFFF;
        if (tinted) {
            RenderSystem.setShaderColor(((c >> 16) & 0xFF) / 255.0F, ((c >> 8) & 0xFF) / 255.0F,
                    (c & 0xFF) / 255.0F, ((c >>> 24) & 0xFF) / 255.0F);
        }
        try {
            g.renderItem(stack, 0, 0);
        } catch (Throwable broken) {
            // another mod's item renderer is not allowed to take the whole screen down with it
        } finally {
            if (tinted) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            unwind(pose, top);
        }
    }

    /**
     * Pops back to {@code top}. On success that is the one push above; when a renderer throws half
     * way, vanilla's own pushes are still on the stack too (it pops them only on the way out of a
     * normal return), and leaving them would skew everything drawn after it.
     */
    private static void unwind(PoseStack pose, PoseStack.Pose top) {
        for (int guard = 0; guard < 64 && pose.last() != top && !pose.clear(); guard++) {
            pose.popPose();
        }
    }

    @Override
    public void image(Object image, int x0, int y0, int x1, int y1, int tint) {
        if (image instanceof McImage mi) {
            if (mi.texture != null) {
                texture(mi.texture, x0, y0, x1, y1, tint, true);
            } else if (mi.shape != null) {
                containRaster(mi.shape, x0, y0, x1, y1, mi.tint);
            }
        } else if (image instanceof ResourceLocation texture) {
            texture(texture, x0, y0, x1, y1, tint, true);
        } else if (image instanceof Raster r) {
            containRaster(r, x0, y0, x1, y1, tint);
        } else if (image instanceof ItemStack stack) {
            drawStack(stack, x0, y0, x1, y1, tint);
        }
    }

    /** Part of a texture - {@code u0..v1} in 0..1 of the whole image - stretched to the box. */
    public void region(ResourceLocation location, int x0, int y0, int x1, int y1,
                       float u0, float v0, float u1, float v1, int tint) {
        if (x1 <= x0 || y1 <= y0) {
            return;
        }
        AbstractTexture texture;
        try {
            texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        } catch (Throwable missing) {
            return;
        }
        int c = fade(tint);
        if (texture == null || (c >>> 24) == 0) {
            return;
        }
        use(texture.getId(), null);
        quad(x0 + ox, y0 + oy, x1 + ox, y1 + oy, u0, v0, u1, v1, c, c, c, c);
    }

    /** A texture file, contained in the box at its own proportions, through the same batch. */
    private void texture(ResourceLocation location, int x0, int y0, int x1, int y1, int tint, boolean contain) {
        AbstractTexture texture;
        try {
            texture = Minecraft.getInstance().getTextureManager().getTexture(location);
        } catch (Throwable missing) {
            return;
        }
        if (texture == null) {
            return;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        float dx0 = x0;
        float dy0 = y0;
        float dw = w;
        float dh = h;
        if (contain) {
            float aspect = ArtSize.aspect(location);
            if (aspect > 0.0F) {
                if (w / (float) h > aspect) {
                    dw = h * aspect;
                } else {
                    dh = w / aspect;
                }
                dx0 = x0 + (w - dw) / 2.0F;
                dy0 = y0 + (h - dh) / 2.0F;
            }
        }
        int c = fade(tint);
        if ((c >>> 24) == 0) {
            return;
        }
        use(texture.getId(), null);
        float ax = Math.round(dx0) + ox;
        float ay = Math.round(dy0) + oy;
        quad(ax, ay, ax + Math.round(dw), ay + Math.round(dh), 0.0F, 0.0F, 1.0F, 1.0F, c, c, c, c);
    }

    /** {@code background-size:contain} for a raster (a vehicle silhouette), whole pixels. */
    private void containRaster(Raster r, int x0, int y0, int x1, int y1, int tint) {
        if (r == null || r.isEmpty()) {
            return;
        }
        int w = x1 - x0;
        int h = y1 - y0;
        float k = Math.min(w / (float) r.width, h / (float) r.height);
        int dw = Math.round(r.width * k);
        int dh = Math.round(r.height * k);
        int ax = x0 + (w - dw) / 2;
        int ay = y0 + (h - dh) / 2;
        raster(r, 0.0F, 0.0F, 1.0F, 1.0F, ax, ay, ax + dw, ay + dh, tint);
    }

    /**
     * The player standing in the box and turning toward the cursor - the vanilla inventory's own
     * maths, measured in design pixels so it feels the same at every window size.
     */
    @Override
    public void player(int x0, int y0, int x1, int y1, int unusedX, int unusedY) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity entity = mc.player;
        if (entity == null || g == null) {
            return;
        }
        flush();
        int bx0 = x0 + ox;
        int by0 = y0 + oy;
        int bx1 = x1 + ox;
        int by1 = y1 + oy;
        int cx = (bx0 + bx1) / 2;
        int feet = by1 - Math.round((by1 - by0) * 0.012F);
        // a player is 1.8 blocks tall and the box is 288 design px: fill it the way the design does
        int scale = Math.max(8, Math.round((by1 - by0) / 1.92F));
        float cssPerPx = 1.0F / Math.max(0.01F, com.barbwra.mlum.client.ui.Px.s);
        float dx = (cx - mouseX) * cssPerPx;
        float dy = (by0 + (by1 - by0) * 0.25F - mouseY) * cssPerPx;
        float yaw = (float) Math.atan(dx / 80.0F) * 40.0F;
        float pitch = Math.max(-25.0F, Math.min(25.0F, (float) Math.atan(dy / 80.0F) * 20.0F));

        pushClip(x0 - Math.round((x1 - x0) * 0.1F), y0 - 8, x1 + Math.round((x1 - x0) * 0.1F), y1 + 8);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * ((float) Math.PI / 180.0F));
        pose.mul(camera);
        float oldBody = entity.yBodyRot;
        float oldY = entity.getYRot();
        float oldX = entity.getXRot();
        float oldHeadO = entity.yHeadRotO;
        float oldHead = entity.yHeadRot;
        PoseStack poses = g.pose();
        PoseStack.Pose top = poses.last();
        OptionInstance<GraphicsStatus> graphics = mc.options.graphicsMode();
        GraphicsStatus graphicsBefore = graphics.get();
        try {
            entity.yBodyRot = 180.0F + yaw * 0.5F;
            entity.setYRot(180.0F + yaw);
            entity.setXRot(-pitch);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
            poses.pushPose();
            poses.translate(0.0F, 0.0F, z);
            com.barbwra.mlum.client.downed.DownedClientEvents.portrait = true;
            InventoryScreen.renderEntityInInventory(g, cx, feet, scale, pose, camera, entity);
        } catch (Throwable broken) {
            // A cosmetic or armour mod failing to render must not take the menu with it - nor leave
            // behind what vanilla switched off for the portrait and only restores on success.
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
            if (graphics.get() != graphicsBefore) {
                graphics.set(graphicsBefore);
            }
        } finally {
            com.barbwra.mlum.client.downed.DownedClientEvents.portrait = false;
            unwind(poses, top);
            entity.yBodyRot = oldBody;
            entity.setYRot(oldY);
            entity.setXRot(oldX);
            entity.yHeadRotO = oldHeadO;
            entity.yHeadRot = oldHead;
        }
        popClip();
    }

    /**
     * Someone else standing in the box, turned a little to the side - the body being looted.
     *
     * <p>A downed player lies flat in the world and the practice body is in the sleeping pose; here
     * both are stood up for the length of the draw, so their gear reads the way your own does.</p>
     */
    @Override
    public void body(int entityId, int x0, int y0, int x1, int y1) {
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc.level == null || !(mc.level.getEntity(entityId) instanceof LivingEntity entity)) {
            return;
        }
        flush();
        int bx0 = x0 + ox;
        int by0 = y0 + oy;
        int bx1 = x1 + ox;
        int by1 = y1 + oy;
        int cx = (bx0 + bx1) / 2;
        int feet = by1 - Math.round((by1 - by0) * 0.012F);
        int scale = Math.max(8, Math.round((by1 - by0) / 1.92F));
        float yaw = 22.0F;

        pushClip(x0 - Math.round((x1 - x0) * 0.1F), y0 - 8, x1 + Math.round((x1 - x0) * 0.1F), y1 + 8);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(0.0F);
        pose.mul(camera);
        float oldBody = entity.yBodyRot;
        float oldY = entity.getYRot();
        float oldX = entity.getXRot();
        float oldHeadO = entity.yHeadRotO;
        float oldHead = entity.yHeadRot;
        net.minecraft.world.entity.Pose oldPose = entity.getPose();
        PoseStack poses = g.pose();
        PoseStack.Pose top = poses.last();
        OptionInstance<GraphicsStatus> graphics = mc.options.graphicsMode();
        GraphicsStatus graphicsBefore = graphics.get();
        com.barbwra.mlum.client.downed.DownedClientEvents.portrait = true;
        try {
            entity.setPose(net.minecraft.world.entity.Pose.STANDING);
            entity.yBodyRot = 180.0F + yaw * 0.5F;
            entity.setYRot(180.0F + yaw);
            entity.setXRot(0.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
            poses.pushPose();
            poses.translate(0.0F, 0.0F, z);
            InventoryScreen.renderEntityInInventory(g, cx, feet, scale, pose, camera, entity);
        } catch (Throwable broken) {
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
            if (graphics.get() != graphicsBefore) {
                graphics.set(graphicsBefore);
            }
        } finally {
            com.barbwra.mlum.client.downed.DownedClientEvents.portrait = false;
            unwind(poses, top);
            entity.setPose(oldPose);
            entity.yBodyRot = oldBody;
            entity.setYRot(oldY);
            entity.setXRot(oldX);
            entity.yHeadRotO = oldHeadO;
            entity.yHeadRot = oldHead;
        }
        popClip();
    }

    /**
     * A vehicle's real model in the showcase box.
     *
     * <p>Clipped and flushed like {@link #player}: the entity renderer writes through its own buffer
     * source and depth, so the batched quads this canvas is holding have to be out of the way first
     * or they land on top of it.</p>
     */
    @Override
    public boolean entity(String entityId, int x0, int y0, int x1, int y1) {
        if (g == null || !EntityPreview.available(entityId)) {
            return false;
        }
        flush();
        pushClip(x0, y0, x1, y1);
        boolean drawn;
        PoseStack poses = g.pose();
        PoseStack.Pose top = poses.last();
        try {
            poses.pushPose();
            poses.translate(0.0F, 0.0F, z);
            // a slow idle turn so a parked vehicle does not read as a still image
            float spin = (UiState.now() % 36_000L) / 100.0F * 0.35F;
            drawn = EntityPreview.render(g, entityId, x0 + ox, y0 + oy, x1 + ox, y1 + oy, mouseX, spin);
        } catch (Throwable broken) {
            drawn = false;
        } finally {
            unwind(poses, top);
        }
        popClip();
        return drawn;
    }

    @Override
    public void fallbackText(Shaped shaped, int penX, int baseline, int argb) {
        if (g == null || shaped == null || shaped.text.isEmpty()) {
            return;
        }
        flush();
        Minecraft mc = Minecraft.getInstance();
        float k = shaped.size * com.barbwra.mlum.client.ui.Px.s / 9.0F;
        int c = fade(argb);
        if ((c >>> 24) < 8) {
            return;
        }
        String shown = ArabicText.autoDisplay(shaped.text);
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(penX + ox, baseline + oy - 7.0F * k, z);
        pose.scale(k, k, 1.0F);
        // see-through: no depth test, like the batch, so a count drawn over an item icon stays on
        // top of it instead of vanishing behind the icon's depth
        mc.font.drawInBatch(shown, 0.0F, 0.0F, c, false, pose.last().pose(), g.bufferSource(),
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
        g.flush();
        pose.popPose();
    }

    @Override
    public void pushClip(int x0, int y0, int x1, int y1) {
        x0 += ox;
        y0 += oy;
        x1 += ox;
        y1 += oy;
        if (clipDepth > 0) {
            int o = (clipDepth - 1) * 4;
            x0 = Math.max(x0, clips[o]);
            y0 = Math.max(y0, clips[o + 1]);
            x1 = Math.min(x1, clips[o + 2]);
            y1 = Math.min(y1, clips[o + 3]);
        }
        if (clipDepth >= clips.length / 4) {
            return;
        }
        flush();
        int o = clipDepth * 4;
        clips[o] = x0;
        clips[o + 1] = y0;
        clips[o + 2] = Math.max(x0, x1);
        clips[o + 3] = Math.max(y0, y1);
        clipDepth++;
        applyScissor();
    }

    @Override
    public void popClip() {
        if (clipDepth <= 0) {
            return;
        }
        flush();
        clipDepth--;
        applyScissor();
    }

    private void applyScissor() {
        if (clipDepth <= 0) {
            RenderSystem.disableScissor();
            return;
        }
        int o = (clipDepth - 1) * 4;
        int x0 = Math.max(0, clips[o]);
        int y0 = Math.max(0, clips[o + 1]);
        int x1 = Math.min(fbW, clips[o + 2]);
        int y1 = Math.min(fbH, clips[o + 3]);
        RenderSystem.enableScissor(x0, fbH - Math.max(y0, y1), Math.max(0, x1 - x0), Math.max(0, y1 - y0));
    }

    @Override
    public void pushZ(float dz) {
        if (zDepth < zs.length) {
            zs[zDepth++] = z;
            z += dz;
        }
    }

    @Override
    public void popZ() {
        if (zDepth > 0) {
            z = zs[--zDepth];
        }
    }

    @Override
    public void pushAlpha(float a) {
        if (alphaDepth < alphas.length) {
            alphas[alphaDepth++] = alpha;
        }
        alpha *= Math.max(0.0F, Math.min(1.0F, a));
    }

    @Override
    public void popAlpha() {
        alpha = alphaDepth > 0 ? alphas[--alphaDepth] : 1.0F;
    }

    @Override
    public void setOffset(int dx, int dy) {
        ox = dx;
        oy = dy;
    }
}
