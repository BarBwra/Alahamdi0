package com.barbwra.mlum.warehouse.net;

import com.barbwra.mlum.warehouse.client.ClientTerminalData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Pushes the whole terminal state to the client, opening the screen if it is not already up.
 *
 * <p>Sent on open and after every accepted action, so the screen is always drawing what the server
 * currently believes rather than what the client last assumed.</p>
 */
public record S2COpenTerminal(TerminalSnapshot snapshot) {

    public static void encode(S2COpenTerminal packet, FriendlyByteBuf buf) {
        packet.snapshot.encode(buf);
    }

    public static S2COpenTerminal decode(FriendlyByteBuf buf) {
        return new S2COpenTerminal(TerminalSnapshot.decode(buf));
    }

    public static void handle(S2COpenTerminal packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTerminalData.accept(packet.snapshot())));
        context.get().setPacketHandled(true);
    }
}
