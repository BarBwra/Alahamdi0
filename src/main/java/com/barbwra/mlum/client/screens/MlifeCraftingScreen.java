package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.client.hud.field.HudPen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The crafting table in the bag's look, with what you can make right now beside it.
 *
 * <pre>
 *   ┌── طاولة الصنع ───────────────┐  ┌── تقدر تسوي · 12 ──┐
 *   │  [][][]                       │  │ [] [] [] [] [] []   │
 *   │  [][][]   ›››   [result]      │  │ [] [] [] [] [] []   │
 *   │  [][][]                       │  │ ...                 │
 *   │  شنطتك                        │  │                     │
 *   │  [][][][][][][][][]  x3       │  │ اضغط وصفة تتعبى    │
 *   │  [][][][][][][][][]           │  └─────────────────────┘
 *   └───────────────────────────────┘
 * </pre>
 *
 * <p>The slots are the vanilla crafting table's own, in the same places, so shift-clicking,
 * dragging and every other mod's handling work unchanged; only the drawing is new. The list on the
 * side is every recipe you have unlocked that your inventory and the grid can make; clicking one
 * fills the grid exactly the way the vanilla recipe book does (shift for as many as you can).</p>
 */
@OnlyIn(Dist.CLIENT)
public class MlifeCraftingScreen extends AbstractContainerScreen<CraftingMenu> {

    private static final int GRID_W = 176;
    private static final int LIST_W = 124;
    private static final int GAP = 6;
    private static final int COLS = 6;
    private static final int ROWS = 7;

    private final ScreenKit kit = new ScreenKit();
    private final List<CraftingRecipe> craftable = new ArrayList<>();
    private int scroll;
    private int ticks;

    public MlifeCraftingScreen(CraftingMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = GRID_W + GAP + LIST_W;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        refresh();
    }

    @Override
    protected void containerTick() {
        if (++ticks % 10 == 0) {
            refresh();
        }
    }

