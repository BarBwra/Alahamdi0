package com.barbwra.mlum.network;

import com.barbwra.mlum.bag.BagSection;
import com.barbwra.mlum.bag.BagService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Move the item at A to B." A <b>request</b>, and nothing more.
 *
 * <h2>The client is never trusted</h2>
 * <p>This carries only coordinates - never the stack, never a size, never a verdict. The server
 * looks up what is actually at the source, works out its footprint from its own config, tests the
 * destination against its own grid, and either performs the move or refuses it and resyncs. A packet
 * that carried the item would let a modified client mint one; a packet that carried "this fits" would
 * let it place a 7x2 rifle into a single cell.</p>
 *
 * <p>{@code amount} is the one number the client may name, and it is a <b>ceiling, not an
 * instruction</b>: the server moves at most that many and never more than the source really holds.
 * It is what lets a right-click drop one at a time and a split carry half, without letting a client
 * conjure a count - the stack it names is still the server's own.</p>
 */
public final class C2SBagMove {

    /** Where a move starts or ends. Sections are the bag halves; the rest are ordinary slots. */
    public enum Place {
        /** The base 27, stored on the player. */
        BAG_BASE,
        /** The worn backpack's rows, stored in the pack. */
        BAG_PACK,
        /** A quick-access slot, always one cell per item. */
        QUICK,
        /** Anything reachable as a normal menu slot - chest, gear, weapons. */
        SLOT;

        public BagSection section() {
            return this == BAG_PACK ? BagSection.PACK : BagSection.BASE;
        }

        public boolean isBag() {
            return this == BAG_BASE || this == BAG_PACK;
        }
    }

    /**
     * The menu the move was made in. A move aimed at a menu the server has since replaced - the
     * vault page turned, the chest closed - is refused instead of landing on whatever slot now has
     * that number.
     */
    private final int containerId;
    private final Place from;
    private final int fromA;
    private final int fromB;
    private final Place to;
    private final int toA;
    private final int toB;
    /** How much of the source stack to move; 0 means all of it. */
    private final int amount;

    /**
     * @param fromA row for a bag place, otherwise the slot or quick index
     * @param fromB column for a bag place, otherwise ignored
     */
    public C2SBagMove(int containerId, Place from, int fromA, int fromB, Place to, int toA, int toB) {
        this(containerId, from, fromA, fromB, to, toA, toB, 0);
    }

    public C2SBagMove(int containerId, Place from, int fromA, int fromB, Place to, int toA, int toB,
                      int amount) {
        this.containerId = containerId;
        this.from = from;
        this.fromA = fromA;
        this.fromB = fromB;
        this.to = to;
        this.toA = toA;
        this.toB = toB;
        this.amount = Math.max(0, amount);
    }

    public static void encode(C2SBagMove msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeEnum(msg.from);
        buf.writeVarInt(msg.fromA);
        buf.writeVarInt(msg.fromB);
        buf.writeEnum(msg.to);
        buf.writeVarInt(msg.toA);
        buf.writeVarInt(msg.toB);
        buf.writeVarInt(msg.amount);
    }

    public static C2SBagMove decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        Place from = buf.readEnum(Place.class);
        int fromA = buf.readVarInt();
        int fromB = buf.readVarInt();
        Place to = buf.readEnum(Place.class);
        int toA = buf.readVarInt();
        int toB = buf.readVarInt();
        return new C2SBagMove(containerId, from, fromA, fromB, to, toA, toB, buf.readVarInt());
    }

    public static void handle(C2SBagMove msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (player.containerMenu.containerId != msg.containerId) {
                BagService.sync(player);
                return;
            }
            BagService.move(player, msg.from, msg.fromA, msg.fromB, msg.to, msg.toA, msg.toB, msg.amount);
        });
        context.setPacketHandled(true);
    }
}
