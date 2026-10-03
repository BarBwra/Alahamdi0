package com.barbwra.mlum.network;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.quest.QuestEntry;
import com.barbwra.mlum.vehicle.VehicleEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Set;

/**
 * One channel, four packets.
 *
 * <p>Everything grid-shaped - container contents, ground item stacks, the scroll offset and the
 * detected item count - still rides on vanilla's container sync, which already diffs slots and
 * {@code ContainerData} per tick. Packets exist only for the things vanilla has no channel for:
 * opening the menu, and the two text systems: quests and vehicles.</p>
 *
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    /**
     * Bumped whenever a packet's fields change shape.
     *
     * <p>Forge refuses a connection whose channel version does not match, which is the only thing
     * standing between a stale client and reading the new faction state packet as the old one - the
     * fields would decode into each other's places and the screen would draw confident nonsense. A
     * clear "outdated mod" at the login screen is worth far more than a silent misread.</p>
     *
     * <p>3 -&gt; 4: faction state gained member ids, online flags and the level floor; the online
     * roster packet was added.</p>
     *
     * <p>4 -&gt; 5: the bag packets were added - {@code C2SBagMove} and {@code S2CBagState}. Bumped
     * because the ids of every later packet shifted, so a version-4 client would decode a bag state
     * as a vault page and act on garbage.</p>
     *
     * <p>5 -&gt; 6: the new menus. The level state gained its rules, the faction state its invite
     * sender, and toasts, bag sorting, the bag config, skills and skill buying were appended.</p>
     *
     * <p>6 -&gt; 7: the bag grew vanilla's own hands. {@code S2CBagState} now carries which inventory
     * slot each base entry is - without it a client could not tell a base cell from a pack cell and
     * dropped every move on the first free square. {@code C2SBagMove} gained an amount (right-click
     * one at a time, split), {@code C2SBagAct} was added for Q and the double-click, and
     * {@code S2CWallet} for the balance, which is no longer a count of items the client can see.</p>
     *
     * <p>7 -&gt; 8: the skills grew a "coming soon" flag and a drop-for-a-fee action, and
     * {@code C2SMenuOpen} was added so the server knows a player is reading a tab that is not the
     * bag - the menu guard cannot see those any other way.</p>
     *
     * <p>8 -&gt; 9: {@code C2SVehicleLock} was added for the L key. The lock state itself rides on
     * the vehicle entity's own data and needs no packet.</p>
     *
     * <p>9 -&gt; 10: {@code S2CRanks} was added - the ladder, the money packs and which rank the
     * player holds, which the top bar now draws where the safe-zone pill used to be.</p>
     *
     * <p>10 -&gt; 11: {@code S2CVehicleLockState}. The lock HUD read the vehicle's persistent data
     * directly, which Forge never sends to clients - so it drew nothing, ever. The rider is told
     * instead.</p>
     *
     * <p>11 -&gt; 12: the worn backpack moved into the Curios {@code back} slot, which Curios already
     * syncs and renders - so the packet this version briefly added was removed again. The bump
     * stands because 11 shipped.</p>
     *
     * <p>12 -&gt; 13: the downed system and the timed container search - {@code S2CDowned},
     * {@code C2SDownedAction}, {@code S2CDistress}, {@code S2CLootSearch}, {@code C2SLootCancel}
     * and {@code S2CScoutInfo} (the scout's empty-container hints).</p>
     *
     * <p>13 -&gt; 14: dragging a downed body was taken out, and {@code S2CDowned} lost its
     * "being dragged" flag with it. The loot screen of a downed body also carries the body's
     * entity id after the vault bytes.</p>
     *
     * <p>14 -&gt; 15: the admin system - {@code S2CAdmin} and {@code C2SAdmin}.</p>
     */
    private static final String PROTOCOL = "15";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            MlumInventory.id("main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.messageBuilder(C2SOpenMlumInventory.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SOpenMlumInventory::encode)
                .decoder(C2SOpenMlumInventory::decode)
                .consumerMainThread(C2SOpenMlumInventory::handle)
                .add();

        CHANNEL.messageBuilder(S2CQuestBoard.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CQuestBoard::encode)
                .decoder(S2CQuestBoard::decode)
                .consumerMainThread(S2CQuestBoard::handle)
                .add();

        CHANNEL.messageBuilder(C2SClaimReward.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SClaimReward::encode)
                .decoder(C2SClaimReward::decode)
                .consumerMainThread(C2SClaimReward::handle)
                .add();

        CHANNEL.messageBuilder(S2CLevelUp.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLevelUp::encode)
                .decoder(S2CLevelUp::decode)
                .consumerMainThread(S2CLevelUp::handle)
                .add();





        CHANNEL.messageBuilder(S2CVehicles.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CVehicles::encode)
                .decoder(S2CVehicles::decode)
                .consumerMainThread(S2CVehicles::handle)
                .add();

        CHANNEL.messageBuilder(C2SVehicleAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SVehicleAction::encode)
                .decoder(C2SVehicleAction::decode)
                .consumerMainThread(C2SVehicleAction::handle)
                .add();

        // Appended, never inserted - ids come from declaration order, so slotting a packet in
        // above this line would silently repoint every id after it.
        CHANNEL.messageBuilder(S2CZoneNotice.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CZoneNotice::encode)
                .decoder(S2CZoneNotice::decode)
                .consumerMainThread(S2CZoneNotice::handle)
                .add();

        CHANNEL.messageBuilder(S2CLevelState.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLevelState::encode)
                .decoder(S2CLevelState::decode)
                .consumerMainThread(S2CLevelState::handle)
                .add();

        CHANNEL.messageBuilder(S2CFactionState.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFactionState::encode)
                .decoder(S2CFactionState::decode)
                .consumerMainThread(S2CFactionState::handle)
                .add();

        CHANNEL.messageBuilder(C2SFactionAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SFactionAction::encode)
                .decoder(C2SFactionAction::decode)
                .consumerMainThread(C2SFactionAction::handle)
                .add();

        CHANNEL.messageBuilder(S2CFactionRoster.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CFactionRoster::encode)
                .decoder(S2CFactionRoster::decode)
                .consumerMainThread(S2CFactionRoster::handle)
                .add();

        CHANNEL.messageBuilder(C2SVaultPage.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SVaultPage::encode)
                .decoder(C2SVaultPage::decode)
                .consumerMainThread(C2SVaultPage::handle)
                .add();

        CHANNEL.messageBuilder(C2SBagMove.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBagMove::encode)
                .decoder(C2SBagMove::decode)
                .consumerMainThread(C2SBagMove::handle)
                .add();

        CHANNEL.messageBuilder(S2CBagState.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBagState::encode)
                .decoder(S2CBagState::decode)
                .consumerMainThread(S2CBagState::handle)
                .add();

        CHANNEL.messageBuilder(S2CToast.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CToast::encode)
                .decoder(S2CToast::decode)
                .consumerMainThread(S2CToast::handle)
                .add();

        CHANNEL.messageBuilder(C2SBagSort.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBagSort::encode)
                .decoder(C2SBagSort::decode)
                .consumerMainThread(C2SBagSort::handle)
                .add();

        CHANNEL.messageBuilder(S2CBagConfig.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBagConfig::encode)
                .decoder(S2CBagConfig::decode)
                .consumerMainThread(S2CBagConfig::handle)
                .add();

        CHANNEL.messageBuilder(S2CSkills.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSkills::encode)
                .decoder(S2CSkills::decode)
                .consumerMainThread(S2CSkills::handle)
                .add();

        CHANNEL.messageBuilder(C2SSkillBuy.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSkillBuy::encode)
                .decoder(C2SSkillBuy::decode)
                .consumerMainThread(C2SSkillBuy::handle)
                .add();

        CHANNEL.messageBuilder(C2SBagAct.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBagAct::encode)
                .decoder(C2SBagAct::decode)
                .consumerMainThread(C2SBagAct::handle)
                .add();

        CHANNEL.messageBuilder(S2CWallet.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CWallet::encode)
                .decoder(S2CWallet::decode)
                .consumerMainThread(S2CWallet::handle)
                .add();

        CHANNEL.messageBuilder(C2SMenuOpen.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SMenuOpen::encode)
                .decoder(C2SMenuOpen::decode)
                .consumerMainThread(C2SMenuOpen::handle)
                .add();

        CHANNEL.messageBuilder(C2SVehicleLock.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SVehicleLock::encode)
                .decoder(C2SVehicleLock::decode)
                .consumerMainThread(C2SVehicleLock::handle)
                .add();

        CHANNEL.messageBuilder(S2CRanks.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CRanks::encode)
                .decoder(S2CRanks::decode)
                .consumerMainThread(S2CRanks::handle)
                .add();

        CHANNEL.messageBuilder(S2CVehicleLockState.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CVehicleLockState::encode)
                .decoder(S2CVehicleLockState::decode)
                .consumerMainThread(S2CVehicleLockState::handle)
                .add();

        CHANNEL.messageBuilder(S2CLootSearch.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CLootSearch::encode)
                .decoder(S2CLootSearch::decode)
                .consumerMainThread(S2CLootSearch::handle)
                .add();

        CHANNEL.messageBuilder(C2SLootCancel.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SLootCancel::encode)
                .decoder(C2SLootCancel::decode)
                .consumerMainThread(C2SLootCancel::handle)
                .add();

        CHANNEL.messageBuilder(S2CScoutInfo.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CScoutInfo::encode)
                .decoder(S2CScoutInfo::decode)
                .consumerMainThread(S2CScoutInfo::handle)
                .add();
        CHANNEL.messageBuilder(S2CDowned.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CDowned::encode)
                .decoder(S2CDowned::decode)
                .consumerMainThread(S2CDowned::handle)
                .add();

        CHANNEL.messageBuilder(C2SDownedAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SDownedAction::encode)
                .decoder(C2SDownedAction::decode)
                .consumerMainThread(C2SDownedAction::handle)
                .add();

        CHANNEL.messageBuilder(S2CDistress.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CDistress::encode)
                .decoder(S2CDistress::decode)
                .consumerMainThread(S2CDistress::handle)
                .add();

        CHANNEL.messageBuilder(S2CAdmin.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CAdmin::encode)
                .decoder(S2CAdmin::decode)
                .consumerMainThread(S2CAdmin::handle)
                .add();
        CHANNEL.messageBuilder(C2SAdmin.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SAdmin::encode)
                .decoder(C2SAdmin::decode)
                .consumerMainThread(C2SAdmin::handle)
                .add();

    }

    /** The player's balance. Sent on login and respawn, and whenever it changes. */
    public static void sendWallet(ServerPlayer player, long balance) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CWallet(balance));
        }
    }

    /** The server's bag config to one player - on login, and to everyone after a reload. */
    public static void sendBagConfig(ServerPlayer player) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CBagConfig(com.barbwra.mlum.bag.BagConfig.snapshot()));
        }
    }

    /* ------------------------------------------------------------------ senders */

    private static boolean canSend(ServerPlayer player) {
        return player != null && player.connection != null;
    }

    public static void sendQuestBoard(ServerPlayer player, List<QuestEntry> entries, Set<Integer> claimed) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CQuestBoard(entries, claimed));
        }
    }



    /** The whole faction view. Built by {@code FactionService}, which owns the rules. */
    public static void sendFactionState(ServerPlayer player, S2CFactionState state) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
        }
    }

    /** The online player list, sent only in answer to the invite window opening. */
    public static void sendFactionRoster(ServerPlayer player, S2CFactionRoster roster) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), roster);
        }
    }

    /** Asks the server to act on a button press in the faction screen. */
    public static void sendFactionAction(C2SFactionAction.Action action, String argument) {
        CHANNEL.sendToServer(new C2SFactionAction(action, argument == null ? "" : argument));
    }

    public static void sendVehicles(ServerPlayer player, List<VehicleEntry> owned,
                                    boolean hasActive, boolean combatLocked, long cooldownTicks,
                                    String blockKey, String activeEntity) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CVehicles(owned, hasActive, combatLocked, cooldownTicks, blockKey, activeEntity));
        }
    }

    /** Fired once when a player crosses a safe area boundary, never per tick. */
    public static void sendZoneNotice(ServerPlayer player, boolean entered, String zoneName) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CZoneNotice(entered, zoneName));
        }
    }

    /** Fired the moment a level is crossed, so the client can pop a card instead of a chat line. */
    public static void sendLevelUp(ServerPlayer player, int level) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CLevelUp(level));
        }
    }

    /** Sent when a player's points change, and once on login. */
    public static void sendLevelState(ServerPlayer player, int points, int level,
                                      int inLevel, int costOfNext,
                                      List<S2CLevelState.Tier> track,
                                      List<S2CLevelState.Rule> rules) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                    new S2CLevelState(points, level, inLevel, costOfNext, track, rules));
        }
    }

}
