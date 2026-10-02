package com.barbwra.mlum.network;

import com.barbwra.mlum.bag.BagEntry;
import com.barbwra.mlum.bag.BagGrid;
import com.barbwra.mlum.bag.BagSection;
import com.barbwra.mlum.client.ClientBagState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The whole bag: both sections, their positions, and the prices the details panel needs.
 *
 * <h2>Why the whole thing rather than a diff</h2>
 * <p>A bag is at most 72 cells and is only ever sent when it changes or when the screen opens. A
 * diff would save a few dozen bytes per move and would introduce the one bug class this design
 * cannot tolerate - a client whose idea of where items are has drifted from the server's, which is
 * how a player ends up dragging an item that is not there. Sending the state whole makes every
 * message self-correcting: whatever the client thought, this is the truth.</p>
 *
 * <h2>Prices travel with the items</h2>
 * <p>Sell prices come from MlumShop's world save data, which only exists on the server. Resolving
 * them here and shipping one double per entry means the details panel is a renderer, and a client
 * without the shop installed simply receives {@code -1} for everything.</p>
 */
public final class S2CBagState {

    /** One row of the payload: the stack, where it sits, and what a trader pays for one. */
    public record Cell(BagSection section, BagEntry entry, double sellPerItem) {
    }

    private final List<Cell> cells;
    private final int baseRows;
    private final int packRows;

    public S2CBagState(List<Cell> cells, int baseRows, int packRows) {
        this.cells = cells;
        this.baseRows = baseRows;
        this.packRows = packRows;
    }

    public List<Cell> cells() {
        return cells;
    }

    public int baseRows() {
        return baseRows;
    }

    public int packRows() {
        return packRows;
    }

    /** Builds the payload from two live grids, pricing each entry as it goes. */
    public static S2CBagState of(BagGrid base, BagGrid pack,
                                 java.util.function.ToDoubleFunction<net.minecraft.world.item.ItemStack> pricer) {
        List<Cell> out = new ArrayList<>();
        for (BagEntry entry : base.entries()) {
            out.add(new Cell(BagSection.BASE, entry, pricer.applyAsDouble(entry.stack())));
        }
        for (BagEntry entry : pack.entries()) {
            out.add(new Cell(BagSection.PACK, entry, pricer.applyAsDouble(entry.stack())));
        }
        return new S2CBagState(out, base.rows(), pack.rows());
    }

    public static void encode(S2CBagState msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.baseRows);
        buf.writeByte(msg.packRows);
        buf.writeVarInt(msg.cells.size());
        for (Cell cell : msg.cells) {
            buf.writeBoolean(cell.section() == BagSection.PACK);
            cell.entry().write(buf);
            buf.writeDouble(cell.sellPerItem());
        }
    }

    public static S2CBagState decode(FriendlyByteBuf buf) {
        int baseRows = buf.readByte();
        int packRows = buf.readByte();
        int count = buf.readVarInt();
        List<Cell> cells = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BagSection section = buf.readBoolean() ? BagSection.PACK : BagSection.BASE;
            BagEntry entry = BagEntry.read(buf);
            cells.add(new Cell(section, entry, buf.readDouble()));
        }
        return new S2CBagState(cells, baseRows, packRows);
    }

    public static void handle(S2CBagState msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            // Guarded rather than assumed: a dedicated server must never touch the client cache,
            // and this packet only ever travels one way.
            if (FMLEnvironment.dist == Dist.CLIENT) {
                ClientBagState.accept(msg);
            }
        });
        context.setPacketHandled(true);
    }
}
