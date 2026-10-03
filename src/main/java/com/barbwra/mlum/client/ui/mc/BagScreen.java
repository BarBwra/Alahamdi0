package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.BagEntry;
import com.barbwra.mlum.bag.BagGrid;
import com.barbwra.mlum.bag.BagSection;
import com.barbwra.mlum.bag.ItemSize;
import com.barbwra.mlum.client.ClientBagState;
import com.barbwra.mlum.client.ClientFactionData;
import com.barbwra.mlum.client.ui.Hits;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.view.InvView;
import com.barbwra.mlum.client.ui.view.Item;
import com.barbwra.mlum.client.ui.view.Overlays;
import com.barbwra.mlum.client.ui.view.Slots;
import com.barbwra.mlum.compat.TaczAttachments;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.menu.AttachmentContainer;
import com.barbwra.mlum.menu.MlumMenu;
import com.barbwra.mlum.menu.slot.AttachmentSlot;
import com.barbwra.mlum.menu.slot.BackpackSlot;
import com.barbwra.mlum.network.C2SBagAct;
import com.barbwra.mlum.network.C2SBagMove;
import com.barbwra.mlum.network.C2SBagSort;
import com.barbwra.mlum.network.C2SVaultPage;
import com.barbwra.mlum.network.ModNetwork;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The bag - and the bag with a chest or a faction vault page open beside it.
 *
 * <p>A container screen, because the bag holds real slots the server owns, but none of vanilla's
 * drawing: every frame is the design's {@link InvView} built from the live menu, painted through
 * the UI canvas. Clicks are routed by what the frame made clickable, not by slot rectangles.</p>
 *
 * <p>Two kinds of "holding" exist, as they do in the design. An item picked up from the grid is
 * held by this screen - it has a position and a footprint - and dropping it asks the server to move
 * it. Anything picked up from an ordinary slot (quick access, gear, a firearm, a chest cell) is on
 * the vanilla cursor as usual, and dropping it on the grid puts it in a free inventory slot and then
 * moves it to the cell that was aimed at. Either way the green or red box shows where it will land,
 * and the item follows the cursor at its full bag size.</p>
 */
@OnlyIn(Dist.CLIENT)
public class BagScreen extends AbstractContainerScreen<MlumMenu> implements UiPage {

    /** View position of a mount (scope, muzzle, laser / grip, mag, stock) to its attachment slot. */
    private static final int[] VIEW_TO_SLOT = {0, 1, 5, 3, 2, 4};

    /** Vanilla's "you clicked outside the window" slot id, which is how a carried stack is thrown. */
    private static final int OUTSIDE = -999;

    /* ---- the item picked up from the grid ---- */
    @Nullable
    private BagEntry held;
    private BagSection heldSection = BagSection.BASE;
    private int heldRow;
    private int heldCol;
    /**
     * Whether the whole stack is in hand.
     *
     * <p>A flag rather than a number, because "all of it" has to keep meaning all of it while the
     * stack itself is changing under the cursor - placing one at a time shrinks the cell, a
     * double-click grows it. A count frozen at pick-up time would go stale on the first of either.
     * When this is false, {@link #heldCount} is the fixed amount that was split off.</p>
     */
    private boolean heldAll = true;
    private int heldCount;
    /** When the current hold started, for the double-click that follows it. */
    private long grabAt;

    /* ---- a move sent to the server, drawn at its target until the answer arrives ---- */
    private int moveVersion = -1;
    private long moveAt;
    private BagSection moveFrom;
    private int moveFromRow;
    private int moveFromCol;
    private BagSection moveTo;
    private int moveToRow;
    private int moveToCol;

    /* ---- a stack from the vanilla cursor, dropped into a free slot and then moved to its cell ---- */
    private int pendingSource = -1;
    private BagSection pendingSection = BagSection.BASE;
    private int pendingRow;
    private int pendingCol;
    private long pendingAt;

    /** The selected thing, e.g. "qa:2" - it stays in the details panel when nothing is hovered. */
    @Nullable
    private String sel;

    /** The bag entries in the order the view's "bag:i" keys refer to them. */
    private final List<BagEntry> entries = new ArrayList<>();
    private final List<BagSection> entrySections = new ArrayList<>();

    public BagScreen(MlumMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        UiBoot.ensure();
        UiState.releaseFx();
        if (!shown) {
            shown = true;
            if (menu.isVault()) {
                openVault();
            }
        }
    }

    /** init() runs again on every resize; the vault's door only opens once per screen. */
    private boolean shown;
    /** When the last vault screen went away, so a page turn can be told from a fresh opening. */
    private static long vaultGoneAt;

    /**
     * A fresh opening unlocks the door; a page turn (the menu reopens within a moment of closing)
     * only spins the dial, since the vault is already open.
     */
    private void openVault() {
        long now = UiState.now();
        boolean turn = now - vaultGoneAt < 1500L;
        com.barbwra.mlum.client.ui.view.VaultView.dialAt = now;
        com.barbwra.mlum.client.ui.view.VaultView.doorAt = turn ? -1L : now;
        if (turn) {
            com.barbwra.mlum.client.ui.view.VaultView.openShutter();
        }
        UiSounds.vault(!turn);
    }

    /* ================================================================== UiPage */

    @Override
    public int tab() {
        return 0;
    }

    @Override
    public boolean bagScrim() {
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        validateHeld();
        applyPendingPlacement();
        Hits.Hit under = UiHost.hitUnderMouse();
        this.hoveredSlot = under == null ? null : slotForKey(under.id);
        if (this.hoveredSlot == null && under != null && under.id.startsWith("bag:") && under.data instanceof Integer i) {
            BagEntry e = i >= 0 && i < entries.size() ? entries.get(i) : null;
            if (e != null && !e.ownedByPack()) {
                this.hoveredSlot = menuSlotOfInventory(e.source());
            }
        }
        UiHost.render(graphics, this);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // the scrim is part of the frame
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // never called - render() does not hand over to vanilla
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
    }

