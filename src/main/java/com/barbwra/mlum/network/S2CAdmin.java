package com.barbwra.mlum.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Everything the admin system tells a client, as a kind and a tag: the player's own staff
 * standing, the panel's pages, notices for the punished, restart countdowns, who is vanished.
 *
 * <p>One packet with a tag rather than a packet per screen, because the panel has many small pages
 * that change together and are only ever read by the one screen that asked for them.</p>
 */
public record S2CAdmin(String kind, CompoundTag data) {

    public static void encode(S2CAdmin msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.kind, 64);
        buf.writeNbt(msg.data);
    }

    public static S2CAdmin decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(64);
        CompoundTag tag = buf.readNbt();
        return new S2CAdmin(kind, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CAdmin msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.admin.ClientAdmin.receive(msg.kind, msg.data)));
        ctx.get().setPacketHandled(true);
    }
}
