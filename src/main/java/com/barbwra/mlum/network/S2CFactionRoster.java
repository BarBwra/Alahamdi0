package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientFactionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who is online, and whether they can be invited.
 *
 * <p><b>Separate from {@link S2CFactionState} on purpose.</b> The state packet is the screen's whole
 * view and is pushed to every online member whenever anything about their faction changes - a zombie
 * kill is enough. The roster is a payload for one window, requested when that window opens. Folding
 * it into the state packet would put the entire player list on the wire every time anyone earns a
 * point, for every member of every faction.</p>
 *
 * <p><b>Online players only.</b> An offline player cannot be told they were invited, and reading
 * every profile that has ever joined means touching disk to build a packet. The list is therefore
 * exactly what the server already has in memory.</p>
 *
 * <p>{@code factionName} empty means free to invite. Anything else is shown greyed with that name
 * in place of the button - the "he can see them but cannot invite them" the screen asks for. The
 * client still only <i>draws</i> that rule; {@code FactionService} re-checks it on arrival.</p>
 */
public record S2CFactionRoster(List<Entry> entries) {

    public record Entry(UUID id, String name, String factionName, boolean invited) {
    }

    /** A server with more online players than this has bigger problems than a truncated list. */
    private static final int MAX_ROWS = 512;

    public static void encode(S2CFactionRoster msg, FriendlyByteBuf buf) {
        int count = Math.min(msg.entries.size(), MAX_ROWS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = msg.entries.get(i);
            buf.writeUUID(entry.id());
            buf.writeUtf(entry.name(), 32);
            buf.writeUtf(entry.factionName(), 64);
            buf.writeBoolean(entry.invited());
        }
    }

    public static S2CFactionRoster decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_ROWS);
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUUID(), buf.readUtf(32), buf.readUtf(64), buf.readBoolean()));
        }
        return new S2CFactionRoster(entries);
    }

    public static void handle(S2CFactionRoster msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientFactionData.setRoster(msg)));
        ctx.get().setPacketHandled(true);
    }
}