    @Override
    public Node main(String hover) {
        return InvView.build(model(hover));
    }

    @Override
    public Node modal(String hover) {
        if (rentOpen && menu.isVault()) {
            InvView.Chest ch = new InvView.Chest();
            ch.pages = menu.vaultPages();
            ch.rentLeft = menu.rentLeft();
            ch.bank = menu.vaultBank();
            ch.canPay = menu.canPayRent();
            ch.level = menu.factionLevel();
            return com.barbwra.mlum.client.ui.view.VaultView.rentModal(ch, hover);
        }
        return UiState.storeModal(hover);
    }

    /** The vault stands on its own: no tab bar, no other menus to switch to. */
    @Override
    public boolean chrome() {
        return !menu.isVault();
    }

    /** The vault's rent dialog is open. */
    private boolean rentOpen;

    /** Whether page {@code target} (1-based) is sealed for want of rent, as far as this client knows. */
    private boolean sealed(int target) {
        return target > 1 && menu.rentLeft() <= 0;
    }

    /** Turns the vault to {@code target}: the rent dialog for a sealed page, else the shutter and the turn. */
    private void turnTo(int target) {
        if (!menu.isVault() || target < 1 || target > menu.vaultPages() || target == menu.vaultPage()
                || held != null || handsFull()) {
            return;
        }
        if (sealed(target)) {
            rentOpen = true;
            return;
        }
        com.barbwra.mlum.client.ui.view.VaultView.closeShutter(target > menu.vaultPage() ? 1 : -1);
        ModNetwork.CHANNEL.sendToServer(new C2SVaultPage(target));
    }

    @Override
    public Node ghost(int mouseX, int mouseY) {
        ItemStack stack = held != null ? held.stack() : menu.getCarried();
        Item it = McItems.of(stack);
        if (it == null) {
            return null;
        }
        int carrying = heldAmount();
        if (held != null && carrying != stack.getCount()) {
            it = it.withCount(carrying);
        }
        return Overlays.ghost(it, mouseX / Px.s, mouseY / Px.s);
    }

    /** How much of {@link #held} is in hand right now. Zero when nothing is held. */
    private int heldAmount() {
        if (held == null) {
            return 0;
        }
        int there = held.stack().getCount();
        return heldAll ? there : Math.max(0, Math.min(heldCount, there));
    }

    /* ================================================================== the model */

