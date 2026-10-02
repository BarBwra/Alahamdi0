package com.barbwra.mlum.warehouse.client.gui;

import com.barbwra.mlum.warehouse.client.ClientTerminalData;
import com.barbwra.mlum.warehouse.core.CargoType;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.barbwra.mlum.warehouse.net.ModNetwork;
import com.barbwra.mlum.warehouse.net.TerminalAction;
import com.barbwra.mlum.warehouse.net.TerminalSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The SecuroServ terminal.
 *
 * <p>A dumb renderer over {@link ClientTerminalData}. It draws what the last snapshot said and,
 * when clicked, sends a typed intent. It computes no price, decides no eligibility and stores no
 * selection - the convoy lives in the server-side session, so what is highlighted here is always
 * what the server would actually dispatch.</p>
 *
 * <p><b>Hit testing is rebuilt every frame.</b> {@link #hits} is cleared at the top of
 * {@code render} and repopulated as each control is drawn, so a control's clickable area is defined
 * in exactly one place - the code that draws it - and the two can never drift apart. It also means
 * a control that stops being drawn stops being clickable in the same frame, which is what keeps a
 * stale button from firing after a snapshot changes underneath it.</p>
 */
public class TerminalScreen extends Screen {

    /*
     * Tab labels are the operation's own vocabulary, not literal translations of English UI nouns.
     * "الإرسال" (dispatch/sending) was the stiff, wrong word here - a smuggler moving stock does not
     * "send" it, they sell it, so the tab is "البيع". Likewise "التصنيع" for production rather than
     * the mechanical "التجميع", and "الطريق" for the route.
     */
    private enum Tab {
        OVERVIEW("نظرة عامة"),
        PRODUCTION("التصنيع"),
        MANIFEST("المخزون"),
        DISPATCH("البيع"),
        UPGRADES("التطويرات"),
        STATS("سجلّي");

        final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    /** A clickable region, valid for one frame. */
    private record Hit(int x, int y, int w, int h, Runnable action) {

        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /*
     * The terminal scales with the window instead of sitting at one fixed size. Clamped at both
     * ends: below the minimum the panels stop fitting their own contents, and above the maximum a
     * 4K window would stretch a five-tab UI across half a metre of desk.
     */
    private static final int MIN_W = 348;
    private static final int MAX_W = 470;
    private static final int MIN_H = 206;
    private static final int MAX_H = 272;
    private static final int NAV_W = 82;

    private final List<Hit> hits = new ArrayList<>();

    private int panelW;
    private int panelH;

    /* Animation clocks. Both are wall-clock so they run at framerate, not tick rate. */
    private static final long OPEN_MS = 220L;
    private static final long TAB_MS = 160L;
    private final long openedAt = System.currentTimeMillis();
    private long tabChangedAt = System.currentTimeMillis();

    /** Deferred so it draws over everything else, exactly like a vanilla item tooltip. */
    private List<Component> tooltip;

    private Tab tab = Tab.OVERVIEW;
    private String selectedRecipe;
    /** Eased display value for the payout figure. Cosmetic only. */
    private float shownTotal;
    private int upgradePath = -1;
    private int upgradeLevel = -1;
    private int manifestScroll;

    private int left;
    private int top;

    public TerminalScreen() {
        super(Component.literal("Warehouse Terminal"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModNetwork.sendAction(TerminalAction.CLOSE, "", 0, 0);
        super.onClose();
    }

    private int refreshTicks;

    /**
     * Pulls a fresh snapshot once a second while the terminal is open.
     *
     * <p>Assembly lines finish, crates spoil and the market moves on the server's clock, none of
     * which the client is told about otherwise - actions push a snapshot, but a player who opens
     * the terminal and simply watches would sit on a frozen view. One packet a second against a
     * 24-per-3-seconds budget leaves plenty of headroom for actual clicking.</p>
     *
     * <p>Progress bars do not depend on this: they interpolate locally from the absolute
     * {@code completesAt} already in the snapshot, so they stay smooth between refreshes.</p>
     */
    @Override
    public void tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            ModNetwork.sendAction(TerminalAction.REFRESH, "", 0, 0);
        }
    }

    /* ================================================================= render */

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        hits.clear();

        TerminalSnapshot snap = ClientTerminalData.snapshot();
        GuiDraw.vignette(g, this.width, this.height);
        if (snap == null) {
            GuiDraw.glowCentered(g, font, "جاري الاتصال بالسيرفر", this.width / 2, this.height / 2, Theme.TEXT_DIM);
            return;
        }

        detectOutcomes(snap);
        tooltip = null;

        panelW = Mth.clamp(this.width - 56, MIN_W, MAX_W);
        panelH = Mth.clamp(this.height - 48, MIN_H, MAX_H);
        left = (this.width - panelW) / 2;
        top = (this.height - panelH) / 2;

        /*
         * Open animation: the terminal eases up from 94% and fades in over 220ms. Done with a pose
         * transform around the panel's centre rather than by animating every draw call, so it costs
         * one matrix push and nothing else has to know it is happening.
         */
        float openT = ease(Mth.clamp((System.currentTimeMillis() - openedAt) / (float) OPEN_MS, 0.0F, 1.0F));
        g.pose().pushPose();
        if (openT < 1.0F) {
            float scale = 0.94F + 0.06F * openT;
            g.pose().translate(left + panelW / 2.0F, top + panelH / 2.0F, 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.pose().translate(-(left + panelW / 2.0F), -(top + panelH / 2.0F), 0.0F);
        }

        GuiDraw.glass(g, left, top, panelW, panelH);
        renderHeader(g, snap);
        renderNav(g, mouseX, mouseY);
        renderWatermark(g);

        int cx = left + NAV_W + 4;
        int cy = top + 22;
        int cw = panelW - NAV_W - 12;
        int ch = panelH - 38;

        // Tab animation: the incoming panel slides in from the right and settles. Short enough to
        // read as responsiveness rather than as a delay.
        float tabT = ease(Mth.clamp((System.currentTimeMillis() - tabChangedAt) / (float) TAB_MS, 0.0F, 1.0F));
        g.pose().pushPose();
        g.pose().translate((1.0F - tabT) * 14.0F, 0.0F, 0.0F);

        switch (tab) {
            case OVERVIEW -> renderOverview(g, snap, cx, cy, cw, ch);
            case PRODUCTION -> renderProduction(g, snap, cx, cy, cw, ch, mouseX, mouseY);
            case MANIFEST -> renderManifest(g, snap, cx, cy, cw, ch, mouseX, mouseY);
            case DISPATCH -> renderDispatch(g, snap, cx, cy, cw, ch, mouseX, mouseY);
            case UPGRADES -> renderUpgrades(g, snap, cx, cy, cw, ch, mouseX, mouseY);
            case STATS -> renderStats(g, snap, cx, cy, cw, ch);
        }
        g.pose().popPose();

        renderFlash(g);
        g.pose().popPose();

        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    /** Switching restarts the slide, so re-clicking the current tab still gives feedback. */
    private void switchTab(Tab target) {
        tab = target;
        tabChangedAt = System.currentTimeMillis();
    }

    /** Cubic ease-out. Fast at the start, settles at the end - reads as weight, not lag. */
    private static float ease(float t) {
        float inv = 1.0F - t;
        return 1.0F - inv * inv * inv;
    }

    private void renderWatermark(GuiGraphics g) {
        GuiDraw.drawLeft(g, font, "Created By BarBwra", left + 8, top + panelH - 11,
                Theme.withAlpha(Theme.TEXT_MUTED, 0xB0));
    }

    private void renderHeader(GuiGraphics g, TerminalSnapshot snap) {
        g.fillGradient(left + 2, top + 1, left + panelW - 2, top + 15,
                Theme.headerTop(), Theme.headerBottom());
        // Hazard tape under the title bar - the one place stripes belong, marking the whole
        // operation as something you should not be doing.
        GuiDraw.hazard(g, left + 2, top + 15, panelW - 4, 2, Theme.accent(), 0x55);
        g.fill(left + 2, top + 17, left + panelW - 2, top + 18, Theme.accent(140));

        GuiDraw.glowLeft(g, font, "MLIFE // WAREHOUSE OPS", left + 9, top + 4, Theme.accent());
        GuiDraw.glowRight(g, font, snap.balance() + "$", left + panelW - 9, top + 4, Theme.SUCCESS);

        if (snap.eventEndsAt() > System.currentTimeMillis()) {
            String remaining = GuiDraw.duration(snap.eventEndsAt() - System.currentTimeMillis());
            GuiDraw.badge(g, font, "2X · " + remaining, left + panelW / 2 - 26, top + 2, Theme.WARN);
        }
    }

    private void renderNav(GuiGraphics g, int mouseX, int mouseY) {
        int y = top + 22;
        for (Tab value : Tab.values()) {
            int h = 18;
            boolean active = value == tab;
            boolean hover = mouseX >= left + 4 && mouseX < left + NAV_W
                    && mouseY >= y && mouseY < y + h;

            if (active) {
                g.fillGradient(left + 4, y, left + NAV_W, y + h, Theme.accent(70), Theme.accent(18));
                g.fill(left + 4, y, left + 6, y + h, Theme.accent());
                GuiDraw.glowRight(g, font, value.title, left + NAV_W - 7, y + 5, Theme.TEXT);
            } else {
                if (hover) {
                    g.fill(left + 4, y, left + NAV_W, y + h, Theme.accent(28));
                    g.fill(left + 4, y, left + 5, y + h, Theme.accent(140));
                }
                GuiDraw.drawRight(g, font, value.title, left + NAV_W - 7, y + 5,
                        hover ? Theme.TEXT : Theme.TEXT_DIM);
            }

            Tab target = value;
            hits.add(new Hit(left + 4, y, NAV_W - 4, h, () -> switchTab(target)));
            y += h + 2;
        }
    }

    /* =============================================================== overview */

    private void renderOverview(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h) {
        GuiDraw.panel(g, font, x, y, w, 44, "المخزون");
        GuiDraw.drawLeft(g, font, snap.crateCount() + " / " + snap.capacity(), x + 8, y + 18, Theme.TEXT);
        GuiDraw.segmentBar(g, x + 8, y + 30, w - 16, 6, 24,
                snap.capacity() == 0 ? 0 : (float) snap.crateCount() / snap.capacity(), Theme.accent());
        GuiDraw.drawRight(g, font, "صلاحية " + snap.shelfLifeDays() + " يوم",
                x + w - 8, y + 18, Theme.TEXT_MUTED);

        int ly = y + 48;
        GuiDraw.panel(g, font, x, ly, w, 62, "خطوط التصنيع " + snap.craftCount() + "/" + snap.lines());
        int row = ly + 17;
        long now = System.currentTimeMillis();
        for (TerminalSnapshot.CraftView craft : snap.crafts()) {
            if (row > ly + 52) {
                break;
            }
            float progress = craft.completesAt() > craft.startedAt()
                    ? (float) (now - craft.startedAt()) / (craft.completesAt() - craft.startedAt())
                    : 1.0F;
            g.fill(x + 8, row + 1, x + 11, row + 7, Theme.cargo(craft.cargo()));
            GuiDraw.meter(g, x + 15, row + 2, w - 72, 5, progress, Theme.cargo(craft.cargo()));
            GuiDraw.drawRight(g, font, GuiDraw.clock(craft.completesAt() - now),
                    x + w - 8, row, Theme.TEXT_DIM);
            row += 11;
        }
        if (snap.crafts().isEmpty()) {
            GuiDraw.drawCentered(g, font, "لا توجد خطوط عاملة حالياً", x + w / 2, ly + 30, Theme.TEXT_MUTED);
        }

        int my = ly + 66;
        GuiDraw.panel(g, font, x, my, w, h - (my - y) - 4, "مؤشر السوق");
        int gx = x + 8;
        int gy = my + 18;
        int gw = w - 60;
        int gh = h - (my - y) - 28;
        GuiDraw.graph(g, gx, gy, gw, gh, snap.history(), Theme.CARGO, 0.6F, 1.6F);

        CargoType[] types = CargoType.values();
        for (int i = 0; i < types.length && i < snap.indices().length; i++) {
            int ky = gy + i * 10;
            g.fill(x + w - 46, ky + 2, x + w - 42, ky + 6, Theme.cargo(i));
            GuiDraw.drawLeft(g, font, String.format("%.2f", snap.indices()[i]),
                    x + w - 38, ky, Theme.TEXT_DIM);
        }
    }

    /* ============================================================= production */

    private void renderProduction(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h,
                                  int mouseX, int mouseY) {
        /*
         * The grid is sized from the recipe count, not from a hardcoded panel height.
         *
         * This is what made the weapon boxes "invisible on servers". The panel was a fixed 88px
         * with a 24px cell and a 2px gutter, which fits exactly two rows - and the catalog lists
         * nine recipes in the order food, medical, weapons, so indices 6, 7 and 8 were the entire
         * weapons column and every one of them tripped the overflow break. Nothing to do with slot
         * sync or NBT: they were laid out past the bottom edge and skipped.
         */
        int cols = 3;
        int cellW = (w - 16) / cols;
        int cellH = 22;
        int rows = Math.max(1, (snap.recipes().size() + cols - 1) / cols);
        int gridH = 17 + rows * (cellH + 2) + 4;

        GuiDraw.panel(g, font, x, y, w, gridH, "الصناديق المتاحة");

        int i = 0;
        for (TerminalSnapshot.RecipeView recipe : snap.recipes()) {
            int col = i % cols;
            int rowIndex = i / cols;
            int rx = x + 8 + col * cellW;
            int ry = y + 17 + rowIndex * (cellH + 2);

            boolean chosen = recipe.id().equals(selectedRecipe);
            boolean hover = mouseX >= rx && mouseX < rx + cellW - 2 && mouseY >= ry && mouseY < ry + cellH;
            int tint = Theme.cargo(recipe.cargo());

            /*
             * Readiness is resolved here rather than only inside the detail panel. A player should
             * be able to see which boxes they can build without clicking every tile in turn - that
             * is the whole "look at it and know" requirement, and it costs one pass over counts the
             * server already sent.
             */
            int shortfall = 0;
            for (TerminalSnapshot.ReqView req : recipe.ingredients()) {
                shortfall += Math.max(0, req.need() - req.held());
            }
            boolean ready = recipe.available() && shortfall == 0;

            // Hover lifts the tile by a pixel and widens it by one on each side. Cheap, and it makes
            // the grid feel physical instead of painted on.
            int lift = hover ? 1 : 0;
            int tx = rx - lift;
            int ty = ry - lift;
            int tw = cellW - 2 + lift * 2;
            int th = cellH + lift * 2;

            if (hover || chosen) {
                GuiDraw.outline(g, tx - 1, ty - 1, tw + 2, th + 2, Theme.withAlpha(tint, hover ? 0x80 : 0x40));
            }
            g.fillGradient(tx, ty, tx + tw, ty + th,
                    Theme.withAlpha(tint, chosen ? 0x58 : (hover ? 0x34 : 0x16)),
                    Theme.withAlpha(tint, 0x06));
            GuiDraw.outline(g, tx, ty, tw, th,
                    chosen ? tint : (hover ? Theme.BORDER_HI : Theme.BORDER_SOFT));

            // A ready box gets a lit strip down its leading edge. Unmistakable at a glance.
            if (ready) {
                g.fill(tx + 1, ty + 1, tx + 3, ty + th - 1, Theme.SUCCESS);
            }

            int color = recipe.available() ? Theme.TEXT : Theme.TEXT_MUTED;
            GuiDraw.glowRight(g, font, CargoType.values()[recipe.cargo()].display() + " "
                    + CargoType.tierNumeral(recipe.tier()), tx + tw - 5, ty + 2, color);
            GuiDraw.drawLeft(g, font, recipe.minValue() + "-" + recipe.maxValue() + "$",
                    tx + 5, ty + 12, Theme.SUCCESS);
            GuiDraw.drawRight(g, font, (recipe.durationSeconds() / 60) + "د",
                    tx + tw - 5, ty + 12, Theme.TEXT_MUTED);

            if (!recipe.available()) {
                g.fill(tx, ty, tx + tw, ty + th, 0x90101010);
                GuiDraw.glowCentered(g, font, "غير متاح", tx + tw / 2, ty + 7, Theme.DANGER);
            } else if (!ready) {
                GuiDraw.drawLeft(g, font, "✖" + shortfall, tx + 5, ty + 2, Theme.DANGER);
            } else {
                GuiDraw.drawLeft(g, font, "✔", tx + 5, ty + 2, Theme.SUCCESS);
            }

            String id = recipe.id();
            hits.add(new Hit(rx, ry, cellW - 2, cellH, () -> selectedRecipe = id));
            i++;
        }

        /* ---- requisition for the selected recipe ---- */
        int ry = y + gridH + 4;
        int rh = h - (ry - y) - 4;
        TerminalSnapshot.RecipeView chosen = find(snap, selectedRecipe);
        GuiDraw.panel(g, font, x, ry, w, rh,
                chosen == null ? "المتطلبات" : CargoType.values()[chosen.cargo()].display()
                        + " " + CargoType.tierNumeral(chosen.tier()));

        if (chosen == null) {
            GuiDraw.drawCentered(g, font, "اختر صندوقاً من الأعلى", x + w / 2, ry + rh / 2 - 4, Theme.TEXT_MUTED);
            return;
        }

        /*
         * Each requirement draws the real item icon beside its name. A player who cannot read the
         * label - or will not - can still recognise what to go and find, which is the difference
         * between a recipe list and a shopping list.
         */
        int line = ry + 17;
        boolean all = true;
        for (TerminalSnapshot.ReqView req : chosen.ingredients()) {
            boolean met = req.held() >= req.need();
            all &= met;

            GuiDraw.drawLeft(g, font, met ? "✔" : "✖", x + 8, line + 3,
                    met ? Theme.SUCCESS : Theme.DANGER);
            GuiDraw.drawLeft(g, font, req.held() + "/" + req.need(), x + 18, line + 3,
                    met ? Theme.TEXT_DIM : Theme.DANGER);

            int iconX = x + w - 8 - font.width(GuiDraw.rtl(req.display())) - 20;
            drawItemIcon(g, req.item(), iconX, line, met ? 1.0F : 0.45F);
            GuiDraw.drawRight(g, font, req.display(), x + w - 8, line + 3, rarityColor(req.rarity()));
            line += 14;
        }

        boolean enabled = all && chosen.available() && snap.craftCount() < snap.lines();
        int bw = 96;
        int bx = x + w - bw - 8;
        int by = ry + rh - 18;
        button(g, bx, by, bw, 14, "بدء التصنيع", enabled, mouseX, mouseY, () -> {
            if (enabled) {
                ModNetwork.sendAction(TerminalAction.START_CRAFT, chosen.id(), 0, 0);
            }
        });
    }

    /** Cache so a missing item is looked up once per session, not once per frame. */
    private static final java.util.Map<String, net.minecraft.world.item.ItemStack> ICONS =
            new java.util.HashMap<>();

    /**
     * Draws an item's real inventory icon, greyed when the requirement is unmet.
     *
     * <p>An id from a mod that is not installed resolves to an empty stack and draws a placeholder
     * square rather than nothing, so a broken recipe line still occupies its space and stays
     * diagnosable at a glance.</p>
     */
    private void drawItemIcon(GuiGraphics g, String itemId, int x, int y, float alpha) {
        net.minecraft.world.item.ItemStack stack = ICONS.computeIfAbsent(itemId, id -> {
            net.minecraft.resources.ResourceLocation key =
                    net.minecraft.resources.ResourceLocation.tryParse(id);
            net.minecraft.world.item.Item item = key == null ? null
                    : net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(key);
            return item == null ? net.minecraft.world.item.ItemStack.EMPTY
                    : new net.minecraft.world.item.ItemStack(item);
        });

        if (stack.isEmpty()) {
            GuiDraw.outline(g, x + 2, y + 2, 12, 12, Theme.withAlpha(Theme.DANGER, 0x80));
            return;
        }

        if (alpha < 1.0F) {
            g.setColor(1.0F, 1.0F, 1.0F, alpha);
        }
        g.renderItem(stack, x, y);
        if (alpha < 1.0F) {
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static int rarityColor(int rarity) {
        return switch (Math.max(1, Math.min(5, rarity))) {
            case 2 -> 0xFF6FCF97;
            case 3 -> 0xFF4FC3F7;
            case 4 -> 0xFFB05CE0;
            case 5 -> 0xFFE8C15A;
            default -> Theme.TEXT_DIM;
        };
    }

    private static TerminalSnapshot.RecipeView find(TerminalSnapshot snap, String id) {
        if (id == null) {
            return null;
        }
        for (TerminalSnapshot.RecipeView recipe : snap.recipes()) {
            if (recipe.id().equals(id)) {
                return recipe;
            }
        }
        return null;
    }

    /* =============================================================== manifest */

    private void renderManifest(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        int panelH2 = h - 20;
        GuiDraw.panel(g, font, x, y, w, panelH2,
                "المخزون " + snap.crateCount() + "/" + snap.capacity());

        /*
         * Columns are derived from the panel width instead of being fixed at six, which is what
         * left a dead strip down the right-hand side once storage was upgraded past 36 crates.
         * The grid now fills the row edge to edge and the cell is widened to fit the value text -
         * at six columns the numbers were overlapping each other.
         */
        int cell = 30;
        int gx = x + 8;
        int gy = y + 18;
        int cols = Math.max(4, (w - 16 + 2) / (cell + 2));
        int rows = Math.max(1, (panelH2 - 26) / (cell + 2));
        long now = System.currentTimeMillis();

        List<TerminalSnapshot.CrateView> crates = snap.crates();
        int perPage = cols * rows;
        int maxScroll = Math.max(0, (crates.size() - 1) / cols - rows + 1);
        manifestScroll = Math.max(0, Math.min(manifestScroll, maxScroll));
        int start = manifestScroll * cols;

        for (int i = 0; i < perPage && start + i < crates.size(); i++) {
            TerminalSnapshot.CrateView crate = crates.get(start + i);
            int cxp = gx + (i % cols) * (cell + 2);
            int cyp = gy + (i / cols) * (cell + 2);

            boolean selected = snap.selected().contains(crate.id());
            boolean spoiled = crate.expiresAt() <= now;
            boolean hover = mouseX >= cxp && mouseX < cxp + cell && mouseY >= cyp && mouseY < cyp + cell;
            int tint = spoiled ? Theme.DANGER : Theme.cargo(crate.cargo());

            g.fillGradient(cxp, cyp, cxp + cell, cyp + cell,
                    Theme.withAlpha(tint, selected ? 0x60 : 0x18), Theme.withAlpha(tint, 0x06));

            // Shelf life as a rising column behind the label - a grid of crates reads as a row of
            // gauges, and one about to spoil is visibly emptier than the ones beside it.
            if (!spoiled) {
                GuiDraw.crateFill(g, cxp + 1, cyp + 1, cell - 2, cell - 2, crate.freshness(), tint);
            } else {
                GuiDraw.hazard(g, cxp + 1, cyp + 1, cell - 2, cell - 2, Theme.DANGER, 0x2A);
            }

            GuiDraw.outline(g, cxp, cyp, cell, cell,
                    selected ? tint : (hover ? Theme.BORDER_HI : Theme.BORDER_SOFT));
            if (selected) {
                GuiDraw.outline(g, cxp - 1, cyp - 1, cell + 2, cell + 2, Theme.withAlpha(tint, 0x70));
            }

            GuiDraw.glowCentered(g, font, spoiled ? "☠" : CargoType.tierNumeral(crate.tier()),
                    cxp + cell / 2, cyp + 4, tint);
            GuiDraw.drawCentered(g, font, String.valueOf(crate.currentValue()),
                    cxp + cell / 2, cyp + 15, spoiled ? Theme.TEXT_MUTED : Theme.TEXT_DIM);

            // Hovering queues a vanilla-style tooltip. Everything a player could want to know about
            // a crate is in it, which is what let the cramped "التفاصيل" panel be deleted outright -
            // that panel showed one crate at a time, collided with its own button, and made the
            // expiry unreadable.
            if (hover) {
                tooltip = crateTooltip(crate, spoiled, now);
            }

            UUID id = crate.id();
            hits.add(new Hit(cxp, cyp, cell, cell,
                    () -> ModNetwork.sendAction(TerminalAction.TOGGLE_CRATE, id.toString(), 0, 0)));
        }

        if (crates.isEmpty()) {
            GuiDraw.drawCentered(g, font, "المخزن فارغ", x + w / 2, y + panelH2 / 2, Theme.TEXT_MUTED);
        }

        /* ---- footer: scroll position, selection count, purge ---- */
        int fy = y + panelH2 + 4;
        if (maxScroll > 0) {
            GuiDraw.drawLeft(g, font, "▲▼ " + (manifestScroll + 1) + "/" + (maxScroll + 1),
                    x + 8, fy + 3, Theme.TEXT_MUTED);
        }
        GuiDraw.drawCentered(g, font, "محدد: " + snap.selected().size() + " — اضغط على صندوق لاختياره",
                x + w / 2, fy + 3, Theme.TEXT_DIM);
        button(g, x + w - 76, fy, 68, 13, "إتلاف التالف", true, mouseX, mouseY,
                () -> ModNetwork.sendAction(TerminalAction.PURGE_SPOILED, "", 0, 0));
    }

    /**
     * The crate tooltip - type, level, value, and shelf life, in the same shape as a vanilla item's
     * lore so it needs no explaining.
     */
    private List<Component> crateTooltip(TerminalSnapshot.CrateView crate, boolean spoiled, long now) {
        int tint = spoiled ? Theme.DANGER : Theme.cargo(crate.cargo());
        List<Component> lines = new ArrayList<>();

        lines.add(Component.literal(GuiDraw.rtlShaped(CargoType.values()[crate.cargo()].display()))
                .withStyle(style -> style.withColor(tint).withBold(true)));
        lines.add(Component.literal(GuiDraw.rtlShaped("المستوى: " + CargoType.tierNumeral(crate.tier())))
                .withStyle(style -> style.withColor(Theme.TEXT_DIM)));
        lines.add(Component.empty());

        lines.add(Component.literal(GuiDraw.rtlShaped("القيمة الأساسية: " + crate.baseValue() + "$"))
                .withStyle(style -> style.withColor(Theme.TEXT_MUTED)));
        lines.add(Component.literal(GuiDraw.rtlShaped("القيمة الحالية: " + crate.currentValue() + "$"))
                .withStyle(style -> style.withColor(spoiled ? Theme.DANGER : Theme.SUCCESS)));
        lines.add(Component.empty());

        if (spoiled) {
            lines.add(Component.literal(GuiDraw.rtlShaped("☠ تالف - لا يمكن بيعه"))
                    .withStyle(style -> style.withColor(Theme.DANGER)));
        } else {
            long left = crate.expiresAt() - now;
            boolean urgent = left < 86_400_000L;
            lines.add(Component.literal(GuiDraw.rtlShaped("الصلاحية: " + GuiDraw.duration(left)))
                    .withStyle(style -> style.withColor(urgent ? Theme.WARN : Theme.TEXT_DIM)));
            lines.add(Component.literal(GuiDraw.rtlShaped("الجودة: " + Math.round(crate.freshness() * 100) + "%"))
                    .withStyle(style -> style.withColor(Theme.TEXT_MUTED)));
        }
        return lines;
    }

    /* =============================================================== dispatch */

    private void renderDispatch(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        GuiDraw.panel(g, font, x, y, w, 34, "القافلة " + snap.selected().size());
        for (int i = 0; i < snap.selected().size(); i++) {
            int sx = x + 8 + i * 20;
            g.fill(sx, y + 17, sx + 17, y + 29, Theme.accent(70));
            GuiDraw.outline(g, sx, y + 17, 17, 12, Theme.accent());
        }
        if (snap.selected().isEmpty()) {
            // An empty convoy is a dead end, so the panel becomes the way out of it: one button
            // that takes the player straight to the screen where they pick what to sell.
            GuiDraw.drawRight(g, font, "لم تختر أي صندوق بعد", x + w - 10, y + 20, Theme.TEXT_MUTED);
            button(g, x + 8, y + 17, 118, 13, "▶ اختر صناديق من المخزون", true, mouseX, mouseY,
                    () -> switchTab(Tab.MANIFEST));
        } else {
            button(g, x + w - 50, y + 17, 42, 12, "إفراغ", true, mouseX, mouseY,
                    () -> ModNetwork.sendAction(TerminalAction.CLEAR_SELECTION, "", 0, 0));
        }

        int ry = y + 38;
        GuiDraw.panel(g, font, x, ry, w, 60, "الطريق");
        int line = ry + 17;
        for (TerminalSnapshot.RouteView route : snap.routes()) {
            boolean chosen = route.id().equals(snap.selectedRoute());
            boolean hover = mouseX >= x + 6 && mouseX < x + w - 6 && mouseY >= line && mouseY < line + 13;
            if (chosen) {
                g.fill(x + 6, line, x + w - 6, line + 13, Theme.accent(46));
            } else if (hover) {
                g.fill(x + 6, line, x + w - 6, line + 13, Theme.accent(18));
            }
            GuiDraw.drawRight(g, font, route.display(), x + w - 10, line + 3,
                    chosen ? Theme.TEXT : Theme.TEXT_DIM);
            GuiDraw.pips(g, x + 10, line + 4, route.riskTier(), 3,
                    route.riskTier() >= 3 ? Theme.DANGER : (route.riskTier() == 2 ? Theme.WARN : Theme.SUCCESS));
            GuiDraw.drawLeft(g, font, String.format("x%.2f", route.multiplier()), x + 34, line + 3, Theme.SUCCESS);
            GuiDraw.drawLeft(g, font, route.distance() + "m", x + 68, line + 3, Theme.TEXT_MUTED);

            String id = route.id();
            hits.add(new Hit(x + 6, line, w - 12, 13, () ->
                    ModNetwork.sendAction(TerminalAction.SET_ROUTE, id, 0, 0)));
            line += 14;
        }

        int qy = ry + 64;
        int qh = h - (qy - y) - 4;
        GuiDraw.panel(g, font, x, qy, w, qh, "الأرباح");

        /*
         * Each multiplier gets its own chip rather than being a line of flat grey text. The label
         * sits right, the value sits left in its own colour, and the row has a border - so the
         * breakdown reads as a receipt instead of as debug output, which is what it looked like
         * when it was five drawString calls stacked on top of each other.
         */
        int rowH = 12;
        int bl = qy + 18;
        for (String entry : snap.quoteBreakdown()) {
            if (bl + rowH > qy + qh - 42) {
                break;
            }
            int split = entry.lastIndexOf(':');
            String label = split > 0 ? entry.substring(0, split) : entry;
            String value = split > 0 ? entry.substring(split + 1).trim() : "";
            boolean boost = value.startsWith("×") && !value.startsWith("×0");

            g.fillGradient(x + 8, bl, x + w - 8, bl + rowH,
                    Theme.withAlpha(Theme.accent(), 0x14), Theme.withAlpha(Theme.accent(), 0x06));
            GuiDraw.outline(g, x + 8, bl, w - 16, rowH, Theme.BORDER_SOFT);
            g.fill(x + 8, bl, x + 10, bl + rowH, Theme.withAlpha(Theme.accent(), 0x90));

            GuiDraw.drawRight(g, font, label, x + w - 13, bl + 2, Theme.TEXT_DIM);
            GuiDraw.drawLeft(g, font, value, x + 14, bl + 2, boost ? Theme.WARN : Theme.TEXT);
            bl += rowH + 2;
        }

        /* ---- the total, counting up to its target ---- */
        int ty = qy + qh - 38;
        boolean enabled = !snap.selected().isEmpty() && !snap.missionActive();

        // Eases toward the real figure instead of snapping, so adding a crate reads as the number
        // climbing. Purely cosmetic - the value paid is always snap.quoteTotal().
        shownTotal += (snap.quoteTotal() - shownTotal) * 0.22F;
        if (Math.abs(snap.quoteTotal() - shownTotal) < 1.0F) {
            shownTotal = snap.quoteTotal();
        }

        int totalColor = snap.quoteTotal() > 0 ? Theme.SUCCESS : Theme.TEXT_MUTED;
        g.fillGradient(x + 8, ty, x + w - 8, ty + 20,
                Theme.withAlpha(totalColor, 0x38), Theme.withAlpha(totalColor, 0x10));
        GuiDraw.outline(g, x + 8, ty, w - 16, 20, Theme.withAlpha(totalColor, 0xA0));
        GuiDraw.drawRight(g, font, "الإجمالي", x + w - 14, ty + 6, Theme.TEXT_DIM);
        GuiDraw.glowLeft(g, font, Math.round(shownTotal) + "$", x + 14, ty + 6, totalColor);

        GuiDraw.badge(g, font, snap.missionActive() ? "لديك عملية جارية" : "النقل مشياً فقط",
                x + w - 92, ty - 14, snap.missionActive() ? Theme.DANGER : Theme.WARN);
        button(g, x + 8, ty + 23, w - 16, 14, "تأكيد البيع", enabled, mouseX, mouseY,
                () -> ModNetwork.sendAction(TerminalAction.DISPATCH, "", 0, 0));
    }

    /* ================================================================== stats */

    /**
     * The player's career: what they have earned, how reliable they are, and their last eight runs.
     *
     * <p>Everything here is server state, sent in the snapshot. The client tallies nothing - a
     * "total earned" the client could compute would be a "total earned" a client could lie
     * about.</p>
     */
    private void renderStats(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h) {
        int headline = 56;
        GuiDraw.panel(g, font, x, y, w, headline, "سجلّ العمليات");

        GuiDraw.drawLeft(g, font, "إجمالي الأرباح", x + 8, y + 19, Theme.TEXT_MUTED);
        GuiDraw.glowLeft(g, font, snap.totalEarned() + "$", x + 8, y + 30, Theme.SUCCESS);

        int total = snap.runsOk() + snap.runsFail();
        GuiDraw.drawRight(g, font, "عمليات ناجحة", x + w - 8, y + 19, Theme.TEXT_MUTED);
        GuiDraw.glowRight(g, font, snap.runsOk() + " / " + total, x + w - 8, y + 30, Theme.TEXT);

        // Reliability bar. Green for the share delivered, red for the share lost - one glance and
        // the player knows whether they are actually good at this.
        float rate = total == 0 ? 0.0F : (float) snap.runsOk() / total;
        int bx = x + 8;
        int bw = w - 16;
        g.fill(bx, y + 44, bx + bw, y + 49, Theme.withAlpha(Theme.DANGER, 0x80));
        if (rate > 0.0F) {
            g.fill(bx, y + 44, bx + Math.round(bw * rate), y + 49, Theme.SUCCESS);
        }
        GuiDraw.outline(g, bx - 1, y + 43, bw + 2, 7, Theme.BORDER_SOFT);
        if (total > 0) {
            GuiDraw.drawCentered(g, font, Math.round(rate * 100) + "%", x + w / 2, y + 33, Theme.TEXT_DIM);
        }

        /* ---- recent runs ---- */
        int ry = y + headline + 4;
        int rh = h - (ry - y) - 4;
        GuiDraw.panel(g, font, x, ry, w, rh, "آخر العمليات");

        if (snap.recent().isEmpty()) {
            GuiDraw.drawCentered(g, font, "لم تقم بأي عملية بيع بعد",
                    x + w / 2, ry + rh / 2 - 4, Theme.TEXT_MUTED);
            return;
        }

        long now = System.currentTimeMillis();
        int line = ry + 18;
        for (TerminalSnapshot.RunView run : snap.recent()) {
            if (line + 10 > ry + rh - 2) {
                break;
            }
            int color = run.success() ? Theme.SUCCESS : Theme.DANGER;

            g.fill(x + 8, line + 1, x + 11, line + 7, color);
            GuiDraw.drawLeft(g, font, run.success() ? "✔" : "✖", x + 15, line, color);
            GuiDraw.drawLeft(g, font, run.success() ? "+" + run.payout() + "$" : "فشلت",
                    x + 26, line, color);
            GuiDraw.drawCentered(g, font, run.crates() + " صندوق", x + w / 2 + 10, line, Theme.TEXT_MUTED);
            GuiDraw.drawRight(g, font, GuiDraw.duration(now - run.at()) + " مضت",
                    x + w - 8, line, Theme.TEXT_MUTED);
            line += 10;
        }
    }

    /* =============================================================== upgrades */

    private void renderUpgrades(GuiGraphics g, TerminalSnapshot snap, int x, int y, int w, int h,
                                int mouseX, int mouseY) {
        // Sized to fit all five levels plus the header - the tree must never clip, which is the
        // same overflow that hid the weapon boxes in the production grid.
        int treeH = 30 + UpgradePath.MAX_LEVEL * 15 + 6;
        GuiDraw.panel(g, font, x, y, w, treeH, "شجرة التطويرات");

        UpgradePath[] paths = UpgradePath.values();
        int colW = (w - 16) / paths.length;
        int nodeW = Math.min(colW - 8, 30);
        int nodeH = 13;

        for (int p = 0; p < paths.length; p++) {
            int colX = x + 8 + p * colW;
            int centerX = colX + colW / 2;
            int hue = Theme.path(p);

            // Column header carries the path's colour, so the tree reads as four tracks.
            g.fill(colX + 2, y + 16, colX + colW - 4, y + 17, Theme.withAlpha(hue, 0xA0));
            GuiDraw.glowCentered(g, font, paths[p].display(), centerX, y + 19, hue);

            // The spine every node sits on, so levels read as a progression rather than a stack.
            g.fill(centerX - 1, y + 30, centerX + 1, y + 30 + UpgradePath.MAX_LEVEL * (nodeH + 2),
                    Theme.withAlpha(hue, 0x28));

            for (int level = UpgradePath.MAX_LEVEL; level >= 1; level--) {
                TerminalSnapshot.UpgradeView node = findNode(snap, p, level);
                if (node == null) {
                    continue;
                }
                int ny = y + 30 + (UpgradePath.MAX_LEVEL - level) * (nodeH + 2);
                int nx = centerX - nodeW / 2;
                boolean chosen = p == upgradePath && level == upgradeLevel;
                boolean hover = mouseX >= nx && mouseX < nx + nodeW && mouseY >= ny && mouseY < ny + nodeH;
                boolean next = node.purchasable() && !node.owned();

                if (node.owned()) {
                    // Bought: solid, saturated, unmistakably "mine".
                    g.fillGradient(nx, ny, nx + nodeW, ny + nodeH,
                            Theme.withAlpha(hue, 0xC0), Theme.withAlpha(hue, 0x70));
                    GuiDraw.outline(g, nx, ny, nodeW, nodeH, hue);
                    GuiDraw.glowCentered(g, font, "✔ " + CargoType.tierNumeral(level),
                            centerX, ny + 3, 0xFF101010);

                } else if (next) {
                    /*
                     * The single node the player can actually buy right now, breathing on a 1.2s
                     * cycle. Exactly one node per path can be in this state, so the animation marks
                     * a decision rather than adding noise.
                     */
                    float pulse = (Mth.sin((System.currentTimeMillis() % 1200L) / 1200.0F * Mth.TWO_PI) + 1.0F) * 0.5F;
                    int glow = 0x50 + (int) (pulse * 0x70);
                    GuiDraw.outline(g, nx - 2, ny - 2, nodeW + 4, nodeH + 4, Theme.withAlpha(hue, glow));
                    g.fillGradient(nx, ny, nx + nodeW, ny + nodeH,
                            Theme.withAlpha(hue, 0x60), Theme.withAlpha(hue, 0x24));
                    GuiDraw.outline(g, nx, ny, nodeW, nodeH, hue);
                    GuiDraw.glowCentered(g, font, CargoType.tierNumeral(level), centerX, ny + 3, hue);

                } else {
                    // Locked: readable, but visibly out of reach.
                    g.fill(nx, ny, nx + nodeW, ny + nodeH, Theme.withAlpha(hue, 0x0E));
                    GuiDraw.outline(g, nx, ny, nodeW, nodeH, Theme.BORDER_SOFT);
                    GuiDraw.drawCentered(g, font, "🔒", centerX, ny + 3, Theme.TEXT_MUTED);
                }

                if (hover || chosen) {
                    GuiDraw.outline(g, nx - 1, ny - 1, nodeW + 2, nodeH + 2,
                            chosen ? Theme.TEXT : Theme.withAlpha(hue, 0xB0));
                }

                int fp = p;
                int fl = level;
                hits.add(new Hit(nx, ny, nodeW, nodeH, () -> {
                    upgradePath = fp;
                    upgradeLevel = fl;
                }));
            }
        }

        int dy = y + treeH + 4;
        int dh = h - (dy - y) - 4;
        TerminalSnapshot.UpgradeView node = findNode(snap, upgradePath, upgradeLevel);
        GuiDraw.panel(g, font, x, dy, w, dh, node == null ? "التفاصيل"
                : paths[node.path()].display() + " " + CargoType.tierNumeral(node.level()));

        if (node == null) {
            GuiDraw.drawCentered(g, font, "اضغط على أي ترقية لعرض تفاصيلها",
                    x + w / 2, dy + dh / 2 - 4, Theme.TEXT_MUTED);
            return;
        }

        int hue = Theme.path(node.path());
        // Colour spine tying the detail panel back to the column it came from.
        g.fill(x + 2, dy + 2, x + 4, dy + dh - 2, Theme.withAlpha(hue, 0xC0));

        int line = dy + 17;
        for (String desc : node.description()) {
            GuiDraw.drawRight(g, font, desc, x + w - 8, line, Theme.TEXT_DIM);
            line += 9;
        }
        line += 2;
        for (TerminalSnapshot.GateView gate : node.gates()) {
            boolean met = gate.current() >= gate.need();
            GuiDraw.drawLeft(g, font, met ? "✔" : "✖", x + 8, line, met ? Theme.SUCCESS : Theme.DANGER);
            GuiDraw.drawLeft(g, font, gate.current() + "/" + gate.need(), x + 20, line,
                    met ? Theme.TEXT_DIM : Theme.DANGER);
            // Progress toward each gate, so a locked upgrade shows how far off it is instead of
            // just refusing. A bar the player watches fill is worth more than a number.
            GuiDraw.thinBar(g, x + 52, line + 2, 40, 3,
                    gate.need() <= 0 ? 1.0F : (float) gate.current() / gate.need(),
                    met ? Theme.SUCCESS : hue);
            GuiDraw.drawRight(g, font, gate.cargoDisplay() + " " + gate.tierLabel(),
                    x + w - 8, line, Theme.TEXT_DIM);
            line += 10;
        }

        boolean affordable = snap.balance() >= node.cost();
        GuiDraw.glowLeft(g, font, node.cost() + "$", x + 8, dy + dh - 16,
                affordable ? Theme.SUCCESS : Theme.DANGER);

        boolean enabled = node.purchasable() && !node.owned() && affordable;
        String label = node.owned() ? "مفعلة"
                : !node.purchasable() ? "مقفلة"
                : !affordable ? "نقود غير كافية"
                : "شراء الترقية";
        button(g, x + w - 90, dy + dh - 18, 82, 14, label, enabled, mouseX, mouseY,
                () -> ModNetwork.sendAction(TerminalAction.BUY_UPGRADE, "", node.path(), node.level()));
    }

    private static TerminalSnapshot.UpgradeView findNode(TerminalSnapshot snap, int path, int level) {
        if (path < 0 || level < 0) {
            return null;
        }
        for (TerminalSnapshot.UpgradeView node : snap.upgrades()) {
            if (node.path() == path && node.level() == level) {
                return node;
            }
        }
        return null;
    }

    /* ================================================================ widgets */

    /**
     * The one button style in the mod.
     *
     * <p>A disabled button still registers a hit region. Clicking it plays a refusal sound instead
     * of doing nothing at all - silence reads as "the UI is broken", a distinct noise reads as "not
     * yet", and the difference is most of what makes an interface feel trustworthy.</p>
     */
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, boolean enabled,
                        int mouseX, int mouseY, Runnable action) {
        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int base = enabled ? Theme.accent() : Theme.TEXT_MUTED;

        // Enabled buttons swell by a pixel under the cursor and gain an outer halo.
        int lift = enabled && hover ? 1 : 0;
        int bx = x - lift;
        int by = y - lift;
        int bw = w + lift * 2;
        int bh = h + lift * 2;

        if (enabled && hover) {
            GuiDraw.outline(g, bx - 1, by - 1, bw + 2, bh + 2, Theme.withAlpha(base, 0x70));
        }
        g.fillGradient(bx, by, bx + bw, by + bh,
                Theme.withAlpha(base, enabled ? (hover ? 0xA0 : 0x50) : 0x1A),
                Theme.withAlpha(base, enabled ? 0x20 : 0x08));
        GuiDraw.outline(g, bx, by, bw, bh, enabled ? base : Theme.BORDER_SOFT);

        if (enabled) {
            GuiDraw.glowCentered(g, font, label, bx + bw / 2, by + (bh - 8) / 2, Theme.TEXT);
        } else {
            GuiDraw.drawCentered(g, font, label, bx + bw / 2, by + (bh - 8) / 2, Theme.TEXT_MUTED);
        }

        hits.add(new Hit(x, y, w, h, enabled ? action : this::denied));
    }

    /* ================================================================ feedback */

    private long flashUntil;
    private int flashColor;
    private int prevCraftCount = -1;
    private int prevOwnedUpgrades = -1;

    /**
     * Detects outcomes by diffing consecutive snapshots and celebrates them.
     *
     * <p>The server never sends "you succeeded" - it sends state. Watching that state change is
     * enough: one more assembly line than last frame means a craft started, one more owned node
     * means an upgrade landed. Deriving it on the client keeps the packet vocabulary closed and
     * still lands the payoff on the exact frame the change becomes visible.</p>
     */
    private void detectOutcomes(TerminalSnapshot snap) {
        int owned = 0;
        for (TerminalSnapshot.UpgradeView node : snap.upgrades()) {
            if (node.owned()) {
                owned++;
            }
        }

        if (prevCraftCount >= 0 && snap.craftCount() > prevCraftCount) {
            flash(Theme.accent());
            play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.8F);
        }
        if (prevOwnedUpgrades >= 0 && owned > prevOwnedUpgrades) {
            flash(Theme.SUCCESS);
            play(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5F, 1.4F);
        }

        prevCraftCount = snap.craftCount();
        prevOwnedUpgrades = owned;
    }

    private void flash(int color) {
        flashColor = color;
        flashUntil = System.currentTimeMillis() + 550L;
    }

    /** An expanding ring around the whole terminal - visible wherever the player is looking. */
    private void renderFlash(GuiGraphics g) {
        long remaining = flashUntil - System.currentTimeMillis();
        if (remaining <= 0L) {
            return;
        }
        float t = 1.0F - remaining / 550.0F;
        int spread = Math.round(t * 7.0F);
        int alpha = Math.round((1.0F - t) * 200.0F);
        GuiDraw.outline(g, left - spread, top - spread,
                panelW + spread * 2, panelH + spread * 2, Theme.withAlpha(flashColor, alpha));
        GuiDraw.outline(g, left - spread - 1, top - spread - 1,
                panelW + spread * 2 + 2, panelH + spread * 2 + 2, Theme.withAlpha(flashColor, alpha / 2));
    }

    private void denied() {
        play(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 0.4F, 1.6F);
    }

    private void play(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        if (minecraft != null) {
            minecraft.getSoundManager().play(
                    new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                            sound.getLocation(), net.minecraft.sounds.SoundSource.MASTER,
                            volume, pitch, net.minecraft.util.RandomSource.create(), false, 0,
                            net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                            0.0D, 0.0D, 0.0D, true));
        }
    }

    /* ================================================================== input */

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Reverse order so the control drawn last - visually on top - wins an overlap.
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(mouseX, mouseY)) {
                    // A crisp tick on every hit. Refusals override it with their own sound from
                    // inside the action, so the two are never confused.
                    play(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.35F, 1.5F);
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (tab == Tab.MANIFEST) {
            manifestScroll = Math.max(0, manifestScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
