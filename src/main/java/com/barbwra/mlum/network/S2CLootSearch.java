package com.barbwra.mlum.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A search's state, every tick it runs: how far along, whether Shift has it on the fast rate, and
 * whether a noise has it stalled. The last packet of a search says it finished or was cancelled.
 */
public record S2CLootSearch(int state, BlockPos pos, float progress, boolean fast, boolean paused) {

    public static final int PROGRESS = 0;
    public static final int DONE = 1;
    public static final int CANCEL = 2;
    public static final int NOISE = 3;

    public static void encode(S2CLootSearch msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.state);
        buf.writeBlockPos(msg.pos);
        buf.writeFloat(msg.progress);
        buf.writeBoolean(msg.fast);
        buf.writeBoolean(msg.paused);
    }

    public static S2CLootSearch decode(FriendlyByteBuf buf) {
        return new S2CLootSearch(buf.readByte(), buf.readBlockPos(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(S2CLootSearch msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.loot.ClientLootSearch.update(msg)));
        ctx.get().setPacketHandled(true);
    }
}
