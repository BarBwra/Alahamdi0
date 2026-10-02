package com.barbwra.mlum.network;

import com.barbwra.mlum.faction.FactionService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A button press in the المنظمة screen.
 *
 * <p><b>Carries an intent, never a result.</b> The client says "I pressed join", not "add me to
 * faction X at rank Y" - every rule is re-checked server-side in {@link FactionService}. A packet
 * from a modified client can therefore ask for things it is not allowed and simply be refused, which
 * is the only safe shape for a system that hands out shared storage.</p>
 */
public record C2SFactionAction(Action action, String argument) {

    /**
     * <p><b>Appended, never reordered.</b> The ordinal is what goes on the wire, so inserting a
     * constant above an existing one would make an old client's "leave" arrive as someone else's
     * "disband".</p>
     */
    public enum Action {
        /** Refresh the whole view - sent when the screen opens. */
        REFRESH,
        /** Create a faction named by {@link #argument}. */
        CREATE,
        /** Accept the outstanding invite. */
        JOIN,
        /** Decline the outstanding invite. */
        REJECT,
        /** Leave the current faction. */
        LEAVE,
        /** Ask for the online player list. Answered with {@code S2CFactionRoster}. */
        ROSTER,
        /** Invite the player whose UUID is in {@link #argument}. */
        INVITE,
        /** Remove the member whose UUID is in {@link #argument}. */
        KICK,
        /** Move the member in {@link #argument} one rank up. */
        PROMOTE,
        /** Move the member in {@link #argument} one rank down. */
        DEMOTE,
        /** Rename the faction to {@link #argument}. Leader only. */
        RENAME,
        /** Hand the faction to the member in {@link #argument}. Leader only. */
        TRANSFER,
        /** Disband. {@link #argument} must repeat the faction's name, as the screen asks. */
        DISBAND,
        /** Pay {@link #argument} currency into the faction bank. Any rank may donate. */
        DONATE,
        /** Open page 1 of the player's own faction vault, if their rank may see it. */
        OPEN_VAULT;

        private static final Action[] VALUES = values();

        static Action byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : REFRESH;
        }
    }

    public static void encode(C2SFactionAction msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action.ordinal());
        buf.writeUtf(msg.argument, 64);
    }

    public static C2SFactionAction decode(FriendlyByteBuf buf) {
        // Unknown ordinals fall back to a harmless refresh rather than throwing on the netty thread
        return new C2SFactionAction(Action.byId(buf.readVarInt()), buf.readUtf(64));
    }

    public static void handle(C2SFactionAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                FactionService.handle(player, msg.action(), msg.argument());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
