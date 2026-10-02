package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientToast;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A short message for the player: shown as the menu's toast when a menu is open, as a chat line
 * otherwise.
 *
 * <p>{@code markup} is logical Arabic where {@code {b}..{/b}} is bold and {@code {n}..{/n}} a
 * number, exactly what the toast draws. {@code bad} gives it the rust border instead of amber.</p>
 */
public record S2CToast(String markup, boolean bad) {

    public static final int MAX = 256;

    public static void encode(S2CToast msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.markup.length() > MAX ? msg.markup.substring(0, MAX) : msg.markup, MAX);
        buf.writeBoolean(msg.bad);
    }

    public static S2CToast decode(FriendlyByteBuf buf) {
        return new S2CToast(buf.readUtf(MAX), buf.readBoolean());
    }

    public static void handle(S2CToast msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientToast.accept(msg.markup(), msg.bad())));
        ctx.get().setPacketHandled(true);
    }
}
