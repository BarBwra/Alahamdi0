package com.barbwra.mlum.network;

import com.barbwra.mlum.admin.AdminActions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Something done in the admin panel, or a player sending a support ticket. Every action is checked
 * against the sender's permissions on the server ({@link AdminActions}) - the panel only asks.
 */
public record C2SAdmin(String action, CompoundTag data) {

    public static void encode(C2SAdmin msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.action, 64);
        buf.writeNbt(msg.data);
    }

    public static C2SAdmin decode(FriendlyByteBuf buf) {
        String action = buf.readUtf(64);
        CompoundTag tag = buf.readNbt();
        return new C2SAdmin(action, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(C2SAdmin msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AdminActions.handle(player, msg.action, msg.data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
