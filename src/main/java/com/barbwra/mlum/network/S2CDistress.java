package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A faction member is down and calling for help: who, where, and for how long to show it. */
public record S2CDistress(String name, double x, double y, double z, int seconds) {

    public static void encode(S2CDistress msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, 32);
        buf.writeDouble(msg.x);
        buf.writeDouble(msg.y);
        buf.writeDouble(msg.z);
        buf.writeVarInt(msg.seconds);
    }

    public static S2CDistress decode(FriendlyByteBuf buf) {
        return new S2CDistress(buf.readUtf(32), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    public static void handle(S2CDistress msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.downed.ClientDistress.add(msg)));
        ctx.get().setPacketHandled(true);
    }
}
