package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientLevelData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The player's own progression plus the whole reward track, pushed whenever points change.
 *
 * <p>Sent on change rather than on a timer, because points only move when the player does something
 * - a kill, a block, a death. Between those events there is nothing to say.</p>
 *
 * <p><b>The track rides along.</b> It is server config, so the client has no other way to learn it,
 * and it is a handful of short rows - cheaper to resend than to build a second packet and a
 * cache-invalidation rule for something that changes only when an operator edits the config.</p>
 *
 * <p>{@code inLevel} and {@code costOfNext} are resolved server-side rather than derived on the
 * client from {@code points}, so the bar cannot disagree with the server about how close the next
 * level is when the curve is retuned mid-session.</p>
 */
public record S2CLevelState(int points, int level, int inLevel, int costOfNext,
                            List<Tier> track, List<Rule> rules) {

    /** One tier of the reward track, as the screen needs to draw it. */
    public record Tier(int level, String icon, String description) {
    }

    /**
     * One line of "how points are earned": {@code +1 قتل زومبي}. {@code kind} is 0 gain, 1 loss,
     * 2 VIP gold, 3 plain; {@code off} greys it out (a boost that is not running).
     */
    public record Rule(String value, int kind, String label, boolean off) {
    }

    public static void encode(S2CLevelState packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.points);
        buf.writeVarInt(packet.level);
        buf.writeVarInt(packet.inLevel);
        buf.writeVarInt(packet.costOfNext);

        buf.writeVarInt(packet.track.size());
        for (Tier tier : packet.track) {
            buf.writeVarInt(tier.level());
            buf.writeUtf(NetText.cut(tier.icon(), 128), 128);
            buf.writeUtf(NetText.cut(tier.description(), 256), 256);
        }
        buf.writeVarInt(packet.rules.size());
        for (Rule rule : packet.rules) {
            buf.writeUtf(NetText.cut(rule.value(), 16), 16);
            buf.writeVarInt(rule.kind());
            buf.writeUtf(NetText.cut(rule.label(), 128), 128);
            buf.writeBoolean(rule.off());
        }
    }

    public static S2CLevelState decode(FriendlyByteBuf buf) {
        int points = buf.readVarInt();
        int level = buf.readVarInt();
        int inLevel = buf.readVarInt();
        int costOfNext = buf.readVarInt();

        int count = buf.readVarInt();
        List<Tier> track = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            track.add(new Tier(buf.readVarInt(), buf.readUtf(128), buf.readUtf(256)));
        }
        int ruleCount = Math.min(buf.readVarInt(), 32);
        List<Rule> rules = new ArrayList<>(ruleCount);
        for (int i = 0; i < ruleCount; i++) {
            rules.add(new Rule(buf.readUtf(16), buf.readVarInt(), buf.readUtf(128), buf.readBoolean()));
        }
        return new S2CLevelState(points, level, inLevel, costOfNext, track, rules);
    }

    public static void handle(S2CLevelState packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientLevelData.accept(packet)));
        context.get().setPacketHandled(true);
    }
}
