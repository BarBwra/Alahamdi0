package com.barbwra.mlum.network;

import com.barbwra.mlum.faction.FactionVaultAccess;
import com.barbwra.mlum.menu.MlumMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Show me another page of the vault I already have open."
 *
 * <p><b>It carries a page number and nothing else.</b> Which faction's vault is being turned comes
 * from the menu the player currently has open on the server, never from the packet - so a modified
 * client can ask for page 3 of whatever it is already looking at, and cannot name a faction it was
 * never given. Any player with no vault open is ignored entirely.</p>
 *
 * <p>Turning a page reopens the menu, because pages can differ in height and a live menu's slot
 * count is fixed. That reopen runs the full access check again rather than reusing the decision
 * that opened the first page.</p>
 */
public record C2SVaultPage(int page) {

    public static void encode(C2SVaultPage msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.page);
    }

    public static C2SVaultPage decode(FriendlyByteBuf buf) {
        return new C2SVaultPage(buf.readVarInt());
    }

    public static void handle(C2SVaultPage msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.containerMenu instanceof MlumMenu menu) || !menu.isVault()) {
                return;
            }
            UUID faction = menu.vaultFaction();
            if (faction == null || msg.page() == menu.vaultPage()) {
                return;
            }
            // Every check first, while the current page is still open: a refused turn leaves the
            // player where they were instead of on a menu the server has already closed.
            if (!FactionVaultAccess.canOpen(player, faction, msg.page(), player, menu.vaultAdmin())) {
                return;
            }
            // Closed quietly rather than with a close packet, so the client swaps one screen for
            // the next instead of dropping to the world for a frame - which is what used to throw
            // the cursor back to the middle of the window on every page turn.
            boolean admin = menu.vaultAdmin();
            player.doCloseContainer();
            if (!FactionVaultAccess.open(player, faction, msg.page(), player, admin)) {
                player.closeContainer();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
