package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The player's balance.
 *
 * <p>Needed because the wallet stopped being a count of items the client could already see. An
 * account lives only on the server, so the top bar has nothing to read unless it is told - and it is
 * told on login, on respawn, and every time the number changes.</p>
 */
public record S2CWallet(long balance) {

    public static void encode(S2CWallet msg, FriendlyByteBuf buf) {
        buf.writeVarLong(msg.balance);
    }

    public static S2CWallet decode(FriendlyByteBuf buf) {
        return new S2CWallet(buf.readVarLong());
    }

    public static void handle(S2CWallet msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.ClientWallet.accept(msg.balance())));
        ctx.get().setPacketHandled(true);
    }
}
