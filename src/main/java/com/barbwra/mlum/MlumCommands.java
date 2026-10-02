package com.barbwra.mlum;

import com.barbwra.mlum.level.LevelCommand;
import com.barbwra.mlum.quest.PlayerQuestCommand;
import com.barbwra.mlum.vehicle.MountCommand;
import com.barbwra.mlum.vehicle.VehicleMenuCommand;
import com.barbwra.mlum.warehouse.command.WarehouseCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Every command this mod owns, under one namespace.
 *
 * <pre>
 * /mlum quest ...        (was /PlayerQuest)
 * /mlum vehicle ...      (was /VehicleMenu)
 * /mlum mount ...        (was /mount)
 * /mlum level ...        (new - levels, VIP and the 2x boosts)
 * /mlum warehouse ...    (was /mwh)
 * </pre>
 *
 * <p><b>Two spellings, on purpose.</b> Every subcommand is reachable both as {@code /mlum quest}
 * and as {@code /mlum:quest}. The spaced form is what Minecraft's own grammar expects and what tab
 * completion groups properly; the colon form is what anyone coming from Bukkit reaches for, and it
 * is a single literal so Skript can paste it without worrying about how brigadier splits
 * arguments. Both resolve to the same node, so there is one implementation behind them.</p>
 *
 * <p>Colon literals work because brigadier reads a literal up to the next space - the colon is just
 * another character in the word. It is not Minecraft namespacing, which does not exist for
 * commands; it only looks like it.</p>
 *
 * <p>The old top-level names are gone. Leaving {@code /mount} registered would keep colliding with
 * every other mod that wants that word, which is the reason for doing this at all.</p>
 */
public final class MlumCommands {

    private MlumCommands() {
    }

    /** Subcommand name to its builder. One entry per system the mod exposes. */
    private record Sub(String name, LiteralArgumentBuilder<CommandSourceStack> node) {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(MlumInventory.MODID);

        Sub[] subs = {
                new Sub("quest", PlayerQuestCommand.build()),
                new Sub("vehicle", VehicleMenuCommand.build()),
                new Sub("mount", MountCommand.build()),
                new Sub("level", LevelCommand.build()),
                new Sub("faction", com.barbwra.mlum.faction.FactionCommand.build()),
                new Sub("warehouse", WarehouseCommand.build()),
        };

        for (Sub sub : subs) {
            root.then(sub.node());
        }
        LiteralCommandNode<CommandSourceStack> rootNode = dispatcher.register(root);

        // Top level rather than under /mlum: this one is typed by operators and pasted into command
        // blocks, and it is the only way a faction vault opens at all.
        dispatcher.register(com.barbwra.mlum.faction.OpenVaultCommand.build());

        // /mlum_inventory vip|reload. Its own literal because the brief names it that way, and
        // because both subcommands are console work rather than anything a script drives.
        dispatcher.register(com.barbwra.mlum.bag.BagCommand.build());

        /*
         * The colon aliases. Each is registered as its own top-level literal that redirects into
         * the matching child of /mlum, so "/mlum:quest for Steve main 1 ..." and
         * "/mlum quest for Steve main 1 ..." parse into exactly the same node with the same
         * arguments. Redirecting rather than rebuilding is what keeps them from drifting apart.
         */
        for (Sub sub : subs) {
            var child = rootNode.getChild(sub.name());
            if (child == null) {
                continue;
            }
            dispatcher.register(Commands.literal(MlumInventory.MODID + ":" + sub.name())
                    .requires(child.getRequirement())
                    .redirect(child));
        }
    }
}