    private InvView.Model model(String hover) {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        InvView.Model m = new InvView.Model();
        m.hover = hover;
        m.showPlayer = MlumConfig.showPlayerModel();

        /* ---- the bag ---- */
        entries.clear();
        entrySections.clear();
        BagGrid base = ClientBagState.base();
        BagGrid pack = ClientBagState.pack();
        boolean moving = moveVersion == ClientBagState.version() && UiState.now() - moveAt < 1500L;
        for (int s = 0; s < 2; s++) {
            BagGrid grid = s == 0 ? base : pack;
            BagSection section = s == 0 ? BagSection.BASE : BagSection.PACK;
            for (BagEntry entry : grid.entries()) {
                Item it = McItems.of(entry.stack());
                if (it == null) {
                    continue;
                }
                InvView.Entry e = new InvView.Entry();
                e.item = it;
                e.row = entry.row();
                e.col = entry.col();
                e.pack = section == BagSection.PACK;
                if (moving && section == moveFrom && entry.row() == moveFromRow && entry.col() == moveFromCol) {
                    e.row = moveToRow;
                    e.col = moveToCol;
                    e.pack = moveTo == BagSection.PACK;
                }
                if (entry == held) {
                    m.heldEntry = m.entries.size();
                    m.heldRemain = Math.max(0, entry.stack().getCount() - heldAmount());
                }
                m.entries.add(e);
                entries.add(entry);
                entrySections.add(section);
            }
        }
        m.packRows = pack.rows();
        ItemStack packStack = slotStack(menu.backpackIndex);
        if (!packStack.isEmpty()) {
            m.pack = McItems.of(packStack);
            m.packName = m.pack.name;
            m.packAdd = BagConfig.addedCells(packStack);
            m.packVip = BagConfig.isVipOnly(packStack);
        }
        m.used = ClientBagState.usedCells();
        m.cap = ClientBagState.capacity();

        /* ---- quick access, gear ---- */
        for (int i = 0; i < MlumMenu.QUICK_SLOTS; i++) {
            m.quick[i] = McItems.of(slotStack(menu.quickStart + i));
        }
        if (player != null) {
            int selected = player.getInventory().selected;
            m.activeQuick = selected >= MlumMenu.WEAPON_SLOTS ? selected - MlumMenu.WEAPON_SLOTS : -1;
        }
        for (int g = 0; g < 6; g++) {
            m.gear[g] = McItems.of(slotStack(gearIndex(g)));
        }

        /* ---- the survivor ---- */
        if (player != null) {
            m.level = player.experienceLevel;
            m.name = player.getGameProfile().getName();
            m.faction = ClientFactionData.inFaction() ? UiText.logical(ClientFactionData.factionName()) : null;
            float max = Math.max(1.0F, player.getMaxHealth());
            float health = Mth.clamp(player.getHealth(), 0.0F, max);
            m.hpSeg = Math.round(health / max * 20.0F);
            m.hp = Mth.ceil(health);
            m.armor = player.getArmorValue();
            m.armorSeg = Math.min(20, m.armor);
            m.food = player.getFoodData().getFoodLevel();
            m.foodSeg = Math.min(20, m.food);
        }

        /* ---- weapons, or the chest ---- */
        for (int i = 0; i < MlumMenu.WEAPON_SLOTS; i++) {
            InvView.Gun g = m.guns[i];
            ItemStack gun = menu.getWeapon(i);
            g.item = McItems.of(gun);
            g.selected = menu.isWeaponSelected(i);
            if (g.item != null) {
                g.rounds = TaczCompat.loadedRounds(gun);
                g.reserve = player == null ? 0 : TaczCompat.reserveInInventory(player, gun);
            }
            for (int k = 0; k < 6; k++) {
                int index = menu.attachmentStart + i * AttachmentContainer.PER_GUN + VIEW_TO_SLOT[k];
                if (index >= menu.slots.size()) {
                    continue;
                }
                Slot slot = menu.slots.get(index);
                g.atts[k] = McItems.of(slot.getItem());
                g.ghosts[k] = slot instanceof AttachmentSlot a ? ghostOf(a.type()) : null;
            }
        }
        if (menu.getRows() > 0) {
            InvView.Chest ch = new InvView.Chest();
            ch.title = chestTitle();
            ch.rows = menu.getRows();
            ch.slots = new Item[ch.rows * 9];
            for (int i = 0; i < ch.slots.length; i++) {
                ch.slots[i] = McItems.of(slotStack(menu.containerStart + i));
            }
            ch.paged = menu.isVault();
            ch.owner = menu.isVault() ? UiText.logical(this.title.getString()) : "";
            ch.rentLeft = menu.rentLeft();
            ch.bank = menu.vaultBank();
            ch.canPay = menu.canPayRent();
            ch.level = menu.factionLevel();
            ch.page = menu.vaultPage() - 1;
            ch.pages = menu.vaultPages();
            ch.bodyId = menu.bodyId();
            m.chest = ch;
        }

        /* ---- what the details panel shows, selection, drop verdicts ---- */
        ItemStack carriedStack = held != null ? held.stack() : menu.getCarried();
        boolean holding = !carriedStack.isEmpty();
        ItemStack inspect = stackForKey(hover);
        if (inspect.isEmpty() && holding) {
            inspect = carriedStack;
        }
        if (inspect.isEmpty() && sel != null) {
            inspect = stackForKey(sel);
            if (inspect.isEmpty()) {
                sel = null;
            }
        }
        m.inspect = McItems.inspect(inspect);
        m.sel = sel;
        if (holding && hover != null) {
            Slot target = slotForKey(hover);
            if (target != null) {
                m.verdicts.put(hover, target.mayPlace(carriedStack) ? Slots.HOT : Slots.NOPE);
            }
        }
        if (holding) {
            Target t = targetAt(UiHost.mouseX, UiHost.mouseY, carriedStack);
            if (t != null) {
                m.dropRow = t.dr;
                m.dropCol = t.dc;
                m.dropW = t.w;
                m.dropH = t.h;
                m.dropOk = t.ok;
            }
        }
        litMounts(m, inspect);
        if (!holding && "bgrid".equals(hover)) {
            // Empty hands over an empty cell. "bgrid" is only ever the hover when no item node is
            // on top of the cursor, so this cannot light up a square that already holds something.
            int[] cell = cellAt(UiHost.mouseX, UiHost.mouseY);
            if (cell != null) {
                m.hoverRow = cell[0];
                m.hoverCol = cell[1];
            }
        }
        return m;
    }

