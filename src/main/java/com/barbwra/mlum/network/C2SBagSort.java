package com.barbwra.mlum.network;

import com.barbwra.mlum.bag.BagService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** "Sort my bag" - the رتّب button. No payload; the server repacks both sections, biggest first. */
public final class C2SBagSort {

    public static final C2SBagSort INSTANCE = new C2SBagSort();

    public static void encode(C2SBagSort msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SBagSort decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(C2SBagSort msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BagService.sort(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
