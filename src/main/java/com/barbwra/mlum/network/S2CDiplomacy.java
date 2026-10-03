package com.barbwra.mlum.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The diplomacy screen's data: alliances, wars, bounties, the factions and the players online. */
public record S2CDiplomacy(String kind, CompoundTag data) {

    public static void encode(S2CDiplomacy msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.kind, 32);
        buf.writeNbt(msg.data);
    }

    public static S2CDiplomacy decode(FriendlyByteBuf buf) {
        String kind = buf.readUtf(32);
        CompoundTag tag = buf.readNbt();
        return new S2CDiplomacy(kind, tag == null ? new CompoundTag() : tag);
    }

    public static void handle(S2CDiplomacy msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.diplomacy.ClientDiplomacy.receive(msg.kind, msg.data)));
        ctx.get().setPacketHandled(true);
    }
}