    /**
     * An attachment that is selected, carried or pointed at lights every mount it would go on, on
     * both guns, so where it belongs can be seen before it is moved.
     */
    private void litMounts(InvView.Model m, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < MlumMenu.WEAPON_SLOTS; i++) {
            if (menu.getWeapon(i).isEmpty()) {
                continue;
            }
            for (int k = 0; k < 6; k++) {
                int index = menu.attachmentStart + i * AttachmentContainer.PER_GUN + VIEW_TO_SLOT[k];
                if (index >= menu.slots.size()) {
                    continue;
                }
                Slot slot = menu.slots.get(index);
                String key = "att:" + i + ":" + k;
                if (slot instanceof AttachmentSlot && slot.getItem() != stack && !m.verdicts.containsKey(key)
                        && slot.mayPlace(stack)) {
                    m.verdicts.put(key, Slots.FIT);
                }
            }
        }
    }

    private static String ghostOf(AttachmentType type) {
        return switch (type) {
            case SCOPE -> "scope";
            case MUZZLE -> "muzzle";
            case EXTENDED_MAG -> "mag";
            case GRIP -> "grip";
            case STOCK -> "stock";
            case LASER -> "laser";
            default -> null;
        };
    }

    private String chestTitle() {
        if (menu.isVault()) {
            return "خزنة المنظمة";
        }
        if (this.title.getContents() instanceof TranslatableContents tc) {
            switch (tc.getKey()) {
                case "container.chest":
                    return "صندوق";
                case "container.chestDouble":
                    return "صندوق كبير";
                case "container.barrel":
                    return "برميل";
                case "container.enderchest":
                    return "صندوق إندر";
                default:
                    break;
            }
        }
        String text = UiText.logical(this.title.getString());
        return text.isEmpty() ? "صندوق" : text;
    }

    /* ================================================================== keys -> slots */

    private ItemStack slotStack(int index) {
        return index >= 0 && index < menu.slots.size() ? menu.slots.get(index).getItem() : ItemStack.EMPTY;
    }

    private int gearIndex(int g) {
        if (g < 4) {
            return menu.armorStart + g;
        }
        return g == 4 ? menu.offhandIndex : menu.backpackIndex;
    }

    /** The menu slot a view key stands for: quick access, gear, firearm, mount or chest cell. */
    @Nullable
    private Slot slotForKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        int index = -1;
        try {
            if (key.startsWith("qa:")) {
                index = menu.quickStart + Integer.parseInt(key.substring(3));
            } else if (key.startsWith("box:")) {
                index = menu.containerStart + Integer.parseInt(key.substring(4));
            } else if (key.startsWith("eq:")) {
                index = gearIndex(Integer.parseInt(key.substring(3)));
            } else if (key.startsWith("gun:")) {
                index = menu.gunStart + Integer.parseInt(key.substring(4));
            } else if (key.startsWith("att:")) {
                String[] p = key.split(":");
                int gun = Integer.parseInt(p[1]);
                int k = Integer.parseInt(p[2]);
                index = menu.attachmentStart + gun * AttachmentContainer.PER_GUN + VIEW_TO_SLOT[k];
            }
        } catch (RuntimeException malformed) {
            return null;
        }
        return index >= 0 && index < menu.slots.size() ? menu.slots.get(index) : null;
    }

    private ItemStack stackForKey(@Nullable String key) {
        if (key == null) {
            return ItemStack.EMPTY;
        }
        if (key.startsWith("bag:")) {
            try {
                int i = Integer.parseInt(key.substring(4));
                return i >= 0 && i < entries.size() ? entries.get(i).stack() : ItemStack.EMPTY;
            } catch (NumberFormatException bad) {
                return ItemStack.EMPTY;
            }
        }
        Slot slot = slotForKey(key);
        return slot == null ? ItemStack.EMPTY : slot.getItem();
    }

    @Nullable
    private Slot menuSlotOfInventory(int invSlot) {
        if (minecraft == null || minecraft.player == null) {
            return null;
        }
        for (int i = menu.invStart; i < menu.invEnd && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container == minecraft.player.getInventory() && slot.getContainerSlot() == invSlot) {
                return slot;
            }
        }
        return null;
    }

    /* ================================================================== where a drop lands */

    private static final class Target {
        boolean ok;
        BagSection sec;
        int r;
        int c;
        int dr;
        int dc;
        int w;
        int h;
    }

    /**
     * The design's {@code targetAt}: the item centred on the cursor, snapped to the grid, in the
     * section its rows fall in - base above the separator, pack below. Null when the cursor is not
     * near the grid at all.
     */
    @Nullable
    private Hits.Hit gridHit() {
        for (Hits.Hit h : UiHost.HITS.all()) {
            if ("bgrid".equals(h.id)) {
                return h;
            }
        }
        return null;
    }

    /**
     * The one cell the cursor is literally over, with no footprint centring.
     *
     * <p>{@link #targetAt} answers "where would this item land", which is a different question - it
     * shifts by half the item's size and clamps to the grid. The hover square has to be the cell
     * under the pointer and nothing else, or it would light up a neighbour.</p>
     */
    @Nullable
    private int[] cellAt(int mouseX, int mouseY) {
        Hits.Hit grid = gridHit();
        if (grid == null) {
            return null;
        }
        float k = (grid.x1 - grid.x0) / 392.0F;
        if (k <= 0.0F) {
            return null;
        }
        // the grid is laid out right to left, so column 0 is at its right edge
        float lx = (grid.x1 - mouseX) / k;
        float ly = (mouseY - grid.y0) / k;
        float pitch = InvView.PITCH;
        if (lx < 0.0F || lx >= 9 * pitch) {
            return null;
        }
        int packRows = ClientBagState.pack().rows();
        int row;
        if (ly < 3 * pitch) {
            row = (int) (ly / pitch);
        } else if (ly < 3 * pitch + InvView.SEPH) {
            return null;   // the backpack's label strip, not a cell
        } else {
            row = 3 + (int) ((ly - 3 * pitch - InvView.SEPH) / pitch);
        }
        if (row < 0 || row >= 3 + packRows) {
            return null;
        }
        int col = (int) (lx / pitch);
        return col < 0 || col > 8 ? null : new int[]{row, col};
    }

    @Nullable
    private Target targetAt(int mouseX, int mouseY, ItemStack stack) {
        Hits.Hit grid = gridHit();
        if (grid == null || stack.isEmpty()) {
            return null;
        }
        float k = (grid.x1 - grid.x0) / 392.0F;
        if (k <= 0.0F) {
            return null;
        }
        float lx = (grid.x1 - mouseX) / k;
        float ly = (mouseY - grid.y0) / k;
        ItemSize size = BagConfig.sizeOf(stack);
        int w = size.width();
        int h = size.height();
        int packRows = ClientBagState.pack().rows();
        int rows = 3 + packRows;
        float pitch = InvView.PITCH;
        if (lx < -pitch || lx > 392 + pitch || ly < -pitch || ly > InvView.yOf(rows - 1) + InvView.CELLW + pitch) {
            return null;
        }
        float rowF = ly < 3 * pitch + InvView.SEPH / 2 ? ly / pitch : (ly - InvView.SEPH) / pitch;
        int c0 = Math.round(lx / pitch - w / 2.0F);
        int r0 = Math.round(rowF - h / 2.0F);
        Target t = new Target();
        t.w = w;
        t.h = h;
        t.r = r0;
        t.c = c0;
        if (r0 >= 0 && r0 + h <= 3) {
            t.sec = BagSection.BASE;
        } else if (packRows > 0 && r0 >= 3 && r0 + h <= rows) {
            t.sec = BagSection.PACK;
            t.r = r0 - 3;
        }
        if (t.sec != null) {
            t.ok = landing(ClientBagState.grid(t.sec), t.r, t.c, size,
                    t.sec == heldSection ? held : null, stack)
                    && !(t.sec == BagSection.PACK && BagConfig.isBackpack(stack));
        }
        t.dr = Math.max(0, Math.min(rows - h, r0));
        t.dc = Math.max(0, Math.min(9 - w, c0));
        return t;
    }

    /**
     * Whether a drop at this footprint would be accepted - the same three-way reading the server
     * makes: empty is a placement, one stack of the same item is a merge, one of something else is a
     * swap, anything more is refused.
     *
     * <p>Only a verdict for the preview box. The server tests it again against its own grid before it
     * moves anything, so a client that answered generously here would simply be corrected.</p>
     */
    private boolean landing(BagGrid grid, int row, int col, ItemSize size,
                            @Nullable BagEntry ignore, ItemStack stack) {
        if (!grid.inBounds(row, col, size)) {
            return false;
        }
        List<BagEntry> blocking = grid.overlapping(row, col, size, ignore);
        if (blocking.isEmpty()) {
            return true;
        }
        if (blocking.size() != 1) {
            return false;
        }
        BagEntry dest = blocking.get(0);
        if (BagGrid.canMerge(dest.stack(), stack)) {
            return true;
        }
        // A swap, which only a grid-held whole stack can do: something from the vanilla cursor has
        // no cell of its own to send the displaced item back to.
        if (held == null || heldAmount() < held.stack().getCount()) {
            return false;
        }
        if (heldSection == BagSection.PACK && BagConfig.isBackpack(dest.stack())) {
            return false;
        }
        return ClientBagState.grid(heldSection).freeExcept(heldRow, heldCol, dest.size(), held, dest);
    }

    /* ================================================================== clicks */

    @Override
    public boolean mouseClicked(double guiX, double guiY, int button) {
        Hits.Hit hit = UiHost.hitUnderMouse();
        // the store hangs off the top bar, so it is offered every click before the bag sees one
        if (UiState.storeClick(hit)) {
            return true;
        }
        String id = hit == null ? null : hit.id;
        if (id == null) {
            /*
             * Nothing under the cursor - the dark edges of the screen. Vanilla throws a carried stack
             * on the ground here, and so does this: it is the only gesture players reach for, and
             * before this the click was swallowed and items simply could not be got rid of.
             */
            if (held != null) {
                dropHeldToWorld(button == 1 ? 1 : heldAmount());
            } else if (!menu.getCarried().isEmpty()) {
                slotClicked(null, OUTSIDE, button, ClickType.PICKUP);
                UiSounds.tick(false);
            }
            return true;
        }
        boolean shift = Screen.hasShiftDown();
        if (id.startsWith("tab:") && hit.data instanceof Integer tab) {
            if (!handsFull()) {
                leaveTo(tab);
            }
            return true;
        }
        if (id.equals("nav:a") || id.equals("nav:d")) {
            if (!handsFull()) {
                UiState.keyHit(id.charAt(4));
                leaveTo(UiScreens.step(0, id.equals("nav:a") ? 1 : -1));
            }
            return true;
        }
        if (id.equals("sort")) {
            if (held == null && menu.getCarried().isEmpty()) {
                ModNetwork.CHANNEL.sendToServer(C2SBagSort.INSTANCE);
            }
            return true;
        }
        if (id.equals("lootall")) {
            sendButton(MlumMenu.BTN_LOOT_CHEST);
            return true;
        }
        if (id.equals("pg:-1") || id.equals("pg:1") || (id.startsWith("vpg:") && hit.data instanceof Integer)) {
            int target = id.startsWith("vpg:") ? (Integer) hit.data + 1 : menu.vaultPage() + (id.equals("pg:1") ? 1 : -1);
            turnTo(target);
            return true;
        }
        if (id.equals("vrent.open")) {
            rentOpen = true;
            return true;
        }
        if (rentOpen && (id.equals("close") || id.equals("modal-bg"))) {
            rentOpen = false;
            return true;
        }
        if (rentOpen && id.startsWith("vrent:")) {
            ModNetwork.CHANNEL.sendToServer(new com.barbwra.mlum.network.C2SVaultRent(Integer.parseInt(id.substring(6))));
            rentOpen = false;
            return true;
        }
        if (rentOpen) {
            return true;
        }
        if (id.startsWith("bag:") && hit.data instanceof Integer i) {
            clickBagItem(i, button, shift);
            return true;
        }
        if (id.equals("bgrid")) {
            if (held != null) {
                dropHeld(button);
            } else if (!menu.getCarried().isEmpty()) {
                placeCarried(button);
            }
            return true;
        }
        Slot slot = slotForKey(id);
        if (slot != null) {
            clickSlot(id, slot, button, shift);
        }
        return true;
    }

    private void clickBagItem(int i, int button, boolean shift) {
        if (held != null) {
            dropHeld(button);
            return;
        }
        BagEntry entry = i >= 0 && i < entries.size() ? entries.get(i) : null;
        if (entry == null) {
            return;
        }
        BagSection section = entrySections.get(i);
        if (!menu.getCarried().isEmpty()) {
            if (!entry.ownedByPack()) {
                // onto an item that is a real slot: vanilla merges or swaps, as it always has
                Slot slot = menuSlotOfInventory(entry.source());
                if (slot != null) {
                    slotClicked(slot, slot.index, button, ClickType.PICKUP);
                }
            } else {
                placeCarried(button);
            }
            return;
        }
        if (shift) {
            quickMove(entry, section);
            return;
        }
        /*
         * Left picks the whole stack up, right picks up half - the two gestures every container in
         * the game uses. A grid item is not on the vanilla cursor while it is held; it stays in its
         * cell and is drawn under the pointer, which is what lets a 7x2 rifle show its real size.
         */
        held = entry;
        heldSection = section;
        heldRow = entry.row();
        heldCol = entry.col();
        heldAll = button != 1;
        heldCount = heldAll ? entry.stack().getCount() : (entry.stack().getCount() + 1) / 2;
        grabAt = UiState.now();
        sel = null;
        UiSounds.tick(true);
    }

    private void clickSlot(String key, Slot slot, int button, boolean shift) {
        if (held != null) {
            moveHeldInto(key, slot);
            return;
        }
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && !slot.mayPlace(carried)) {
            explainRefusal(key, slot, carried);
            return;
        }
        boolean hadCarried = !carried.isEmpty();
        slotClicked(slot, slot.index, button, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
        if (!shift) {
            boolean nowCarried = !menu.getCarried().isEmpty();
            if (!hadCarried && nowCarried) {
                UiSounds.tick(true);
            } else if (hadCarried) {
                UiSounds.tick(false);
            }
        }
        sel = key;
    }

    private void explainRefusal(String key, Slot slot, ItemStack stack) {
        if (key.startsWith("qa:") && TaczCompat.isGun(stack)) {
            UiState.toast("الأسلحة ما تنحط في الوصول السريع. حطها في خانة الأسلحة.", true);
        } else if (slot instanceof BackpackSlot bp) {
            UiState.toast(bp.rejection(stack) == BackpackSlot.Reason.VIP_ONLY
                    ? "هذي الشنطة للاعبين اللي عندهم {b}VIP{/b} بس" : "هذي الخانة للشنط بس", true);
        } else if (key.startsWith("gun:")) {
            UiState.toast("خانة الأسلحة للمسدسات والرشاشات بس", true);
        } else if (key.startsWith("att:")) {
            UiState.toast(TaczAttachments.isAttachment(stack) ? "هذي القطعة ما تركب هنا" : "هذي الخانة لقطع السلاح بس", true);
        }
    }

    /** Held grid item onto a slot: base items are real slots (two vanilla clicks), pack items go through the server. */
    private void moveHeldInto(String key, Slot target) {
        BagEntry entry = held;
        if (entry == null) {
            return;
        }
        ItemStack stack = entry.stack();
        if (!target.mayPlace(stack)) {
            explainRefusal(key, target, stack);
            return;
        }
        if (entry.ownedByPack()) {
            if (target.hasItem()) {
                UiState.toast("الخانة مشغولة. فضّها أول", true);
                return;
            }
            ModNetwork.CHANNEL.sendToServer(new C2SBagMove(menu.containerId, C2SBagMove.Place.BAG_PACK, heldRow, heldCol,
                    C2SBagMove.Place.SLOT, target.index, 0));
        } else {
            Slot from = menuSlotOfInventory(entry.source());
            if (from == null || !menu.getCarried().isEmpty()) {
                cancelHold();
                return;
            }
            // half in hand is vanilla's right-click pick-up, so ask for the same half back
            slotClicked(from, from.index, heldAll ? 0 : 1, ClickType.PICKUP);
            slotClicked(target, target.index, 0, ClickType.PICKUP);
        }
        held = null;
        sel = key;
        UiSounds.tick(false);
    }

    /**
     * Puts the held item down: all of it on a left click, one on a right click.
     *
     * <p>Right-clicking the cell it came from within the double-click window means the player
     * double-clicked it, which gathers instead - the same shortcut every vanilla container has, and
     * the reason a bag full of loose wood could never be tidied by hand.</p>
     */
    private void dropHeld(int button) {
        BagEntry entry = held;
        if (entry == null) {
            return;
        }
        Target t = targetAt(UiHost.mouseX, UiHost.mouseY, entry.stack());
        if (t == null) {
            cancelHold();
            return;
        }
        boolean samePlace = t.sec == heldSection && t.r == heldRow && t.c == heldCol;
        if (button == 0 && samePlace && UiState.now() - grabAt < 350L && entry.stack().isStackable()) {
            ModNetwork.CHANNEL.sendToServer(
                    C2SBagAct.gather(menu.containerId, heldSection, heldRow, heldCol));
            grabAt = 0L;
            heldAll = true;
            UiSounds.tick(true);
            return;
        }
        if (samePlace) {
            // back where it started: just let go, no packet at all
            held = null;
            UiSounds.tick(false);
            return;
        }
        if (!t.ok) {
            UiState.toast(t.sec == BagSection.PACK && BagConfig.isBackpack(entry.stack())
                    ? "ما تقدر تحط شنطة داخل شنطة" : "ما فيه مكان كافي هنا", true);
            return;
        }
        int amount = button == 1 ? 1 : heldAmount();
        if (button == 1 && t.sec != null) {
            // One at a time only makes sense onto an empty cell or a stack of the same thing. Onto
            // something else the move is a swap, and a swap is all-or-nothing - asking for one would
            // be refused by the server and read to the player as a dead click.
            BagEntry dest = ClientBagState.grid(t.sec).at(t.r, t.c);
            if (dest != null && dest != entry && !BagGrid.canMerge(dest.stack(), entry.stack())) {
                amount = heldAmount();
            }
        }
        if (amount <= 0) {
            held = null;
            return;
        }
        boolean whole = amount >= entry.stack().getCount();
        if (button == 1) {
            // this click is also the first cell of a possible right-drag; mark it so dragging
            // straight off it does not immediately drop a second one in the same place
            painted.clear();
            painted.add(cellKey(t.sec, t.r, t.c));
        }
        ModNetwork.CHANNEL.sendToServer(new C2SBagMove(menu.containerId, place(heldSection), heldRow, heldCol,
                place(t.sec), t.r, t.c, whole ? 0 : amount));
        if (whole) {
            // draw it at its destination until the server answers - only for a move of the whole
            // stack, where "it is now over there" is the complete truth
            moveVersion = ClientBagState.version();
            moveAt = UiState.now();
            moveFrom = heldSection;
            moveFromRow = heldRow;
            moveFromCol = heldCol;
            moveTo = t.sec;
            moveToRow = t.r;
            moveToCol = t.c;
        }
        if (heldSection == BagSection.PACK && t.sec == BagSection.BASE && BagConfig.isBackpack(entry.stack())) {
            UiState.toast("شلت الشنطة · أغراضها باقية داخلها", false);
        }
        afterMoved(amount);
        UiSounds.tick(false);
    }

    /** Q, or a click on the dark outside the panels: the held item onto the ground. */
    private void dropHeldToWorld(int amount) {
        if (held == null || amount <= 0) {
            return;
        }
        int n = Math.min(amount, heldAmount());
        if (n <= 0) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(
                C2SBagAct.drop(menu.containerId, heldSection, heldRow, heldCol, n));
        afterMoved(n);
        UiSounds.tick(false);
    }

    /** Book-keeping after {@code n} of the held stack has gone somewhere: what is left, if anything. */
    private void afterMoved(int n) {
        if (held == null) {
            return;
        }
        if (!heldAll) {
            heldCount -= n;
        }
        int left = heldAll ? held.stack().getCount() - n : heldCount;
        if (left <= 0) {
            held = null;
        }
    }

    private static C2SBagMove.Place place(BagSection section) {
        return section == BagSection.PACK ? C2SBagMove.Place.BAG_PACK : C2SBagMove.Place.BAG_BASE;
    }

    /** The vanilla cursor's stack into the bag at the aimed cell. */
    private void placeCarried(int button) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        Target t = targetAt(UiHost.mouseX, UiHost.mouseY, carried);
        if (t == null) {
            return;
        }
        /*
         * Landing on a stack of the same thing that happens to be a base cell - a real vanilla slot.
         * Hand the click straight to vanilla: topping a stack up and right-click-one-at-a-time are
         * already implemented there, correctly, and in one round trip instead of two.
         */
        BagEntry dest = t.sec == null ? null : ClientBagState.grid(t.sec).at(t.r, t.c);
        if (dest != null && !dest.ownedByPack() && BagGrid.canMerge(dest.stack(), carried)) {
            Slot slot = menuSlotOfInventory(dest.source());
            if (slot != null) {
                slotClicked(slot, slot.index, button, ClickType.PICKUP);
                UiSounds.tick(false);
                return;
            }
        }
        if (!t.ok) {
            UiState.toast(t.sec == BagSection.PACK && BagConfig.isBackpack(carried)
                    ? "ما تقدر تحط شنطة داخل شنطة" : "ما فيه مكان كافي هنا", true);
            return;
        }
        for (int i = menu.invStart; i < menu.invEnd && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem() && slot.mayPlace(carried)) {
                slotClicked(slot, slot.index, button, ClickType.PICKUP);
                pendingSource = slot.getContainerSlot();
                pendingSection = t.sec;
                pendingRow = t.r;
                pendingCol = t.c;
                pendingAt = UiState.now();
                UiSounds.tick(false);
                return;
            }
        }
        UiState.toast("ما فيه مكان كافي في الحقيبة", true);
    }

    /** Finishes {@link #placeCarried}: once the new entry shows up in the bag, move it to the aimed cell. */
    private void applyPendingPlacement() {
        if (pendingSource < 0) {
            return;
        }
        if (UiState.now() - pendingAt > 2500L) {
            pendingSource = -1;
            return;
        }
        for (BagEntry entry : ClientBagState.base().entries()) {
            if (entry.isEmpty() || entry.source() != pendingSource) {
                continue;
            }
            pendingSource = -1;
            if (pendingSection == BagSection.BASE && entry.row() == pendingRow && entry.col() == pendingCol) {
                return;
            }
            boolean fits = landing(ClientBagState.grid(pendingSection), pendingRow, pendingCol, entry.size(),
                    pendingSection == BagSection.BASE ? entry : null, entry.stack());
            if (fits && !(pendingSection == BagSection.PACK && BagConfig.isBackpack(entry.stack()))) {
                ModNetwork.CHANNEL.sendToServer(new C2SBagMove(menu.containerId, C2SBagMove.Place.BAG_BASE, entry.row(), entry.col(),
                        place(pendingSection), pendingRow, pendingCol));
            }
            return;
        }
    }

    /** Shift on a bag item: base items are a real vanilla quick move; pack items go to a free base cell. */
    private void quickMove(BagEntry entry, BagSection section) {
        if (!entry.ownedByPack()) {
            Slot slot = menuSlotOfInventory(entry.source());
            if (slot != null) {
                slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
            }
            return;
        }
        int[] spot = ClientBagState.base().firstFree(entry.size(), null);
        if (spot == null) {
            UiState.toast("ما فيه مكان كافي في الحقيبة", true);
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new C2SBagMove(menu.containerId, C2SBagMove.Place.BAG_PACK, entry.row(), entry.col(),
                C2SBagMove.Place.BAG_BASE, spot[0], spot[1]));
    }

    private void cancelHold() {
        held = null;
        heldAll = true;
        heldCount = 0;
    }

    /** A held entry the server has since moved or removed is let go of rather than acted on. */
    private void validateHeld() {
        if (held == null) {
            return;
        }
        BagGrid grid = ClientBagState.grid(heldSection);
        BagEntry now = grid.at(heldRow, heldCol);
        if (now == null || now.isEmpty() || now.row() != heldRow || now.col() != heldCol) {
            cancelHold();
            return;
        }
        held = now;
        if (!heldAll) {
            // a split can only ever shrink: the cell may have been emptied under us by a hopper,
            // another screen, or the server refusing the move we just asked for
            heldCount = Math.min(heldCount, now.stack().getCount());
            if (heldCount <= 0) {
                cancelHold();
            }
        }
    }

    private void sendButton(int id) {
        if (minecraft != null && minecraft.gameMode != null && minecraft.player != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void leaveTo(int tab) {
        cancelHold();
        UiScreens.go(0, tab);
    }

    /**
     * A stack on the cursor. Leaving the bag or turning a vault page closes this menu on the server,
     * and vanilla puts a carried stack back in the inventory - or on the ground when it is full - so
     * the player is asked to put it down first instead of having it moved or dropped for them.
     */
    private boolean handsFull() {
        if (menu.getCarried().isEmpty()) {
            return false;
        }
        UiState.toast("حط اللي بيدك أول", true);
        return true;
    }

    /* ================================================================== the right drag */

    /**
     * Cells this right-drag has already dropped one into, as {@code "P:row:col"}.
     *
     * <p>Vanilla's right-drag paints one item into each slot the cursor crosses, and crossing the
     * same slot twice does not put a second one in. The set is what expresses "already painted" -
     * without it, holding the button still over one cell would pour the whole stack into it a frame
     * at a time.</p>
     */
    private final java.util.Set<String> painted = new java.util.HashSet<>();

    @Override
    public boolean mouseReleased(double guiX, double guiY, int button) {
        painted.clear();
        return true;
    }

    @Override
    public boolean mouseDragged(double guiX, double guiY, int button, double dragX, double dragY) {
        if (button == 1 && held != null) {
            paintOne();
        }
        return true;
    }

    private static String cellKey(BagSection section, int row, int col) {
        return (section == BagSection.PACK ? "P:" : "B:") + row + ":" + col;
    }

    /** One item into the cell under the cursor, if this drag has not been here already. */
    private void paintOne() {
        BagEntry entry = held;
        if (entry == null || heldAmount() <= 0) {
            return;
        }
        Target t = targetAt(UiHost.mouseX, UiHost.mouseY, entry.stack());
        if (t == null || t.sec == null || !t.ok) {
            return;
        }
        if (t.sec == heldSection && t.r == heldRow && t.c == heldCol) {
            return;   // the cell it is coming from is not a destination
        }
        if (!painted.add(cellKey(t.sec, t.r, t.c))) {
            return;
        }
        ModNetwork.CHANNEL.sendToServer(new C2SBagMove(menu.containerId, place(heldSection), heldRow, heldCol,
                place(t.sec), t.r, t.c, 1));
        afterMoved(1);
        UiSounds.tick(false);
    }

    /* ================================================================== keys */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        /*
         * Q, before anything else.
         *
         * Vanilla's own handler only fires for a hovered Slot, and half the grid - the worn
         * backpack's rows - is not made of slots at all, so items there could never be thrown away.
         * Routing both halves through one packet is what makes Q mean the same thing everywhere in
         * the grid. Ctrl+Q throws the whole stack, exactly as it does in a chest.
         */
        if (minecraft != null && minecraft.options.keyDrop.matches(keyCode, scanCode)) {
            boolean all = Screen.hasControlDown();
            if (held != null) {
                dropHeldToWorld(all ? heldAmount() : 1);
                return true;
            }
            if (menu.getCarried().isEmpty() && dropHovered(all)) {
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (rentOpen) {
                rentOpen = false;
                return true;
            }
            if (UiState.storeEscape()) {
                return true;
            }
            if (held != null) {
                cancelHold();
                return true;
            }
            if (!menu.getCarried().isEmpty()) {
                putCarriedBack();
                return true;
            }
            onClose();
            return true;
        }
        int dir = UiScreens.navDirection(keyCode, scanCode);
        if (dir != 0 && menu.isVault()) {
            // in the vault, A and D turn its pages instead: A (left) is the next one
            turnTo(menu.vaultPage() + (dir > 0 ? 1 : -1));
            return true;
        }
        if (dir != 0) {
            if (held == null && !handsFull()) {
                UiState.keyHit(dir > 0 ? 'a' : 'd');
                leaveTo(UiScreens.step(0, dir));
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Throws away whatever the cursor is resting on. True when there was something to throw.
     *
     * <p>Bag cells go through the bag's own packet; every other cell is an ordinary slot and gets
     * vanilla's THROW click, so a gun, a piece of armour or a chest cell behaves exactly as it does
     * anywhere else in the game.</p>
     */
    private boolean dropHovered(boolean all) {
        Hits.Hit hit = UiHost.hitUnderMouse();
        if (hit == null) {
            return false;
        }
        if (hit.id.startsWith("bag:") && hit.data instanceof Integer i
                && i >= 0 && i < entries.size()) {
            BagEntry entry = entries.get(i);
            ModNetwork.CHANNEL.sendToServer(C2SBagAct.drop(menu.containerId, entrySections.get(i),
                    entry.row(), entry.col(), all ? 0 : 1));
            UiSounds.tick(false);
            return true;
        }
        Slot slot = slotForKey(hit.id);
        if (slot != null && slot.hasItem()) {
            slotClicked(slot, slot.index, all ? 1 : 0, ClickType.THROW);
            UiSounds.tick(false);
            return true;
        }
        return false;
    }

    /** Esc with something on the cursor puts it down in the bag instead of closing everything. */
    private void putCarriedBack() {
        ItemStack carried = menu.getCarried();
        for (int i = menu.invStart; i < menu.invEnd && i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (!slot.hasItem() && slot.mayPlace(carried)) {
                slotClicked(slot, slot.index, 0, ClickType.PICKUP);
                UiSounds.tick(false);
                return;
            }
        }
        onClose();
    }

    @Override
    public void removed() {
        if (menu.isVault()) {
            vaultGoneAt = UiState.now();
        }
        cancelHold();
        super.removed();
    }
}
