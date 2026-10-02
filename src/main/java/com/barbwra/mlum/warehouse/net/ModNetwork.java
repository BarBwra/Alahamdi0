package com.barbwra.mlum.warehouse.net;

import com.barbwra.mlum.warehouse.WarehouseMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * One channel, four packets.
 *
 * <p>Nothing in this mod rides on a vanilla container, so unlike {@code mlum} there is no slot sync
 * to lean on - every byte the client sees arrives through here. That is deliberate: a chest GUI is
 * identified by its title, and identifying a UI by a string the player can influence is what made
 * the Skript version's sell and upgrade screens forgeable from a renamed shulker box.</p>
 *
 * <p>The traffic:</p>
 * <ul>
 *   <li><b>{@link S2COpenTerminal}</b> - the whole terminal state, on open and after every accepted
 *       action. Already resolved into display-ready numbers.</li>
 *   <li><b>{@link S2CCloseTerminal}</b> - server-initiated close.</li>
 *   <li><b>{@link S2CMissionHud}</b> - once a second to a runner, carrying absolute deadlines the
 *       client interpolates between.</li>
 *   <li><b>{@link C2STerminalAction}</b> - a typed intent and nothing else.</li>
 * </ul>
 *
 * <p>The protocol string is compared on both ends, so a client running an older build is rejected
 * at handshake instead of silently misreading a packet.</p>
 */
public final class ModNetwork {

    private ModNetwork() {
    }

    private static final String PROTOCOL = "1";

    // "warehouse", not "main" - MlumInventory's own channel already owns mlum:main, and two
    // SimpleChannels registered under the same ResourceLocation would collide at handshake.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            WarehouseMod.id("warehouse"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int nextId = 0;

    /**
     * Registers every packet. Called once from common setup.
     *
     * <p>Ids are assigned by declaration order, so packets are only ever appended - reordering this
     * method silently repoints every id and a mismatched client would decode the wrong payload
     * rather than failing the handshake.</p>
     */
    public static void register() {
        CHANNEL.messageBuilder(S2COpenTerminal.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2COpenTerminal::encode)
                .decoder(S2COpenTerminal::decode)
                .consumerMainThread(S2COpenTerminal::handle)
                .add();

        CHANNEL.messageBuilder(S2CCloseTerminal.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CCloseTerminal::encode)
                .decoder(S2CCloseTerminal::decode)
                .consumerMainThread(S2CCloseTerminal::handle)
                .add();

        CHANNEL.messageBuilder(S2CMissionHud.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CMissionHud::encode)
                .decoder(S2CMissionHud::decode)
                .consumerMainThread(S2CMissionHud::handle)
                .add();

        CHANNEL.messageBuilder(C2STerminalAction.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2STerminalAction::encode)
                .decoder(C2STerminalAction::decode)
                .consumerMainThread(C2STerminalAction::handle)
                .add();
    }

    /* ------------------------------------------------------------------ senders */

    private static boolean canSend(ServerPlayer player) {
        return player != null && player.connection != null;
    }

    public static void sendTerminal(ServerPlayer player, TerminalSnapshot snapshot) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2COpenTerminal(snapshot));
        }
    }

    public static void sendCloseTerminal(ServerPlayer player) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CCloseTerminal());
        }
    }

    public static void sendMissionHud(ServerPlayer player, S2CMissionHud hud) {
        if (canSend(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), hud);
        }
    }

    /** Client to server. The only outbound call the client ever makes. */
    public static void sendAction(TerminalAction action, String text, int a, int b) {
        CHANNEL.sendToServer(new C2STerminalAction(action.ordinal(), text, a, b));
    }
}
