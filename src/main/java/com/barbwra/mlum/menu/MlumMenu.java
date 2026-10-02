package com.barbwra.mlum.menu;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.menu.slot.ArmorSlot;
import com.barbwra.mlum.menu.slot.AttachmentSlot;
import com.barbwra.mlum.menu.slot.GliderSlot;
import com.barbwra.mlum.menu.slot.GunSlot;
import com.barbwra.mlum.menu.slot.OffhandSlot;
import com.barbwra.mlum.menu.slot.QuickSlot;
import com.barbwra.mlum.menu.slot.VicinitySlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one menu behind every tactical screen.
 *
 * <p>{@code rows} decides what the left column is: 0 = plain inventory, 3 = single chest / barrel /
 * ender chest, 6 = double chest (which expands over the vicinity scanner).</p>
 *
 * <p>Slot order, identical on both sides, which is what makes the menu syncable:
 * container, vicinity, inventory 3x9, quick access 1x7, weapons x2, armour x4, offhand. The two
 * weapon cells now sit in the bottom left corner; only their coordinates moved, not their meaning
 * (still vanilla hotbar 0 and 1).</p>
 *
 * <p><b>The hotbar split.</b> Vanilla inventory indices 0 and 1 become the two weapon cells and 2-8
 * become the seven quick access cells. Because they are still genuine hotbar indices, the mouse
 * wheel and the number keys keep selecting them with no extra code - only the placement rules
 * change, and they run both ways: weapon cells take guns only, quick access takes everything
 * except guns.</p>
 */
public class MlumMenu extends AbstractContainerMenu {

    /* menu buttons - piggybacks on vanilla's ServerboundContainerButtonClickPacket */
    public static final int BTN_SCROLL_UP = 0;
    public static final int BTN_SCROLL_DOWN = 1;
    public static final int BTN_LOOT_ALL = 2;
    /** خذ الكل: the open chest into the bag, footprints respected. */
    public static final int BTN_LOOT_CHEST = 3;

    /* synced ints */
    public static final int DATA_TOTAL = 0;
    public static final int DATA_SCROLL = 1;
    public static final int DATA_COUNT = 2;

    public static final int WEAPON_SLOTS = 2;
    public static final int QUICK_SLOTS = 7;

    private final Player player;
    private final Container container;
    private final VicinityContainer vicinity;
    private final ContainerData data;
    private final int rows;
    private final int vicinityRows;

    /* slot index ranges, inclusive-exclusive */
    public final int containerStart;
    public final int containerEnd;
    public final int vicinityStart;
    public final int vicinityEnd;
    public final int invStart;
    public final int invEnd;
    public final int quickStart;
    public final int quickEnd;
    public final int gunStart;
    public final int gunEnd;
    public final int armorStart;
    public final int armorEnd;
    public final int offhandIndex;
    public final int attachmentStart;
    public final int attachmentEnd;
    public final int gliderIndex;
    /**
     * The worn backpack, in the bottom-left gear position.
     *
     * <p>Added last so every index above it is unchanged - the slot list is baked on both sides at
     * construction, so appending is the only edit that cannot desync an existing client.</p>
     */
    public final int backpackIndex;

    private final AttachmentContainer attachments;
    private final Container gliderContainer;

    private int scanCooldown;
    private List<ItemEntity> nearby = Collections.emptyList();

    /* ---- the faction vault, when that is what the container is ---- */

    /** Client and server: this menu shows a faction vault page rather than a chest. */
    private boolean vault;
    /** 1-based page being shown, and how many the faction has. */
    private int vaultPage = 1;
    private int vaultPages = 1;
    /** Server only - the client is never told which faction it is looking at. */
    @javax.annotation.Nullable
    private java.util.UUID vaultFaction;
    /** Server only - opened by an operator's command, so page turns skip the membership check. */
    private boolean vaultAdmin;

    /* ---- live bag sync: what the bag looked like when it was last sent ---- */
    private long bagSignature = Long.MIN_VALUE;
    @javax.annotation.Nullable
    private net.minecraft.nbt.Tag packTag;
    private String packKey = "";

    /* ------------------------------------------------------------ construction */

    /** Client side factory. */
    public MlumMenu(int id, Inventory playerInv, FriendlyByteBuf buf) {
        this(id, playerInv, readRows(buf));
        // written after the row count by the vault; absent for a plain inventory or a chest
        if (buf.readableBytes() >= 3) {
            this.vault = buf.readByte() == 1;
            this.vaultPage = Math.max(1, buf.readByte());
            this.vaultPages = Math.max(1, buf.readByte());
        }
    }

