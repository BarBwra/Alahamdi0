package com.barbwra.mlum.network;

import com.barbwra.mlum.zone.MenuGuard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "One of your menus is open on my screen." Sent while a tab is up, and once when it closes.
 *
 * <p>Only the bag is a real container the server can see; the other five tabs are plain screens that
 * never talk to it. This is how {@link MenuGuard} knows about those - and it is a repeated claim
 * rather than a latch, so a flag left behind by a crash or a lost connection expires by itself.</p>
 */
public record C2SMenuOpen(boolean open) {

    public static void encode(C2SMenuOpen msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
    }

    public static C2SMenuOpen decode(FriendlyByteBuf buf) {
        return new C2SMenuOpen(buf.readBoolean());
    }

    public static void handle(C2SMenuOpen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                MenuGuard.set(player, msg.open());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
