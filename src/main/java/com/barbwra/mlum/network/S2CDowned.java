package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A downed player's state, to the player themselves and to everyone who can see them: down or up,
 * how long is left, and how far a revive has got and who is doing it.
 */
public record S2CDowned(int entityId, boolean downed, int remaining, int total, float revive,
                        String reviver) {

    public static void encode(S2CDowned msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.downed);
        buf.writeVarInt(Math.max(0, msg.remaining));
        buf.writeVarInt(Math.max(1, msg.total));
        buf.writeFloat(msg.revive);
        buf.writeUtf(msg.reviver == null ? "" : msg.reviver, 32);
    }

    public static S2CDowned decode(FriendlyByteBuf buf) {
        return new S2CDowned(buf.readVarInt(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readUtf(32));
    }

    public static void handle(S2CDowned msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.downed.ClientDowned.update(msg)));
        ctx.get().setPacketHandled(true);
    }
}
