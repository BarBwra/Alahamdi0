package com.barbwra.mlum.bag;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The server-side rules that keep a worn backpack behaving like a worn backpack.
 *
 * <p>Three jobs, all of them consequences of the contents living inside the item:</p>
 * <ul>
 *   <li>Stop the backpack mod's own right-click GUI, so there is exactly one way into a pack.</li>
 *   <li>Drop the worn pack on death, with its contents inside it.</li>
 *   <li>Re-check legality on login, in case the config or the player's VIP changed while away.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class BackpackEvents {

    private BackpackEvents() {
    }

    /**
     * Cancels the backpack mod's right-click-to-open.
     *
     * <p><b>{@code HIGHEST} priority and cancel.</b> The other mod opens its GUI from this same
     * event, so the only way to win is to be called first and stop it propagating. The item is still
     * perfectly usable in every other way - this refuses the <i>open</i>, not the item.</p>
     *
     * <p>Matched by id from config rather than by the mod's own class, because that mod is not on
     * the compile path. The consequence is that this only fires for ids an admin has listed, which is
     * the same set the bag is willing to wear - the two can never disagree.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!BagConfig.disableBackpackRightClick()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !BagConfig.backpackIds().contains(id)) {
            return;
        }
        event.setCanceled(true);
        // DENY rather than the default, so nothing downstream treats this as an unhandled use and
        // swings the arm - the player pressed a button and nothing should appear to happen.
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
    }

    /**
     * Drops the worn pack when the player dies.
     *
     * <p>Only runs when the drops are actually happening - with {@code keepInventory} on, Forge does
     * not fire this, and the pack simply stays in the player's persistent data, which is exactly the
     * behaviour the brief asks for on that gamerule.</p>
     *
     * <p>The contents need no separate handling. They are NBT on the stack being dropped, so one
     * item entity carries the whole pack.</p>
     */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack pack = BackpackAccess.worn(player);
        if (pack.isEmpty()) {
            return;
        }
        BackpackAccess.setWorn(player, ItemStack.EMPTY);
        event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                player.level(), player.getX(), player.getY(), player.getZ(), pack));
        MlumInventory.LOGGER.debug("[{}] dropped worn backpack for {} on death",
                MlumInventory.MODID, player.getGameProfile().getName());
    }

    /**
     * Re-checks the worn pack on login.
     *
     * <p>Covers the case the brief calls out directly - a player who logs in wearing a pack that has
     * since been removed from the config, or who lost VIP while offline.</p>
     */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BackpackEnforcer.enforce(player);
        }
    }
}
