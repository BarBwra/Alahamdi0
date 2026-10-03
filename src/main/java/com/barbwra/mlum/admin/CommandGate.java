package com.barbwra.mlum.admin;

import com.barbwra.mlum.MlumInventory;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Lets an admin rank run commands it normally could not.
 *
 * <h2>How</h2>
 * <p>Every command - vanilla's, this mod's, every other mod's - decides who may use it with one
 * test on each node of its tree ({@code requires(...)}). Once every mod has registered its
 * commands, this walks the whole tree and widens each test: the node's own test, <i>or</i> the
 * player's admin rank holds {@code cmd.} plus the node's path. {@code /tp} is {@code cmd.tp},
 * {@code /mlum downed} is {@code cmd.mlum.downed}, and holding a parent covers everything under it.
 * Nothing is ever narrowed, so no one loses a command they had.</p>
 *
 * <p>It runs again whenever the commands are rebuilt ({@code /reload}), and a player whose rank
 * changes is sent a fresh command list so the new commands tab-complete straight away.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class CommandGate {

    private CommandGate() {
    }

    private static final Field REQUIREMENT = find();

    private static Field find() {
        try {
            Field f = CommandNode.class.getDeclaredField("requirement");
            f.setAccessible(true);
            return f;
        } catch (Throwable broken) {
            MlumInventory.LOGGER.warn("[{}] cannot open command requirements - admin ranks will not unlock commands",
                    MlumInventory.MODID, broken);
            return null;
        }
    }

    /** A node's test, widened for admin ranks. */
    private record Gated(Predicate<CommandSourceStack> original, String path) implements Predicate<CommandSourceStack> {
        @Override
        public boolean test(CommandSourceStack source) {
            return original.test(source) || Staff.allowsCommand(source, path);
        }
    }

    /** Lowest, so every mod's commands are already in the tree. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterCommandsEvent event) {
        if (REQUIREMENT == null) {
            return;
        }
        Set<CommandNode<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        wrap(event.getDispatcher().getRoot(), "", seen);
    }

    @SuppressWarnings("unchecked")
    private static void wrap(CommandNode<CommandSourceStack> node, String path, Set<CommandNode<?>> seen) {
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            if (!seen.add(child)) {
                continue;
            }
            String p = path.isEmpty() ? child.getName() : path + "." + child.getName();
            Predicate<CommandSourceStack> original = child.getRequirement();
            if (!(original instanceof Gated)) {
                try {
                    REQUIREMENT.set(child, new Gated(original, p.toLowerCase(java.util.Locale.ROOT)));
                } catch (Throwable broken) {
                    return;
                }
            }
            wrap(child, p, seen);
        }
    }

    /** Every top-level command name, for the panel's permission picker. */
    public static java.util.List<String> roots(net.minecraft.server.MinecraftServer server) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (CommandNode<CommandSourceStack> child : server.getCommands().getDispatcher().getRoot().getChildren()) {
            out.add(child.getName());
        }
        out.sort(String::compareTo);
        return out;
    }
}
