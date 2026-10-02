package com.barbwra.mlum.client;

import com.barbwra.mlum.network.S2CFactionRoster;
import com.barbwra.mlum.network.S2CFactionState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * The client's copy of the faction view, filled by {@link S2CFactionState}.
 *
 * <p>Every field is {@code volatile} because the packet lands on the network thread and the screen
 * reads on the render thread. The same reason {@link ClientVehicleData} does it.</p>
 *
 * <p>Nothing here is authoritative. The screen draws from it and the buttons ask the server to act;
 * the server never trusts a number that came back from here.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientFactionData {

    private ClientFactionData() {
    }

    private static volatile boolean inFaction = false;
    private static volatile String factionName = "";
    private static volatile int level = 0;
    private static volatile int points = 0;
    private static volatile int pointsFloor = 0;
    private static volatile int pointsForNext = 0;
    private static volatile int bank = 0;
    private static volatile String myRole = "";
    private static volatile String inviteFrom = "";
    private static volatile long createdAt = 0L;
    private static volatile int serverRank = 0;
    private static volatile int factionCount = 0;
    private static volatile int myBalance = 0;
    private static volatile int createCost = 0;
    private static volatile List<S2CFactionState.Member> members = List.of();
    private static volatile List<S2CFactionState.Standing> top = List.of();
    private static volatile String inviteBy = "";
    private static volatile long inviteAt = 0L;
    private static volatile int version;

    /**
     * The invite window's player list.
     *
     * <p>Held separately because it arrives separately and on demand. It is deliberately <b>not</b>
     * cleared when the state packet lands: the two are independent, and wiping the list every time
     * a teammate earned a point would empty the window under the cursor.</p>
     */
    private static volatile List<S2CFactionRoster.Entry> roster = List.of();
    private static volatile boolean rosterLoaded = false;

    public static void set(S2CFactionState state) {
        inFaction = state.inFaction();
        factionName = state.factionName();
        level = state.level();
        points = state.points();
        pointsFloor = state.pointsFloor();
        pointsForNext = state.pointsForNext();
        bank = state.bank();
        myRole = state.myRole();
        inviteFrom = state.inviteFrom();
        createdAt = state.createdAt();
        serverRank = state.serverRank();
        factionCount = state.factionCount();
        myBalance = state.myBalance();
        createCost = state.createCost();
        members = List.copyOf(state.members());
        top = List.copyOf(state.top());
        inviteBy = state.inviteBy();
        inviteAt = state.inviteAt();
        version++;
    }

    /** Who sent the outstanding invite, or blank. */
    public static String inviteBy() {
        return inviteBy;
    }

    /** When the invite was sent (wall clock ms), or 0 when unknown. */
    public static long inviteAt() {
        return inviteAt;
    }

    /** Bumped on every state packet. */
    public static int version() {
        return version;
    }

    public static void setRoster(S2CFactionRoster packet) {
        roster = List.copyOf(packet.entries());
        rosterLoaded = true;
    }

    /** Wiped on disconnect, so a second server never shows the first one's faction. */
    public static void reset() {
        inFaction = false;
        factionName = "";
        level = 0;
        points = 0;
        pointsFloor = 0;
        pointsForNext = 0;
        bank = 0;
        myRole = "";
        inviteFrom = "";
        createdAt = 0L;
        serverRank = 0;
        factionCount = 0;
        myBalance = 0;
        createCost = 0;
        members = List.of();
        top = List.of();
        roster = List.of();
        rosterLoaded = false;
        inviteBy = "";
        inviteAt = 0L;
        version++;
    }

    /** Forgets the invite window's list, so opening it again shows it loading rather than stale. */
    public static void clearRoster() {
        roster = List.of();
        rosterLoaded = false;
    }

    public static boolean inFaction() {
        return inFaction;
    }

    public static String factionName() {
        return factionName;
    }

    public static int level() {
        return level;
    }

    public static int points() {
        return points;
    }

    public static int pointsFloor() {
        return pointsFloor;
    }

    public static int pointsForNext() {
        return pointsForNext;
    }

    /** Points still to earn for the next level, floored at zero at the ceiling. */
    public static int pointsRemaining() {
        return Math.max(0, pointsForNext - points);
    }

    public static int bank() {
        return bank;
    }

    public static String myRole() {
        return myRole;
    }

    public static long createdAt() {
        return createdAt;
    }

    public static int serverRank() {
        return serverRank;
    }

    public static int factionCount() {
        return factionCount;
    }

    public static int myBalance() {
        return myBalance;
    }

    public static int createCost() {
        return createCost;
    }

    /** Name of the faction inviting this player, or empty when there is no invite. */
    public static String inviteFrom() {
        return inviteFrom;
    }

    public static boolean invited() {
        return !inviteFrom.isEmpty();
    }

    public static List<S2CFactionState.Member> members() {
        return members;
    }

    public static List<S2CFactionState.Standing> top() {
        return top;
    }

    public static List<S2CFactionRoster.Entry> roster() {
        return roster;
    }

    public static boolean rosterLoaded() {
        return rosterLoaded;
    }

    public static int onlineMembers() {
        int count = 0;
        for (S2CFactionState.Member member : members) {
            if (member.online()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Progress through the <i>current</i> level, 0 to 1.
     *
     * <p>Measured from the level's own floor, not from zero. The earlier version divided the
     * lifetime total by the next threshold, so a faction that had just reached level 3 on 30,000
     * points drew a bar three quarters full against a 40,000 target - it only ever read empty at
     * level 0. The floor is what makes a fresh level start at an empty bar.</p>
     */
    public static float progress() {
        int span = pointsForNext - pointsFloor;
        if (span <= 0) {
            return 1.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (points - pointsFloor) / (float) span));
    }
}
