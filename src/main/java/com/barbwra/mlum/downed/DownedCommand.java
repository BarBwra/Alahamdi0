package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.util.ChargeTag;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mlum downed ...} - trying the downed system without a second player.
 *
 * <pre>
 *   /mlum downed dummy              a practice body two blocks in front of you
 *   /mlum downed self               go down yourself, to see it from the ground
 *   /mlum downed revive &lt;player&gt;    get someone up
 *   /mlum downed charge             fill the defibrillator in your hand
 *   /mlum downed list               who is down right now
 * </pre>
 */
public final class DownedCommand {

    private DownedCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("downed")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dummy").executes(DownedCommand::dummy))
                .then(Commands.literal("self").executes(DownedCommand::self))
                .then(Commands.literal("revive")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(DownedCommand::revive)))
                .then(Commands.literal("charge").executes(DownedCommand::charge))
                .then(Commands.literal("list").executes(DownedCommand::list));
    }

    private static int dummy(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        DownedDummy body = ModEntities.DOWNED_DUMMY.get().create(level);
        if (body == null) {
            return 0;
        }
        Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot()).scale(2.0D);
        body.moveTo(player.getX() + ahead.x, player.getY(), player.getZ() + ahead.z, player.getYRot() + 90.0F, 0.0F);
        body.prime();
        level.addFreshEntity(body);
        ctx.getSource().sendSuccess(() -> Component.literal("Practice body placed. It bleeds out in "
                + MlumConfig.downedSeconds() + "s."), false);
        return 1;
    }

    private static int self(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!DownedService.downOnPurpose(player)) {
            ctx.getSource().sendFailure(Component.literal("You are already down."));
            return 0;
        }
        return 1;
    }

    private static int revive(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (!DownedService.reviveByCommand(player)) {
            ctx.getSource().sendFailure(Component.literal(player.getGameProfile().getName() + " is not down."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Revived " + player.getGameProfile().getName()), true);
        return 1;
    }

    private static int charge(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("Hold the defibrillator item ("
                    + MlumConfig.defibItem() + ")."));
            return 0;
        }
        int cap = MlumConfig.defibCapacity();
        ChargeTag.set(held, ChargeTag.DEFIB, cap, cap);
        ctx.getSource().sendSuccess(() -> Component.literal("Charged to " + cap + "/" + cap), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var names = DownedService.downedNames(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.literal(names.isEmpty() ? "Nobody is down."
                : "Down: " + String.join(", ", names)), false);
        return names.size();
    }
}
