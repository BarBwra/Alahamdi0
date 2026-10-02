package com.barbwra.mlum.client;

import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.BagEntry;
import com.barbwra.mlum.bag.BagGrid;
import com.barbwra.mlum.bag.BagSection;
import com.barbwra.mlum.compat.ShopCompat;
import com.barbwra.mlum.network.S2CBagState;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The client's copy of the bag, as last sent by the server.
 *
 * <p>Read-only as far as gameplay is concerned. The screen draws from here and sends requests; it
 * never edits this to reflect a move it hopes will succeed. Optimistic local edits are what produce
 * an item that appears to move and then snaps back, and worse, a drag that starts from a cell the
 * server has already emptied.</p>
 *
 * <p>Prices are cached alongside because they arrive with the state - see {@link S2CBagState}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientBagState {

    private ClientBagState() {
    }

    private static BagGrid base = new BagGrid(BagSection.BASE, BagConfig.BASE_ROWS);
    private static BagGrid pack = new BagGrid(BagSection.PACK, 0);
    private static final Map<BagEntry, Double> PRICES = new HashMap<>();
    /** The last price seen for each item type, so a stack outside the bag can still be priced. */
    private static final Map<net.minecraft.world.item.Item, Double> PRICE_BY_ITEM = new HashMap<>();

    public static void accept(S2CBagState msg) {
        BagGrid newBase = new BagGrid(BagSection.BASE, msg.baseRows());
        BagGrid newPack = new BagGrid(BagSection.PACK, msg.packRows());
        PRICES.clear();

        for (S2CBagState.Cell cell : msg.cells()) {
            BagGrid target = cell.section() == BagSection.PACK ? newPack : newBase;
            target.entries().add(cell.entry());
            PRICES.put(cell.entry(), cell.sellPerItem());
            if (!cell.entry().stack().isEmpty()) {
                PRICE_BY_ITEM.put(cell.entry().stack().getItem(), cell.sellPerItem());
            }
        }
        version++;
        base = newBase;
        pack = newPack;
    }

    private static int version;

    /** Bumped on every state packet, so screens can tell the bag changed without comparing it. */
    public static int version() {
        return version;
    }

    /**
     * What a trader pays for one of this stack's item, from the last bag state that held one, or
     * {@link ShopCompat#NOT_SELLABLE} when none has been seen.
     */
    public static double priceOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ShopCompat.NOT_SELLABLE;
        }
        for (Map.Entry<BagEntry, Double> e : PRICES.entrySet()) {
            if (e.getKey().stack() == stack) {
                return e.getValue();
            }
        }
        Double v = PRICE_BY_ITEM.get(stack.getItem());
        return v == null ? ShopCompat.NOT_SELLABLE : v;
    }

    public static BagGrid base() {
        return base;
    }

    public static BagGrid pack() {
        return pack;
    }

    public static BagGrid grid(BagSection section) {
        return section == BagSection.PACK ? pack : base;
    }

    public static int rows() {
        return base.rows() + pack.rows();
    }

    public static int capacity() {
        return (base.rows() + pack.rows()) * BagConfig.COLUMNS;
    }

    public static int usedCells() {
        return base.usedCells() + pack.usedCells();
    }

    public static boolean hasPack() {
        return pack.rows() > 0;
    }

    /**
     * What a trader pays for one of these, or {@link ShopCompat#NOT_SELLABLE}.
     *
     * <p>Keyed by entry identity rather than by item, because the server priced this exact stack -
     * NBT and all - and two stacks of the same item can differ in ways that change the price.</p>
     */
    public static double price(@Nullable BagEntry entry) {
        Double value = entry == null ? null : PRICES.get(entry);
        return value == null ? ShopCompat.NOT_SELLABLE : value;
    }

    /** The entry under a grid cell in a section, or null. */
    @Nullable
    public static BagEntry at(BagSection section, int row, int col) {
        return grid(section).at(row, col);
    }

    /** Wipes the cache when the screen closes, so a stale bag can never be drawn on reopen. */
    public static void clear() {
        base = new BagGrid(BagSection.BASE, BagConfig.BASE_ROWS);
        pack = new BagGrid(BagSection.PACK, 0);
        PRICES.clear();
        // sell prices belong to the server that sent them
        PRICE_BY_ITEM.clear();
        version++;
    }

    /** True when the stack would fit somewhere in the bag. Used for the red "no room" toast. */
    public static boolean hasRoomFor(ItemStack stack) {
        return base.hasRoomFor(stack) || pack.hasRoomFor(stack);
    }
}
