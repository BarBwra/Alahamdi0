package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientQuestBoard;
import com.barbwra.mlum.quest.QuestBoard;
import com.barbwra.mlum.quest.QuestEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The whole quest board in one packet. It is at most {@link QuestBoard#MAX_LINES} short entries and
 * only moves when Skript actually changes something, so sending it whole is cheaper than tracking
 * deltas - and it removes any chance of the client drifting out of step.
 */
public record S2CQuestBoard(List<QuestEntry> entries, Set<Integer> claimed) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(Math.min(entries.size(), QuestBoard.MAX_LINES));
        for (int i = 0; i < entries.size() && i < QuestBoard.MAX_LINES; i++) {
            entries.get(i).write(buf);
        }
        buf.writeByte(Math.min(claimed.size(), QuestBoard.MAX_LINES));
        int written = 0;
        for (int line : claimed) {
            if (written++ >= QuestBoard.MAX_LINES) {
                break;
            }
            buf.writeVarInt(line);
        }
    }

    public static S2CQuestBoard decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readByte(), QuestBoard.MAX_LINES);
        List<QuestEntry> entries = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            entries.add(QuestEntry.read(buf));
        }
        int claimedCount = Math.min(buf.readByte(), QuestBoard.MAX_LINES);
        Set<Integer> claimed = new LinkedHashSet<>();
        for (int i = 0; i < claimedCount; i++) {
            claimed.add(buf.readVarInt());
        }
        return new S2CQuestBoard(entries, claimed);
    }

    public static void handle(S2CQuestBoard msg, Supplier<NetworkEvent.Context> ctx) {
        // the supplier body is only ever linked on the physical client
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientQuestBoard.set(msg.entries(), msg.claimed()));
    }
}