    private void refresh() {
        craftable.clear();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        StackedContents stacked = new StackedContents();
        minecraft.player.getInventory().fillStackedContents(stacked);
        menu.fillCraftSlotsStackedContents(stacked);
        for (CraftingRecipe r : minecraft.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            if (!r.isSpecial() && r.canCraftInDimensions(3, 3) && minecraft.player.getRecipeBook().contains(r)
                    && stacked.canCraft(r, null)) {
                craftable.add(r);
            }
        }
        craftable.sort(Comparator.comparing(r -> r.getResultItem(minecraft.level.registryAccess()).getHoverName().getString()));
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private int maxScroll() {
        int rows = (craftable.size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        CraftingRecipe hover = recipeAt(mouseX, mouseY);
        if (hover != null && minecraft != null && minecraft.level != null) {
            g.renderTooltip(font, hover.getResultItem(minecraft.level.registryAccess()), mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x0 = leftPos;
        int y0 = topPos;
        kit.begin(g);
        try {
            HudPen pen = kit.pen;
            kit.panel(x0 - 5, y0 - 4, GRID_W + 8, imageHeight + 8);
            kit.heading("طاولة الصنع", x0 + GRID_W - 2, y0 + 8.0F);
            pen.text(pen.kufi("شنطتك", 5.0F, 600), x0 + GRID_W - 4, y0 + 80.0F, HudPen.RIGHT, ScreenKit.MUTED);
            // a cell under every slot, wherever the menu put it
            for (Slot s : menu.slots) {
                boolean result = s instanceof net.minecraft.world.inventory.ResultSlot;
                float cx = x0 + s.x - 1;
                float cy = y0 + s.y - 1;
                float size = 18;
                if (result) {
                    cx -= 4;
                    cy -= 4;
                    size = 26;
                }
                pen.rect(cx, cy, size, size, 0xFF161A13);
                pen.rect(cx, cy, size, 0.5F, result ? ScreenKit.AMBER : 0xFF272B22);
                pen.rect(cx, cy + size - 0.5F, size, 0.5F, result ? ScreenKit.AMBER : 0xFF272B22);
                pen.rect(cx, cy, 0.5F, size, result ? ScreenKit.AMBER : 0xFF272B22);
                pen.rect(cx + size - 0.5F, cy, 0.5F, size, result ? ScreenKit.AMBER : 0xFF272B22);
                pen.rect(cx + 0.5F, cy + 0.5F, size - 1, 1, 0x80000000);
            }
            // the chevrons from the grid to the result
            float ax = x0 + 92;
            float ay = y0 + 42;
            for (int i = 0; i < 3; i++) {
                float px = ax + i * 6;
                int c = ScreenKit.alpha(ScreenKit.AMBER, 0.35F + 0.3F * i);
                pen.rect(px, ay - 3, 1.5F, 1.5F, c);
                pen.rect(px + 1.5F, ay - 1.5F, 1.5F, 1.5F, c);
                pen.rect(px + 3, ay, 1.5F, 1.5F, c);
                pen.rect(px + 1.5F, ay + 1.5F, 1.5F, 1.5F, c);
                pen.rect(px, ay + 3, 1.5F, 1.5F, c);
            }

            // the list
            float lx = x0 + GRID_W + GAP;
            kit.panel(lx, y0 - 4, LIST_W, imageHeight + 8);
            kit.heading("تقدر تسوي", lx + LIST_W - 6.0F, y0 + 8.0F);
            pen.text(pen.pixel(String.valueOf(craftable.size()), 7.0F, 700), lx + 6.0F, y0 + 8.5F, HudPen.LEFT, ScreenKit.AMBER);
            if (craftable.isEmpty()) {
                pen.text(pen.kufi("ما عندك مواد لأي وصفة", 5.0F, 600), lx + LIST_W / 2.0F, y0 + 60.0F, HudPen.CENTER, ScreenKit.FAINT);
            }
            for (int i = 0; i < ROWS * COLS; i++) {
                int idx = scroll * COLS + i;
                if (idx >= craftable.size()) {
                    break;
                }
                float cx = cellX(i);
                float cy = cellY(i);
                boolean hov = mouseX >= cx && mouseX < cx + 18 && mouseY >= cy && mouseY < cy + 18;
                pen.rect(cx, cy, 18, 18, hov ? 0xFF1E2319 : 0xFF161A13);
                int edge = hov ? ScreenKit.AMBER : 0xFF272B22;
                pen.rect(cx, cy, 18, 0.5F, edge);
                pen.rect(cx, cy + 17.5F, 18, 0.5F, edge);
                pen.rect(cx, cy, 0.5F, 18, edge);
                pen.rect(cx + 17.5F, cy, 0.5F, 18, edge);
            }
            if (maxScroll() > 0) {
                float track = ROWS * 19.0F;
                float th = Math.max(8.0F, track * ROWS / (float) (ROWS + maxScroll()));
                float ty = y0 + 16 + (track - th) * scroll / (float) maxScroll();
                pen.rect(lx + 2.0F, y0 + 16, 1.0F, track, 0x22FFFFFF);
                pen.rect(lx + 2.0F, ty, 1.0F, th, ScreenKit.AMBER);
            }
            pen.text(pen.kufi("اضغط وصفة وتتعبى · Shift لأكثر عدد", 4.3F, 600), lx + LIST_W / 2.0F, y0 + imageHeight - 2.0F,
                    HudPen.CENTER, ScreenKit.FAINT);
        } finally {
            kit.end();
        }
        // the results on top of their cells
        if (minecraft != null && minecraft.level != null) {
            for (int i = 0; i < ROWS * COLS; i++) {
                int idx = scroll * COLS + i;
                if (idx >= craftable.size()) {
                    break;
                }
                ItemStack out = craftable.get(idx).getResultItem(minecraft.level.registryAccess());
                int ix = (int) cellX(i) + 1;
                int iy = (int) cellY(i) + 1;
                g.renderItem(out, ix, iy);
                g.renderItemDecorations(font, out, ix, iy);
            }
        }
    }

    private float cellX(int i) {
        float lx = leftPos + GRID_W + GAP;
        // right to left, the way the bag lays out
        return lx + LIST_W - 8 - 18 - (i % COLS) * 19.0F;
    }

    private float cellY(int i) {
        return topPos + 16 + (i / COLS) * 19.0F;
    }

    private CraftingRecipe recipeAt(double mx, double my) {
        for (int i = 0; i < ROWS * COLS; i++) {
            int idx = scroll * COLS + i;
            if (idx >= craftable.size()) {
                return null;
            }
            float cx = cellX(i);
            float cy = cellY(i);
            if (mx >= cx && mx < cx + 18 && my >= cy && my < cy + 18) {
                return craftable.get(idx);
            }
        }
        return null;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // drawn in renderBg, in the UI's own fonts
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        CraftingRecipe r = recipeAt(mx, my);
        if (r != null && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handlePlaceRecipe(menu.containerId, r, hasShiftDown());
            ScreenKit.click();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= leftPos + GRID_W + GAP) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll + (delta > 0 ? -1 : 1)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    protected boolean hasClickedOutside(double mx, double my, int left, int top, int button) {
        return mx < left - 6 || my < top - 4 || mx >= left + imageWidth || my >= top + imageHeight + 4;
    }
}
