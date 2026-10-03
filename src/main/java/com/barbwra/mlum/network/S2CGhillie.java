package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Who is hidden by a ghillie suit right now, by entity id.
 *
 * <p>Sent rather than read off the invisible flag, because that flag is shared: a potion, another
 * mod's suit or a staff vanish set it too, and only a player this mod hid may be drawn as the
 * shimmer and counted as hidden on the HUD.</p>
 */
public record S2CGhillie(int[] ids) {

    public static void encode(S2CGhillie msg, FriendlyByteBuf buf) {
        buf.writeVarIntArray(msg.ids);
    }

    public static S2CGhillie decode(FriendlyByteBuf buf) {
        return new S2CGhillie(buf.readVarIntArray(1024));
    }

    public static void handle(S2CGhillie msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.camo.GhillieClient.receive(msg.ids)));
        ctx.get().setPacketHandled(true);
    }
}
