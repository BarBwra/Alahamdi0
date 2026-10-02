package com.barbwra.mlum.network;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.quest.QuestBoard;
import com.barbwra.mlum.quest.QuestEntry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * "I pressed claim on quest line N."
 *
 * <p>The mod hands out nothing. It checks the line exists, is actually complete and has not been
 * claimed, marks it claimed, and then runs one configurable command from the server console with
 * the player name and line substituted in. <b>Skript listens for that command and decides what the
 * reward really is</b> - which keeps the same contract as the rest of the quest system: the mod
 * owns pixels, Skript owns logic.</p>
 *
 * <p>The command runs with console authority rather than the player's, because the whole point is
 * to let it do things the player could not do themselves. It is a fixed string from the server
 * config with only {@code %player%} and {@code %line%} substituted, so a client can never inject
 * anything into it - the only thing the packet controls is <i>which</i> line number, and that is
 * validated against the player's own board first.</p>
 */
public record C2SClaimReward(int line) {

    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(line);
    }

    public static C2SClaimReward decode(FriendlyByteBuf buf) {
        return new C2SClaimReward(buf.readVarInt());
    }

    public static void handle(C2SClaimReward msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        QuestEntry entry = null;
        for (QuestEntry candidate : QuestBoard.get(player)) {
            if (candidate.line() == msg.line()) {
                entry = candidate;
                break;
            }
        }
        if (entry == null || !entry.isComplete() || QuestBoard.isClaimed(player, entry.line())) {
            return;
        }

        QuestBoard.claim(player, entry.line());
        com.barbwra.mlum.util.Feedback.ok(player, "استلمت مكافآت {b}" + entry.title() + "{/b}");

        /*
         * Hand over the items the card actually showed.
         *
         * This used to run the claim command and nothing else, on the principle that the mod awards
         * nothing and Skript owns every reward. That is a defensible contract, but it made the
         * board lie: the panel draws the reward items, the player presses "استلام", and unless a
         * Skript command named in claimCommand happened to exist and happened to give exactly those
         * items, they got nothing. Showing an item and then not giving it is worse than not showing
         * it. The command hook still runs afterwards, so anything built on it keeps working.
         */
        if (MlumConfig.grantQuestItems()) {
            for (ItemStack reward : entry.rewards()) {
                if (reward.isEmpty()) {
                    continue;
                }
                ItemStack copy = reward.copy();
                // Anything that will not fit goes on the floor at the player's feet rather than
                // being silently destroyed - a full bag must never cost someone their reward.
                if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                    player.drop(copy, false);
                }
            }
        }

        String template = MlumConfig.claimCommand();
        if (template.isEmpty()) {
            return;
        }
        // An offline-mode server will accept names vanilla's online rules never allow, and this
        // string is pasted into a console-authority command. Anything but a normal name is refused
        // rather than substituted - the UUID is always safe, so it is what the fallback uses.
        String name = player.getGameProfile().getName();
        if (!SAFE_NAME.matcher(name).matches()) {
            name = player.getUUID().toString();
        }
        String command = template
                .replace("%player%", name)
                .replace("%line%", String.valueOf(entry.line()));

        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(console, command);
    }
}
