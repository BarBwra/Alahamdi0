package com.barbwra.mlum.network;

import com.barbwra.mlum.dealer.Dealer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Something done in the dealership: buy, close, or an edit. All of it is re-checked in {@link Dealer}. */
public record C2SDealer(String action, CompoundTag data) {

    public static void encode(C2SDealer msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 32);
        buf.writeNbt(msg.data);
    }

    public static C2SDealer decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new C2SDealer(action, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(C2SDealer msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Dealer.handle(player, msg.action, msg.data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
