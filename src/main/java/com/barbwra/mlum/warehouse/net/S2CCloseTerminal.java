package com.barbwra.mlum.warehouse.net;

import com.barbwra.mlum.warehouse.client.ClientTerminalData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server-initiated close, sent when an action ends the session - dispatching a convoy, for
 * instance, teleports the player away and the terminal should not follow them there.
 */
public record S2CCloseTerminal() {

    public static void encode(S2CCloseTerminal packet, FriendlyByteBuf buf) {
    }

    public static S2CCloseTerminal decode(FriendlyByteBuf buf) {
        return new S2CCloseTerminal();
    }

    public static void handle(S2CCloseTerminal packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ClientTerminalData::close));
        context.get().setPacketHandled(true);
    }
}
