package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.bag.BackpackStore;
import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.BagGrid;
import com.barbwra.mlum.bag.BagSection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A player who dies from the ground leaves their things in a backpack where they lay.
 *
 * <h2>Which backpack</h2>
 * <p>The smallest of the configured backpacks (the {@code [backpacks]} list in
 * {@code mlum_inventory.toml}) that takes everything - a few things make a leather pack, a full
 * inventory a military one. VIP-only packs are never used. When even the biggest is not enough the
 * rest goes into a second bag, and so on, so nothing is ever lost or left loose.</p>
 *
 * <h2>The bag they were wearing</h2>
 * <p>Its contents are tipped out and packed with everything else, and the empty pack goes in as one
 * more item, so the death bag is one flat thing to go through - never a bag inside a bag inside a
 * bag. The dropped bags last ten minutes on the ground, twice an ordinary drop.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DeathBag {

    private DeathBag() {
    }

    /** Players whose drops, about to happen, should be bagged. */
    private static final Set<UUID> EXPECTED = new HashSet<>();

    static void expect(ServerPlayer player) {
        EXPECTED.add(player.getUUID());
    }

    /** Lowest, so the worn backpack and every other mod's additions are already in the list. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !EXPECTED.remove(player.getUUID())
                || event.isCanceled()) {
            return;
        }
        List<Item> bags = bagTypes();
        if (bags.isEmpty() || event.getDrops().isEmpty()) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) {
                continue;
            }
            if (BagConfig.isBackpack(stack) && BackpackStore.hasContents(stack)) {
                loot.addAll(BackpackStore.contents(stack));
                BackpackStore.clear(stack);
            }
            loot.add(stack);
        }
        if (loot.isEmpty()) {
            return;
        }
        List<ItemEntity> out = new ArrayList<>();
        List<ItemStack> left = loot;
        int guard = 0;
        while (!left.isEmpty() && guard++ < 64) {
            Item type = smallestFitting(bags, left);
            if (type == null) {
                type = bags.get(bags.size() - 1);
            }
            ItemStack bag = new ItemStack(type);
            BagGrid grid = new BagGrid(BagSection.PACK, BagConfig.addedRows(bag));
            List<ItemStack> rest = pack(grid, left);
            if (rest.size() == left.size()) {
                break;   // nothing went in at all - an item too big for any bag; drop those loose
            }
            BackpackStore.write(bag, grid);
            out.add(entity(player, bag));
            left = rest;
        }
        for (ItemStack stack : left) {
            out.add(entity(player, stack));
        }
        event.getDrops().clear();
        event.getDrops().addAll(out);
    }

    /** The configured, non-VIP backpacks, smallest first. */
    private static List<Item> bagTypes() {
        List<Item> out = new ArrayList<>();
        for (ResourceLocation id : BagConfig.backpackIds()) {
            Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item == null || item == Items.AIR) {
                continue;
            }
            ItemStack probe = new ItemStack(item);
            if (BagConfig.isBackpack(probe) && !BagConfig.isVipOnly(probe)) {
                out.add(item);
            }
        }
        out.sort(Comparator.comparingInt(item -> BagConfig.addedCells(new ItemStack(item))));
        return out;
    }

    private static Item smallestFitting(List<Item> bags, List<ItemStack> loot) {
        for (Item type : bags) {
            BagGrid trial = new BagGrid(BagSection.PACK, BagConfig.addedRows(new ItemStack(type)));
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : loot) {
                copies.add(stack.copy());
            }
            if (pack(trial, copies).isEmpty()) {
                return type;
            }
        }
        return null;
    }

    /** Puts what fits into the grid - merging stacks first, then the first free spot. */
    private static List<ItemStack> pack(BagGrid grid, List<ItemStack> loot) {
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : loot) {
            ItemStack remaining = grid.merge(stack);
            if (!remaining.isEmpty() && !grid.add(remaining)) {
                rest.add(remaining);
            }
        }
        return rest;
    }

    private static ItemEntity entity(ServerPlayer player, ItemStack stack) {
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.3D, player.getZ(), stack);
        entity.setDeltaMovement(0.0D, 0.15D, 0.0D);
        entity.setDefaultPickUpDelay();
        entity.setExtendedLifetime();
        return entity;
    }
}
