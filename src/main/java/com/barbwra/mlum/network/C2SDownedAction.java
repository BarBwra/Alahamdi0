package com.barbwra.mlum.network;

import com.barbwra.mlum.downed.DownedService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Something a player did to a downed body, or that a downed player did themselves.
 *
 * <p>Only names the action and the body. Range, who may do what, which item is in hand and how long
 * a revive has really been held are all checked on the server; a client that lies gets nowhere.</p>
 */
public record C2SDownedAction(int action, int target) {

    public static final int LOOT = 0;
    /** Repeated while F is held on the revive option; the revive runs only while these keep coming. */
    public static final int REVIVE = 1;
    public static final int STOP = 2;
    public static final int GIVE_UP = 3;
    public static final int DISTRESS = 4;
    public static final int DRAG = 5;
    public static final int DEFIB = 6;

    public static void encode(C2SDownedAction msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.action);
        buf.writeVarInt(msg.target);
    }

    public static C2SDownedAction decode(FriendlyByteBuf buf) {
        return new C2SDownedAction(buf.readByte(), buf.readVarInt());
    }

    public static void handle(C2SDownedAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                DownedService.act(player, msg.action, msg.target);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
