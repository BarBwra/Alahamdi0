package com.barbwra.mlum.network;

import com.barbwra.mlum.faction.Diplomacy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** A request from the diplomacy screen. Every rule is checked again in {@link Diplomacy}. */
public record C2SDiplomacy(String action, CompoundTag data) {

    public static void encode(C2SDiplomacy msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 32);
        buf.writeNbt(msg.data);
    }

    public static C2SDiplomacy decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new C2SDiplomacy(action, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(C2SDiplomacy msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Diplomacy.handle(player, msg.action, msg.data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
