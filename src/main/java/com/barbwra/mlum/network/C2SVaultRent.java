package com.barbwra.mlum.network;

import com.barbwra.mlum.faction.FactionVaultAccess;
import com.barbwra.mlum.menu.MlumMenu;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * "Pay this rent plan for the vault I have open." The plan is an index into
 * {@code FactionLevel.RENT_DAYS}; the faction, as with page turns, comes from the open menu.
 * After paying, the page is reopened so the screen shows the new rent at once.
 */
public record C2SVaultRent(int plan) {

    public static void encode(C2SVaultRent msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.plan);
    }

    public static C2SVaultRent decode(FriendlyByteBuf buf) {
        return new C2SVaultRent(buf.readVarInt());
    }

    public static void handle(C2SVaultRent msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !(player.containerMenu instanceof MlumMenu menu) || !menu.isVault()) {
                return;
            }
            UUID faction = menu.vaultFaction();
            if (faction == null) {
                return;
            }
            String said = FactionVaultAccess.payRent(player, faction, msg.plan());
            boolean ok = said.startsWith("+");
            if (ok) {
                Feedback.ok(player, said.substring(1));
                boolean admin = menu.vaultAdmin();
                int page = menu.vaultPage();
                player.doCloseContainer();
                if (!FactionVaultAccess.open(player, faction, page, player, admin)) {
                    player.closeContainer();
                }
            } else {
                Feedback.bad(player, said);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
