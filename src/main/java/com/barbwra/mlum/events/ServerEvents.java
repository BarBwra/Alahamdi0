package com.barbwra.mlum.events;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.menu.MlumMenu;
import com.barbwra.mlum.menu.MlumMenuProvider;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.quest.PlayerQuestCommand;
import com.barbwra.mlum.quest.QuestBoard;
import com.barbwra.mlum.util.LegacyMigration;
import com.barbwra.mlum.vehicle.CombatTracker;
import com.barbwra.mlum.vehicle.VehicleGarage;
import com.barbwra.mlum.vehicle.VehicleMenuCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * Server side hooks: command registration, quest board sync, the chest/barrel/ender chest
 * takeover, and hotbar slot enforcement.
 *
 * <p>The takeover deliberately only recognises the three vanilla block types the spec asks for.
 * Anything else - shulkers, hoppers, modded machines - keeps its own screen, so no other mod's
 * container is ever hijacked.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class ServerEvents {

    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // every subsystem now hangs off one /mlum root - see MlumCommands
        com.barbwra.mlum.MlumCommands.register(event.getDispatcher());
    }

    /* ------------------------------------------------------------------- syncing */

    /** Everything the client caches, pushed together - it is a few small packets, once. */
    private static void syncAll(ServerPlayer player) {
        QuestBoard.sync(player);
        VehicleGarage.sync(player);
        // the bag's rules first, then the bag drawn with them - and the skills tab's list
        ModNetwork.sendBagConfig(player);
        com.barbwra.mlum.bag.BagService.sync(player);
        // the balance is an account on the server now, not a count of items the client can see
        com.barbwra.mlum.bag.WalletService.sync(player);
        com.barbwra.mlum.skill.SkillService.sync(player);
        // the ladder and this player's place on it, for the pill in the top bar
        com.barbwra.mlum.rank.RankService.sync(player);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // carries quest and garage data across the rename before anything reads it
            LegacyMigration.run(player);
            // and a pack left in this mod's old storage into the Curios slot that replaced it
            if (com.barbwra.mlum.bag.BackpackAccess.migrateToCurios(player)) {
                MlumInventory.LOGGER.info("[{}] moved {}'s backpack into the Curios back slot",
                        MlumInventory.MODID, player.getGameProfile().getName());
            }
            syncAll(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAll(player);
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAll(player);
        }
    }


    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // a menu claim outlives its claimant otherwise, and the map would only ever grow
            com.barbwra.mlum.zone.MenuGuard.forget(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CombatTracker.reset();
    }

    /* ----------------------------------------------------------------- PvP lock */

    /**
     * The automatic half of the vehicle lock. Both sides of a player-on-player hit get tagged, so
     * neither the attacker nor the victim can drive away from a fight they are in.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (MlumConfig.combatLockSeconds() <= 0) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        CombatTracker.tag(victim);
        CombatTracker.tag(attacker);
    }


    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();

        // Only the server decides. Forge still forwards the interaction packet when the client
        // side event is left alone, so cancelling here is enough.
        if (level.isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND || player.isSpectator()) {
            return;
        }

        // mirror vanilla: sneaking with something in hand means "place the block", not "open"
        boolean holdingSomething = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        if (player.isSecondaryUseActive() && holdingSomething) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        Container container = null;
        Component title = null;
        Kind kind = null;

        if (block instanceof ChestBlock chestBlock) {
            if (!MlumConfig.takeoverChests()) {
                return;
            }
            // null when the chest is blocked by a solid block or a cat is sitting on it
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider == null) {
                return;
            }
            container = ChestBlock.getContainer(chestBlock, state, level, pos, false);
            title = provider.getDisplayName();
            kind = Kind.CHEST;
        } else if (block instanceof BarrelBlock) {
            if (!MlumConfig.takeoverBarrels()) {
                return;
            }
            if (level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel) {
                container = barrel;
                title = barrel.getDisplayName();
                kind = Kind.BARREL;
            }
        } else if (block instanceof EnderChestBlock) {
            if (!MlumConfig.takeoverEnderChest()) {
                return;
            }
            if (level.getBlockEntity(pos) instanceof EnderChestBlockEntity enderChest) {
                PlayerEnderChestContainer inventory = player.getEnderChestInventory();
                inventory.setActiveChest(enderChest);
                container = inventory;
                title = Component.translatable("container.enderchest");
                kind = Kind.ENDER;
            }
        } else {
            return;
        }

        if (container == null || title == null) {
            return;
        }

        int rows = container.getContainerSize() / 9;
        if (rows != 3 && rows != 6) {
            return;   // not a shape this layout can draw - leave it to vanilla
        }

        openMlumScreen(player, container, title, rows);
        awardAndAnger(player, kind);

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Opens the tactical menu with a container in the left column. */
    public static void openMlumScreen(ServerPlayer player, Container container, Component title, int rows) {
        MlumMenuProvider provider = new MlumMenuProvider(title, container, rows);
        NetworkHooks.openScreen(player, provider, buf -> buf.writeByte(provider.getRows()));
    }

    private static void awardAndAnger(ServerPlayer player, @Nullable Kind kind) {
        if (kind == null) {
            return;
        }
        switch (kind) {
            case CHEST -> {
                player.awardStat(Stats.OPEN_CHEST);
                PiglinAi.angerNearbyPiglins(player, true);
            }
            case BARREL -> {
                player.awardStat(Stats.OPEN_BARREL);
                PiglinAi.angerNearbyPiglins(player, true);
            }
            case ENDER -> {
                player.awardStat(Stats.OPEN_ENDERCHEST);
                PiglinAi.angerNearbyPiglins(player, true);
            }
        }
    }

    /* --------------------------------------------------- hotbar slot enforcement */

    /**
     * The hotbar rules run <b>both ways</b>, and they have to be enforced here as well as in the
     * menu: hotbar 0-8 are genuine vanilla slots (that is what makes keys 1-9 and the scroll wheel
     * work), so vanilla will happily drop a picked-up item into any of them without ever consulting
     * {@code Slot#mayPlace}.
     *
     * <ul>
     *   <li>Slots 0-1 are weapon cells - anything that is not a gun gets pushed out.</li>
     *   <li>Slots 2-8 are quick access - any gun gets pulled out, into a free weapon cell if there
     *       is one, otherwise into the main inventory.</li>
     * </ul>
     *
     * Runs every other tick; it is nine cheap stack checks, and anything that sneaks in is gone
     * again before the player notices.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !MlumConfig.enforceGunSlots()) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.tickCount % 2 != 0) {
            return;
        }
        Inventory inventory = player.getInventory();

        // weapon cells: evict anything that is not a gun
        for (int slot = 0; slot < MlumMenu.WEAPON_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && !TaczCompat.isGun(stack)) {
                evict(inventory, slot, true);
            }
        }

        // quick access: evict any gun
        int quickEnd = MlumMenu.WEAPON_SLOTS + MlumMenu.QUICK_SLOTS;
        for (int slot = MlumMenu.WEAPON_SLOTS; slot < quickEnd; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && TaczCompat.isGun(stack)) {
                evict(inventory, slot, false);
            }
        }
    }

    /**
     * Moves a misplaced stack out of {@code slot} <b>without ever destroying it</b>.
     *
     * <p>Three attempts, in order: put it somewhere legal; if the inventory is full, swap it with a
     * stack from the main inventory that does belong in this slot; and if there is nothing to swap
     * with, put it back. The last case leaves the rule broken for a moment, which is the right
     * trade - dropping a player's gun on the floor because their bag was full would be a far worse
     * bug than a gun sitting in the wrong slot until a space appears.</p>
     */
    private static void evict(Inventory inventory, int slot, boolean slotWantsGuns) {
        ItemStack moved = inventory.getItem(slot).copy();
        inventory.setItem(slot, ItemStack.EMPTY);

        boolean placed = slotWantsGuns
                ? placeOutsideWeaponSlots(inventory, moved)
                : placeGun(inventory, moved);
        if (placed) {
            return;
        }

        int mainStart = MlumMenu.WEAPON_SLOTS + MlumMenu.QUICK_SLOTS;
        for (int i = mainStart; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack candidate = inventory.getItem(i);
            if (candidate.isEmpty() || TaczCompat.isGun(candidate) != slotWantsGuns) {
                continue;
            }
            inventory.setItem(slot, candidate.copy());
            inventory.setItem(i, moved.copy());
            inventory.setChanged();
            return;
        }

        inventory.setItem(slot, moved);
        inventory.setChanged();
    }

    /** A displaced gun prefers a free weapon cell, then the main inventory - never quick access. */
    private static boolean placeGun(Inventory inventory, ItemStack stack) {
        for (int i = 0; i < MlumMenu.WEAPON_SLOTS && !stack.isEmpty(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        int mainStart = MlumMenu.WEAPON_SLOTS + MlumMenu.QUICK_SLOTS;
        for (int i = mainStart; i < Inventory.INVENTORY_SIZE && !stack.isEmpty(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        inventory.setChanged();
        return stack.isEmpty();
    }

    /** {@code Inventory#add} would happily drop it straight back into slot 0, so place by hand. */
    private static boolean placeOutsideWeaponSlots(Inventory inventory, ItemStack stack) {
        int limit = Inventory.INVENTORY_SIZE;
        for (int i = MlumMenu.WEAPON_SLOTS; i < limit && !stack.isEmpty(); i++) {
            ItemStack current = inventory.getItem(i);
            if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, stack)) {
                int space = Math.min(current.getMaxStackSize(), inventory.getMaxStackSize()) - current.getCount();
                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    current.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = MlumMenu.WEAPON_SLOTS; i < limit && !stack.isEmpty(); i++) {
            if (inventory.getItem(i).isEmpty()) {
                inventory.setItem(i, stack.copy());
                stack.setCount(0);
            }
        }
        inventory.setChanged();
        return stack.isEmpty();
    }

    private enum Kind {
        CHEST, BARREL, ENDER
    }
}
