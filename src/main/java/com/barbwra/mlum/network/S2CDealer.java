package com.barbwra.mlum.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The dealership to a client: open it, its stock changed, how a purchase went, who is browsing. */
public record S2CDealer(String kind, CompoundTag data) {

    public static void encode(S2CDealer msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.kind, 32);
        buf.writeNbt(msg.data);
    }

    public static S2CDealer decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new S2CDealer(kind, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CDealer msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.dealer.ClientDealer.receive(msg.kind, msg.data)));
        ctx.get().setPacketHandled(true);
    }
}
