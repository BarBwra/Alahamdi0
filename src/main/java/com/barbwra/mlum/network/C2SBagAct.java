package com.barbwra.mlum.network;

import com.barbwra.mlum.bag.BagSection;
import com.barbwra.mlum.bag.BagService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The two things a player does to a bag cell that are not a move: throw it away, or pull every other
 * stack of the same thing into it.
 *
 * <p><b>Why not vanilla's own clicks.</b> Half the grid is vanilla slots and half is NBT inside the
 * worn backpack, and a player cannot see the difference - nor should they. Routing Q and the
 * double-click through one packet is what makes them behave identically wherever the cursor happens
 * to be; sending vanilla a THROW for base cells and inventing something else for pack cells would
 * give the same gesture two different behaviours in one grid.</p>
 *
 * <p>Like {@link C2SBagMove} this carries coordinates and a ceiling, never a stack and never a
 * verdict. The server reads what is actually in the cell and decides.</p>
 */
public final class C2SBagAct {

    public enum Op {
        /** Onto the ground, {@code amount} of it. */
        DROP,
        /** Every other stack of the same item, into this cell, until it is full. */
        GATHER
    }

    private final int containerId;
    private final Op op;
    private final boolean pack;
    private final int row;
    private final int col;
    /** For {@link Op#DROP}: how many, 0 meaning the whole stack. Ignored otherwise. */
    private final int amount;

    public C2SBagAct(int containerId, Op op, BagSection section, int row, int col, int amount) {
        this.containerId = containerId;
        this.op = op;
        this.pack = section == BagSection.PACK;
        this.row = row;
        this.col = col;
        this.amount = Math.max(0, amount);
    }

    public static C2SBagAct drop(int containerId, BagSection section, int row, int col, int amount) {
        return new C2SBagAct(containerId, Op.DROP, section, row, col, amount);
    }

    public static C2SBagAct gather(int containerId, BagSection section, int row, int col) {
        return new C2SBagAct(containerId, Op.GATHER, section, row, col, 0);
    }

    public static void encode(C2SBagAct msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.containerId);
        buf.writeEnum(msg.op);
        buf.writeBoolean(msg.pack);
        buf.writeVarInt(msg.row);
        buf.writeVarInt(msg.col);
        buf.writeVarInt(msg.amount);
    }

    public static C2SBagAct decode(FriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        Op op = buf.readEnum(Op.class);
        BagSection section = buf.readBoolean() ? BagSection.PACK : BagSection.BASE;
        int row = buf.readVarInt();
        int col = buf.readVarInt();
        return new C2SBagAct(containerId, op, section, row, col, buf.readVarInt());
    }

    public static void handle(C2SBagAct msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            // the same gate every bag packet passes: a menu the server has since replaced takes
            // nothing more, or the coordinates would land on whatever now has those numbers
            if (player.containerMenu.containerId != msg.containerId) {
                BagService.sync(player);
                return;
            }
            BagSection section = msg.pack ? BagSection.PACK : BagSection.BASE;
            switch (msg.op) {
                case DROP -> BagService.drop(player, section, msg.row, msg.col, msg.amount);
                case GATHER -> BagService.gather(player, section, msg.row, msg.col);
            }
        });
        context.setPacketHandled(true);
    }
}
