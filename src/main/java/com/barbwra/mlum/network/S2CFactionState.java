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
 * Everything the المنظمة screen needs, in one packet.
 *
 * <p><b>Why one packet rather than several.</b> The screen has three states - no faction, in a
 * faction, invited - and which one it shows depends on fields that would otherwise arrive
 * separately. Splitting them means a frame where the client believes it is both invited and a
 * member, and the screen draws a button that should not exist. Sending the whole view at once makes
 * that unrepresentable.</p>
 *
 * <p>The invite picker's player list is deliberately <i>not</i> here - see
 * {@link S2CFactionRoster}. It is not view state; it is a payload fetched when a window opens, and
 * folding it in would mean every member of every faction receives the whole online roster every
 * time anyone earns a point.</p>
 *
 * <p>Sent on login, on any membership change, and whenever the screen is opened.</p>
 */
public record S2CFactionState(boolean inFaction,
                              String factionName,
                              int level,
                              int points,
                              /** Lifetime points at the start of the current level - the bar's floor. */
                              int pointsFloor,
                              /** Lifetime points that reach the next level - the bar's ceiling. */
                              int pointsForNext,
                              int bank,
                              String myRole,
                              String inviteFrom,
                              long createdAt,
                              /** 1-based position on the server leaderboard; 0 when not in a faction. */
                              int serverRank,
                              int factionCount,
                              /** The viewer's own currency, so the create screen can price itself. */
                              int myBalance,
                              int createCost,
                              List<Member> members,
                              List<Standing> top,
                              /** Who sent the outstanding invite, and when; blank when unknown. */
                              String inviteBy,
                              long inviteAt) {

    /**
     * One row of the members list.
     *
     * <p>Carries the {@link UUID} and not only the name: promote, demote and kick are aimed by
     * clicking a row, and two players can share a display name long enough for the wrong one to be
     * thrown out. The name is for reading; the id is what the action travels on.</p>
     */
    public record Member(UUID id, String name, String role, int contributed, int donated,
                         int playerLevel, boolean online, long joinedAt) {
    }

    /** One row of the leaderboard. */
    public record Standing(String name, int level, int points, int bank, int members, boolean mine) {
    }

    /** Guards against a hostile or broken peer sizing a list into an allocation failure. */
    private static final int MAX_ROWS = 256;

    public static void encode(S2CFactionState msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.inFaction);
        buf.writeUtf(msg.factionName, 64);
        buf.writeVarInt(msg.level);
        buf.writeVarInt(msg.points);
        buf.writeVarInt(msg.pointsFloor);
        buf.writeVarInt(msg.pointsForNext);
        buf.writeVarInt(msg.bank);
        buf.writeUtf(msg.myRole, 32);
        buf.writeUtf(msg.inviteFrom, 64);
        buf.writeLong(msg.createdAt);
        buf.writeVarInt(msg.serverRank);
        buf.writeVarInt(msg.factionCount);
        buf.writeVarInt(msg.myBalance);
        buf.writeVarInt(msg.createCost);

        int memberCount = Math.min(msg.members.size(), MAX_ROWS);
        buf.writeVarInt(memberCount);
        for (int i = 0; i < memberCount; i++) {
            Member member = msg.members.get(i);
            buf.writeUUID(member.id());
            buf.writeUtf(member.name(), 32);
            buf.writeUtf(member.role(), 32);
            buf.writeVarInt(member.contributed());
            buf.writeVarInt(member.donated());
            buf.writeVarInt(member.playerLevel());
            buf.writeBoolean(member.online());
            buf.writeLong(member.joinedAt());
        }

        int topCount = Math.min(msg.top.size(), MAX_ROWS);
        buf.writeVarInt(topCount);
        for (int i = 0; i < topCount; i++) {
            Standing standing = msg.top.get(i);
            buf.writeUtf(standing.name(), 64);
            buf.writeVarInt(standing.level());
            buf.writeVarInt(standing.points());
            buf.writeVarInt(standing.bank());
            buf.writeVarInt(standing.members());
            buf.writeBoolean(standing.mine());
        }
        buf.writeUtf(msg.inviteBy, 32);
        buf.writeLong(msg.inviteAt);
    }

    public static S2CFactionState decode(FriendlyByteBuf buf) {
        boolean in = buf.readBoolean();
        String name = buf.readUtf(64);
        int level = buf.readVarInt();
        int points = buf.readVarInt();
        int floor = buf.readVarInt();
        int next = buf.readVarInt();
        int bank = buf.readVarInt();
        String role = buf.readUtf(32);
        String invite = buf.readUtf(64);
        long created = buf.readLong();
        int rank = buf.readVarInt();
        int count = buf.readVarInt();
        int balance = buf.readVarInt();
        int cost = buf.readVarInt();

        int memberCount = Math.min(buf.readVarInt(), MAX_ROWS);
        List<Member> members = new ArrayList<>(memberCount);
        for (int i = 0; i < memberCount; i++) {
            members.add(new Member(buf.readUUID(), buf.readUtf(32), buf.readUtf(32),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readLong()));
        }

        int topCount = Math.min(buf.readVarInt(), MAX_ROWS);
        List<Standing> top = new ArrayList<>(topCount);
        for (int i = 0; i < topCount; i++) {
            top.add(new Standing(buf.readUtf(64), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));
        }

        String inviteBy = buf.readUtf(32);
        long inviteAt = buf.readLong();
        return new S2CFactionState(in, name, level, points, floor, next, bank, role, invite,
                created, rank, count, balance, cost, members, top, inviteBy, inviteAt);
    }

    public static void handle(S2CFactionState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientFactionData.set(msg)));
        ctx.get().setPacketHandled(true);
    }
}