    private MlumMenu(int id, Inventory playerInv, int rows) {
        this(id, playerInv, new SimpleContainer(rows * 9), rows, new SimpleContainerData(DATA_COUNT));
    }

    private static int readRows(FriendlyByteBuf buf) {
        return Mth.clamp(buf.readByte(), 0, 6);
    }

    /** Server side constructor. */
    public MlumMenu(int id, Inventory playerInv, Container container, int rows, ContainerData data) {
        super(ModMenus.MAIN.get(), id);

        this.player = playerInv.player;
        this.rows = Mth.clamp(rows, 0, 6);
        this.container = container;
        this.data = data;
        this.vicinityRows = MlumLayout.vicinityRows(this.rows);

        checkContainerDataCount(data, DATA_COUNT);
        if (this.rows > 0) {
            checkContainerSize(container, this.rows * 9);
            container.startOpen(this.player);
        }

        this.vicinity = this.player instanceof ServerPlayer serverPlayer
                ? new VicinityContainer(vicinityRows, serverPlayer)
                : new VicinityContainer(vicinityRows);

        this.attachments = new AttachmentContainer(playerInv);
        // A one-cell container of its own: the glider is not a vanilla equipment slot, so there is
        // no player inventory index to point at. It is per-menu, which means it does NOT persist -
        // see the note in the README about giving it a real home.
        this.gliderContainer = new SimpleContainer(1);

        final int r = this.rows;

        /* ---- the chest, at the same cell size as the player's own grid ---- */
        containerStart = slots.size();
        for (int row = 0; row < r; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(container, row * 9 + col,
                        MlumLayout.invGridX(MlumLayout.CHEST_GRID_X, col) + MlumLayout.INV_INSET,
                        MlumLayout.invGridY(MlumLayout.CHEST_GRID_Y, row) + MlumLayout.INV_INSET));
            }
        }
        containerEnd = slots.size();

        /* ---- ground items - inventory view only, so this loop is empty while looting ---- */
        vicinityStart = slots.size();
        for (int i = 0; i < vicinityRows; i++) {
            addSlot(new VicinitySlot(vicinity, i,
                    MlumLayout.vicinityCellX(i) + (MlumLayout.VICINITY_CELL - 16) / 2,
                    MlumLayout.vicinityCellY(i) + (MlumLayout.VICINITY_CELL - 16) / 2));
        }
        vicinityEnd = slots.size();

        /* ---- 3x9 player inventory (Inventory 9..35) ---- */
        invStart = slots.size();
        for (int row = 0; row < MlumLayout.INV_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, 9 + row * 9 + col,
                        MlumLayout.invSlotX(r, col), MlumLayout.invSlotY(r, row)));
            }
        }
        invEnd = slots.size();

        /* ---- 7 quick access cells (Inventory 2..8), no firearms allowed ---- */
        quickStart = slots.size();
        for (int col = 0; col < QUICK_SLOTS; col++) {
            addSlot(new QuickSlot(playerInv, WEAPON_SLOTS + col,
                    MlumLayout.quickSlotX(r, col), MlumLayout.quickSlotY(r)));
        }
        quickEnd = slots.size();

        /* ---- bottom left: the two firearm cells (Inventory 0..1) ---- */
        gunStart = slots.size();
        for (int i = 0; i < WEAPON_SLOTS; i++) {
            addSlot(new GunSlot(playerInv, i,
                    MlumLayout.gunSlotX(r, i), MlumLayout.gunSlotY(r, i), i));
        }
        gunEnd = slots.size();

        /* ---- worn armour: flanking the model, or lined up in the gear strip while looting ---- */
        armorStart = slots.size();
        addSlot(new ArmorSlot(playerInv, 39,
                MlumLayout.armorSlotX(r, 0), MlumLayout.armorSlotY(r, 0),
                EquipmentSlot.HEAD, player, InventoryMenu.EMPTY_ARMOR_SLOT_HELMET));
        addSlot(new ArmorSlot(playerInv, 38,
                MlumLayout.armorSlotX(r, 1), MlumLayout.armorSlotY(r, 1),
                EquipmentSlot.CHEST, player, InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE));
        addSlot(new ArmorSlot(playerInv, 37,
                MlumLayout.armorSlotX(r, 2), MlumLayout.armorSlotY(r, 2),
                EquipmentSlot.LEGS, player, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS));
        addSlot(new ArmorSlot(playerInv, 36,
                MlumLayout.armorSlotX(r, 3), MlumLayout.armorSlotY(r, 3),
                EquipmentSlot.FEET, player, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS));
        armorEnd = slots.size();

        /* ---- offhand ---- */
        offhandIndex = slots.size();
        addSlot(new OffhandSlot(playerInv, 40,
                MlumLayout.offhandSlotX(r), MlumLayout.offhandSlotY(r)));

        /*
         * ---- attachments: six mounting points per firearm ----
         *
         * Always added, even while looting and even when the firearm cells are empty. The slot list
         * is baked on both sides at construction, so a menu that grew a slot when a gun was picked
         * up would desync the moment it happened. An unusable slot refuses everything and is drawn
         * as absent instead.
         *
         * They are parked off-canvas in the looting layout rather than dropped, for the same
         * reason: same count, same order, both sides.
         */
        attachmentStart = slots.size();
        for (int gun = 0; gun < WEAPON_SLOTS; gun++) {
            for (int slot = 0; slot < AttachmentContainer.PER_GUN; slot++) {
                int index = gun * AttachmentContainer.PER_GUN + slot;
                int x = MlumLayout.chestView(r) ? -9000 : MlumLayout.attSlotX(slot);
                int y = MlumLayout.chestView(r) ? -9000 : MlumLayout.attSlotY(gun, slot);
                addSlot(new AttachmentSlot(attachments, index, x, y));
            }
        }
        attachmentEnd = slots.size();

        /* ---- glider ---- */
        /*
         * Parked off-canvas in both layouts. The bag design has no glider cell, and this container
         * is per-menu - anything left in it vanished when the screen closed. Keeping the slot (so
         * the slot list stays identical on both sides) but out of reach is what stops that loss.
         */
        gliderIndex = slots.size();
        addSlot(new GliderSlot(gliderContainer, 0, -9000, -9000));

        /*
         * ---- the worn backpack ----
         *
         * Parked off-canvas while looting, like the attachments and the glider: the slot count and
         * order have to match on both sides, so a slot is never conditionally absent - only moved
         * somewhere the player cannot reach.
         */
        backpackIndex = slots.size();
        addSlot(new com.barbwra.mlum.menu.slot.BackpackSlot(
                new BackpackContainer(player), 0,
                MlumLayout.chestView(r) ? -9000 : BagLayout.gearSlotX(BagLayout.GEAR_BACKPACK) + 18,
                MlumLayout.chestView(r) ? -9000 : BagLayout.gearSlotY(BagLayout.GEAR_BACKPACK) + 18,
                player));

        addDataSlots(data);
    }

    /* ---------------------------------------------------------------- accessors */

    public int getRows() {
        return rows;
    }

    /** Marks this menu as a page of a faction's vault. Server side, right after construction. */
    public MlumMenu asVault(java.util.UUID faction, int page, int pages, boolean admin) {
        this.vault = true;
        this.vaultFaction = faction;
        this.vaultPage = page;
        this.vaultPages = pages;
        this.vaultAdmin = admin;
        return this;
    }

    public boolean isVault() {
        return vault;
    }

    public int vaultPage() {
        return vaultPage;
    }

    public int vaultPages() {
        return vaultPages;
    }

    @javax.annotation.Nullable
    public java.util.UUID vaultFaction() {
        return vaultFaction;
    }

    public boolean vaultAdmin() {
        return vaultAdmin;
    }

    public int getVicinityRows() {
        return vicinityRows;
    }

    public Container getContainer() {
        return container;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getWeapon(int index) {
        int slot = gunStart + index;
        return slot >= gunStart && slot < gunEnd ? slots.get(slot).getItem() : ItemStack.EMPTY;
    }

    /** True when the player currently has weapon cell {@code index} selected on the hotbar. */
    public boolean isWeaponSelected(int index) {
        return player.getInventory().selected == index;
    }

    public int getVicinityTotal() {
        return data.get(DATA_TOTAL);
    }

    public int getVicinityScroll() {
        return data.get(DATA_SCROLL);
    }

    public int getMaxVicinityScroll() {
        return Math.max(0, getVicinityTotal() - vicinityRows);
    }

    public ItemStack getVicinityStack(int row) {
        return vicinity.getItem(row);
    }

    /* -------------------------------------------------------------- server tick */

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer serverPlayer) {
            if (vicinityRows > 0 && --scanCooldown <= 0) {
                scanCooldown = Math.max(1, MlumConfig.scanIntervalTicks());
                refreshVicinity(serverPlayer);
            }
            // The bag is drawn from its own packet, not from these slots, so anything that changes
            // the base cells or the worn pack behind its back - a shift-click out of a chest, a
            // pickup, a backpack put on in its socket - is sent here, the tick it happens.
            long signature = bagSignature(serverPlayer);
            if (signature != bagSignature) {
                bagSignature = signature;
                com.barbwra.mlum.bag.BagService.sync(serverPlayer);
            }
        }
        super.broadcastChanges();
    }

    private long bagSignature(ServerPlayer serverPlayer) {
        long h = 1L;
        Inventory inv = serverPlayer.getInventory();
        for (int i = com.barbwra.mlum.bag.BagStore.FIRST_SLOT; i <= com.barbwra.mlum.bag.BagStore.LAST_SLOT; i++) {
            ItemStack s = inv.getItem(i);
            long v = s.isEmpty() ? 0L
                    : System.identityHashCode(s.getItem()) * 17L + s.getCount() * 131L + s.getDamageValue();
            h = h * 31L + v;
        }
        // the worn pack: only re-read when the tag holding it was replaced
        net.minecraft.nbt.Tag tag = com.barbwra.mlum.bag.BackpackAccess.wornTag(serverPlayer);
        if (tag != packTag) {
            packTag = tag;
            ItemStack worn = com.barbwra.mlum.bag.BackpackAccess.worn(serverPlayer);
            packKey = worn.isEmpty() ? ""
                    : String.valueOf(net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(worn.getItem()))
                    + "#" + com.barbwra.mlum.bag.BagConfig.addedRows(worn);
        }
        return h * 31L + packKey.hashCode();
    }

    private void refreshVicinity(ServerPlayer serverPlayer) {
        nearby = VicinityScanner.scan(serverPlayer, MlumConfig.vicinityRadius(), MlumConfig.maxTracked());
        int total = nearby.size();
        int scroll = Mth.clamp(data.get(DATA_SCROLL), 0, Math.max(0, total - vicinityRows));
        data.set(DATA_TOTAL, total);
        data.set(DATA_SCROLL, scroll);
        vicinity.bind(nearby, scroll);
    }

    /* ------------------------------------------------------------------ buttons */

    @Override
    public boolean clickMenuButton(Player clicker, int id) {
        if (id == BTN_LOOT_CHEST) {
            if (clicker instanceof ServerPlayer serverPlayer && rows > 0 && !vault) {
                com.barbwra.mlum.bag.BagService.lootAll(serverPlayer, container);
            }
            return true;
        }
        if (id == BTN_LOOT_ALL) {
            if (clicker instanceof ServerPlayer serverPlayer) {
                lootAll(serverPlayer);
                scanCooldown = 0;
            }
            return true;
        }
        if (vicinityRows <= 0) {
            return false;
        }
        int max = getMaxVicinityScroll();
        // a whole row per notch: stepping one item would slide every icon in the grid sideways,
        // which makes a list you were reading unreadable
        int step = MlumLayout.VICINITY_COLS;
        switch (id) {
            case BTN_SCROLL_UP -> data.set(DATA_SCROLL, Mth.clamp(data.get(DATA_SCROLL) - step, 0, max));
            case BTN_SCROLL_DOWN -> data.set(DATA_SCROLL, Mth.clamp(data.get(DATA_SCROLL) + step, 0, max));
            default -> {
                return false;
            }
        }
        if (vicinity.isServerSide()) {
            vicinity.bind(nearby, data.get(DATA_SCROLL));
        }
        return true;
    }

    private void lootAll(ServerPlayer serverPlayer) {
        if (!MlumConfig.allowVicinityPickup()) {
            return;
        }
        double radius = MlumConfig.vicinityRadius() + VicinityScanner.reachSlack();
        double radiusSq = radius * radius;

        for (ItemEntity entity : new ArrayList<>(nearby)) {
            if (entity == null || !entity.isAlive() || entity.getItem().isEmpty()) {
                continue;
            }
            if (entity.level() != serverPlayer.level()
                    || entity.distanceToSqr(serverPlayer.position()) > radiusSq) {
                continue;
            }
            ItemStack probe = entity.getItem().copy();
            int before = probe.getCount();
            moveIntoPlayer(probe, false);
            int moved = before - probe.getCount();
            if (moved <= 0) {
                continue;
            }
            ItemStack live = entity.getItem();
            live.shrink(moved);
            serverPlayer.take(entity, moved);
            if (live.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(live.copy());
            }
        }
        refreshVicinity(serverPlayer);
    }

    /* ------------------------------------------------------------------ clicking */

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
        if (slotId >= vicinityStart && slotId < vicinityEnd) {
            if (clickType == ClickType.SWAP || clickType == ClickType.CLONE || clickType == ClickType.THROW) {
                return;
            }
            if (clickType == ClickType.PICKUP && button == 0
                    && getCarried().isEmpty()
                    && slots.get(slotId).hasItem()
                    && MlumConfig.directLoot()) {
                quickMoveStack(clicker, slotId);
                return;
            }
        }
        super.clicked(slotId, button, clickType, clicker);
    }

    /* --------------------------------------------------------------- shift-click */

    @Override
    public ItemStack quickMoveStack(Player clicker, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        /* ---- ground -> player: move a probe first, then remove the real amount ---- */
        if (index >= vicinityStart && index < vicinityEnd) {
            if (!MlumConfig.allowVicinityPickup()) {
                return ItemStack.EMPTY;
            }
            ItemStack probe = slot.getItem().copy();
            int before = probe.getCount();
            moveIntoPlayer(probe, false);
            int moved = before - probe.getCount();
            if (moved <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = slot.remove(moved);
            slot.onTake(clicker, taken);
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index >= containerStart && index < containerEnd) {
            if (!moveIntoPlayer(stack, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            if (containerEnd > containerStart) {
                moved = moveItemStackTo(stack, containerStart, containerEnd, false);
            }
            if (!stack.isEmpty() && !moved) {
                moved = tryEquip(stack, index);
            }
            if (!stack.isEmpty() && !moved) {
                moved = moveWithinPlayer(stack, index);
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(clicker, stack);
        return original;
    }

    /**
     * Weapon cells first for guns, then the inventory, then quick access.
     * {@code QuickSlot#mayPlace} is what keeps a gun out of quick access here.
     */
    private boolean moveIntoPlayer(ItemStack stack, boolean reverse) {
        boolean moved = false;
        if (TaczCompat.isGun(stack)) {
            moved = moveItemStackTo(stack, gunStart, gunEnd, false);
            if (!stack.isEmpty()) {
                // a gun that found no weapon cell goes to the main inventory, never quick access
                moved |= moveItemStackTo(stack, invStart, invEnd, reverse);
            }
            return moved;
        }
        moved = moveItemStackTo(stack, invStart, invEnd, reverse);
        if (!stack.isEmpty()) {
            moved |= moveItemStackTo(stack, quickStart, quickEnd, reverse);
        }
        return moved;
    }

    /** Shuffling between the player's own areas without bouncing an item back where it came from. */
    private boolean moveWithinPlayer(ItemStack stack, int index) {
        boolean fromInventory = index >= invStart && index < invEnd;
        boolean fromQuick = index >= quickStart && index < quickEnd;
        boolean isGun = TaczCompat.isGun(stack);

        boolean moved = false;
        if (!fromInventory) {
            moved |= moveItemStackTo(stack, invStart, invEnd, false);
        }
        if (!stack.isEmpty() && !fromQuick && !isGun) {
            moved |= moveItemStackTo(stack, quickStart, quickEnd, false);
        }
        if (!moved && !stack.isEmpty() && fromInventory && !isGun) {
            moved = moveItemStackTo(stack, quickStart, quickEnd, false);
        }
        return moved;
    }

    /** Shift-clicking armour, a shield or a gun sends it to the right home first. */
    private boolean tryEquip(ItemStack stack, int fromIndex) {
        if (TaczCompat.isGun(stack) && !(fromIndex >= gunStart && fromIndex < gunEnd)) {
            if (moveItemStackTo(stack, gunStart, gunEnd, false)) {
                return true;
            }
        }

        EquipmentSlot equipment = Mob.getEquipmentSlotForItem(stack);
        if (equipment.getType() == EquipmentSlot.Type.ARMOR) {
            // armour cells run head, chest, legs, feet - EquipmentSlot#getIndex is feet=0..head=3
            int target = armorStart + (3 - equipment.getIndex());
            if (target >= armorStart && target < armorEnd && fromIndex != target) {
                Slot dest = slots.get(target);
                if (!dest.hasItem() && dest.mayPlace(stack)) {
                    return moveItemStackTo(stack, target, target + 1, false);
                }
            }
        } else if (equipment == EquipmentSlot.OFFHAND) {
            if (fromIndex != offhandIndex && !slots.get(offhandIndex).hasItem()) {
                return moveItemStackTo(stack, offhandIndex, offhandIndex + 1, false);
            }
        }
        return false;
    }

    /* ----------------------------------------------------------------- lifecycle */

    @Override
    public boolean stillValid(Player check) {
        return rows <= 0 || container.stillValid(check);
    }

    @Override
    public void removed(Player check) {
        super.removed(check);
        if (rows > 0) {
            container.stopOpen(check);
        }
    }
}
