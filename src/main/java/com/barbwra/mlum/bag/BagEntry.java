package com.barbwra.mlum.bag;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * One item sitting at a position in the bag.
 *
 * <p>Mutable on purpose. A placement engine that returned a new entry for every move would churn
 * an object per drag frame and force every holder to re-look-up its reference; the grid owns these
 * and moves them by writing {@link #row} and {@link #col}.</p>
 *
 * <p><b>The size is not stored.</b> It is derived from the stack through {@link BagConfig} every
 * time it is asked for. Storing it would mean a saved bag disagreeing with the config the moment an
 * admin edits {@code item_sizes}, and then an item that renders at one size and collides at
 * another - the config is the single source of truth and {@link BagGrid#reflow} is what reconciles
 * an old layout to a new one.</p>
 */
public final class BagEntry {

    /** {@link #source} for an entry the backpack's own NBT owns. */
    public static final int OWNED_BY_PACK = -1;

    private ItemStack stack;
    private int row;
    private int col;
    private final int source;

    public BagEntry(ItemStack stack, int row, int col) {
        this(stack, row, col, OWNED_BY_PACK);
    }

    /**
     * @param source the player inventory slot this entry <i>is</i>, or {@link #OWNED_BY_PACK}
     */
    public BagEntry(ItemStack stack, int row, int col, int source) {
        this.stack = stack == null ? ItemStack.EMPTY : stack;
        this.row = row;
        this.col = col;
        this.source = source;
    }

    /**
     * Which player inventory slot backs this entry, or {@link #OWNED_BY_PACK}.
     *
     * <p><b>This is the difference between a bag and a duplication bug.</b> The base 27 cells are
     * not storage of their own - they are a <i>layout over</i> the player's real inventory slots
     * 9..35. Only the position is ours; the stack belongs to the vanilla inventory and must be read
     * and written there. Storing the stack as well would give the player two inventories holding the
     * same items, and 27 slots that no other mod, hopper or death handler can see.</p>
     *
     * <p>Backpack rows are the opposite case: nothing in vanilla owns them, so the pack's NBT does.</p>
     */
    public int source() {
        return source;
    }

    public boolean ownedByPack() {
        return source == OWNED_BY_PACK;
    }

    public ItemStack stack() {
        return stack;
    }

    public void setStack(ItemStack value) {
        this.stack = value == null ? ItemStack.EMPTY : value;
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public void moveTo(int newRow, int newCol) {
        this.row = newRow;
        this.col = newCol;
    }

    public ItemSize size() {
        return BagConfig.sizeOf(stack);
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /** True when this entry's footprint covers the given cell. */
    public boolean covers(int r, int c) {
        ItemSize size = size();
        return r >= row && r < row + size.height() && c >= col && c < col + size.width();
    }

    /* ------------------------------------------------------------------ codec */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Item", stack.save(new CompoundTag()));
        tag.putByte("Row", (byte) row);
        tag.putByte("Col", (byte) col);
        return tag;
    }

    public static BagEntry load(CompoundTag tag) {
        return new BagEntry(ItemStack.of(tag.getCompound("Item")),
                tag.getByte("Row"), tag.getByte("Col"));
    }

    /**
     * <p><b>{@link #source} travels.</b> It used to be left out, and every entry therefore arrived on
     * the client claiming to be owned by the pack. That one missing field is what made a base item
     * unreachable as a vanilla slot on the client: the drop position was never applied, Q dropped
     * nothing, shift-click and right-click fell through, and the hovered slot stayed null. It is an
     * index into the player's own inventory, not a capability, so sending it grants nothing - the
     * server re-reads whatever is really in that slot before it acts.</p>
     */
    public void write(FriendlyByteBuf buf) {
        buf.writeItem(stack);
        buf.writeByte(row);
        buf.writeByte(col);
        // OWNED_BY_PACK is -1, so shift by one and keep the varint single-byte
        buf.writeVarInt(source + 1);
    }

    public static BagEntry read(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int row = buf.readByte();
        int col = buf.readByte();
        int source = buf.readVarInt() - 1;
        return new BagEntry(stack, row, col, source);
    }
}
