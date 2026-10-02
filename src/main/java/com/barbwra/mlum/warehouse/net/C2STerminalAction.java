package com.barbwra.mlum.warehouse.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The only thing a client can ever say to this mod.
 *
 * <p>An action ordinal, an optional short string, and two ints. That is the whole vocabulary. It is
 * handled on the main thread and passed straight to {@link TerminalSession#handle}, which re-checks
 * session, range, rate limit and the action's own rule before anything changes.</p>
 *
 * <p>The string is length-capped at decode, so a hostile client cannot force an oversized
 * allocation by claiming a huge payload.</p>
 */
public record C2STerminalAction(int action, String text, int a, int b) {

    public static final int MAX_TEXT = 64;

    public static void encode(C2STerminalAction packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action);
        buf.writeUtf(packet.text == null ? "" : packet.text, MAX_TEXT);
        buf.writeVarInt(packet.a);
        buf.writeVarInt(packet.b);
    }

    public static C2STerminalAction decode(FriendlyByteBuf buf) {
        return new C2STerminalAction(buf.readVarInt(), buf.readUtf(MAX_TEXT),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2STerminalAction packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player != null) {
                TerminalSession.handle(player, TerminalAction.byId(packet.action()),
                        packet.text(), packet.a(), packet.b());
            }
        });
        context.get().setPacketHandled(true);
    }
}
