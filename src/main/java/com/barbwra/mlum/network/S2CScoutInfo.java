package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Which of the containers around a level 3 scout are empty, by container key. */
public record S2CScoutInfo(long[] keys, boolean[] empty) {

    public static void encode(S2CScoutInfo msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.keys.length, msg.empty.length);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeLong(msg.keys[i]);
            buf.writeBoolean(msg.empty[i]);
        }
    }

    public static S2CScoutInfo decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 1024);
        long[] keys = new long[n];
        boolean[] empty = new boolean[n];
        for (int i = 0; i < n; i++) {
            keys[i] = buf.readLong();
            empty[i] = buf.readBoolean();
        }
        return new S2CScoutInfo(keys, empty);
    }

    public static void handle(S2CScoutInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.loot.ClientScoutInfo.set(msg.keys(), msg.empty())));
        ctx.get().setPacketHandled(true);
    }
}
