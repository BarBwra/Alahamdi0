package com.barbwra.mlum.network;

import com.barbwra.mlum.loot.LootSearch;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "I let go of the button." The server would notice on its own when the held-use repeats stop,
 * but half a second later; this makes letting go feel immediate.
 */
public final class C2SLootCancel {

    public static final C2SLootCancel INSTANCE = new C2SLootCancel();

    public static void encode(C2SLootCancel msg, FriendlyByteBuf buf) {
    }

    public static C2SLootCancel decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(C2SLootCancel msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                LootSearch.cancelFromClient(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
