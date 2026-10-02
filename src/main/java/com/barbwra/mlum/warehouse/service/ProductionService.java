package com.barbwra.mlum.warehouse.service;

import com.barbwra.mlum.warehouse.WarehouseMod;
import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.catalog.Catalog;
import com.barbwra.mlum.warehouse.catalog.Ingredient;
import com.barbwra.mlum.warehouse.catalog.Recipe;
import com.barbwra.mlum.warehouse.core.CraftJob;
import com.barbwra.mlum.warehouse.core.Crate;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import com.barbwra.mlum.warehouse.sched.DeadlineScheduler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Assembly lines: starting them, finishing them, and re-arming them after a restart.
 *
 * <p>The two rules that matter here both come straight out of the prototype's failures.</p>
 *
 * <p><b>1. Ingredients are matched by registry id.</b> See {@link Ingredient#matches}. Substring
 * matching on display names meant a renamed stack of anything satisfied any recipe.</p>
 *
 * <p><b>2. Consumption is one atomic pass.</b> Count everything, verify everything, and only then
 * deduct. The prototype counted in one loop and deducted in a second, and in three of its nine
 * recipes the deduction loop tested a stale variable from the counting loop - so depending on what
 * happened to be in the player's last inventory slot, the primary ingredient was either never
 * consumed (free crates, repeatable) or every item in the inventory was treated as that ingredient
 * and destroyed. Neither failure is reachable from a single verify-then-deduct.</p>
 */
public final class ProductionService {

    private ProductionService() {
    }

    /** How long to wait before retrying a completion that had nowhere to go. */
    private static final long RETRY_WHEN_FULL_MILLIS = 60_000L;

    public enum StartResult {
        OK,
        NO_WAREHOUSE,
        UNKNOWN_RECIPE,
        /** One of the recipe's items belongs to a mod that is not installed. */
        RECIPE_UNAVAILABLE,
        /** Every production line is already busy. */
        NO_FREE_LINE,
        /** Storage is full, so the crate would have nowhere to land. */
        STORAGE_FULL,
        MISSING_INGREDIENTS
    }

    /** One unsatisfied requirement, for the terminal's requisition list. */
    public record Missing(Ingredient ingredient, int held) {

        public int shortfall() {
            return Math.max(0, ingredient.count() - held);
        }
    }

    /* ---------------------------------------------------------------- checking */

    /** How many of {@code ingredient} the player is carrying. Registry identity only. */
    public static int held(Player player, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (ingredient.matches(stack)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (ingredient.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Every line of the recipe with its held count, satisfied or not - the requisition panel. */
    public static List<Missing> audit(Player player, Recipe recipe) {
        List<Missing> out = new ArrayList<>(recipe.ingredients().size());
        for (Ingredient ingredient : recipe.ingredients()) {
            out.add(new Missing(ingredient, held(player, ingredient)));
        }
        return out;
    }

    public static boolean hasAll(Player player, Recipe recipe) {
        for (Ingredient ingredient : recipe.ingredients()) {
            if (held(player, ingredient) < ingredient.count()) {
                return false;
            }
        }
        return true;
    }

    /* ---------------------------------------------------------------- starting */

    /**
     * Commits the materials and opens an assembly line.
     *
     * <p>Storage capacity is checked <i>now</i> as well as at completion. Letting a player spend
     * forty minutes of real time and a stack of components on a crate that cannot be stored when it
     * lands is a worse experience than refusing at the point of purchase.</p>
     */
    public static StartResult start(MinecraftServer server, ServerPlayer player, String recipeId) {
        if (!com.barbwra.mlum.warehouse.License.gate(player)) {
            return StartResult.NO_WAREHOUSE;
        }
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse == null) {
            return StartResult.NO_WAREHOUSE;
        }

        Recipe recipe = Catalog.get().recipe(recipeId);
        if (recipe == null) {
            return StartResult.UNKNOWN_RECIPE;
        }
        if (!recipe.isAvailable()) {
            return StartResult.RECIPE_UNAVAILABLE;
        }
        if (!warehouse.hasFreeLine()) {
            return StartResult.NO_FREE_LINE;
        }
        if (!warehouse.hasSpace()) {
            return StartResult.STORAGE_FULL;
        }
        if (!hasAll(player, recipe)) {
            return StartResult.MISSING_INGREDIENTS;
        }

        // Verified above; from here the deduction cannot half-succeed.
        for (Ingredient ingredient : recipe.ingredients()) {
            deduct(player, ingredient);
        }

        long now = System.currentTimeMillis();
        long duration = (long) recipe.durationSeconds() * 1000L;
        if (warehouse.isVip()) {
            duration = (long) (duration * WarehouseConfig.vipCraftFactor());
        }

        CraftJob job = new CraftJob(
                UUID.randomUUID(),
                recipe.id(),
                recipe.rollValue(player.getRandom()),
                now,
                now + duration);

        warehouse.addCraft(job);
        data.setDirty();
        arm(server, warehouse.owner(), job);
        return StartResult.OK;
    }

    private static void deduct(Player player, Ingredient ingredient) {
        int remaining = ingredient.count();
        remaining = drain(player.getInventory().items, ingredient, remaining);
        drain(player.getInventory().offhand, ingredient, remaining);
    }

    private static int drain(Iterable<ItemStack> slots, Ingredient ingredient, int remaining) {
        for (ItemStack stack : slots) {
            if (remaining <= 0) {
                return 0;
            }
            if (!ingredient.matches(stack)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            remaining -= taken;
        }
        return remaining;
    }

    /* -------------------------------------------------------------- completion */

    /** Puts a job's deadline on the scheduler. */
    private static void arm(MinecraftServer server, UUID owner, CraftJob job) {
        DeadlineScheduler.get().at(job.completesAt(), "craft:" + job.id(),
                () -> complete(server, owner, job.id()));
    }

    /**
     * Turns a finished job into a crate.
     *
     * <p>Re-reads the job from storage rather than trusting the captured copy: between the deadline
     * being armed and it firing, an admin may have cleared the warehouse or the player may have
     * cancelled the line, and firing on a stale snapshot would resurrect it.</p>
     */
    private static void complete(MinecraftServer server, UUID owner, UUID jobId) {
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(owner);
        if (warehouse == null) {
            return;
        }
        CraftJob job = warehouse.craft(jobId);
        if (job == null) {
            return;
        }

        Recipe recipe = Catalog.get().recipe(job.recipeId());
        if (recipe == null) {
            WarehouseMod.LOGGER.warn("[mwh] Craft {} references recipe '{}' which no longer exists - dropping it.",
                    jobId, job.recipeId());
            warehouse.removeCraft(jobId);
            data.setDirty();
            return;
        }

        long now = System.currentTimeMillis();

        // Nowhere to put it: hold the line open and try again shortly rather than destroy the crate.
        if (!warehouse.hasSpace()) {
            DeadlineScheduler.get().at(now + RETRY_WHEN_FULL_MILLIS, "craft-retry:" + jobId,
                    () -> complete(server, owner, jobId));
            notify(server, owner, Component.literal("§6[المستودع] §c"
                    + "المخزن ممتلئ - الشحنة "
                    + "في الانتظار."));
            return;
        }

        warehouse.removeCraft(jobId);
        Crate crate = StorageService.store(warehouse, recipe.cargo(), recipe.tier(), job.rolledValue(), now);
        data.setDirty();

        if (crate != null) {
            notify(server, owner, Component.literal("§6[المستودع] §a✔ §f"
                    + recipe.displayName() + " §7» §e" + crate.baseValue() + "$"));
        }
    }

    private static void notify(MinecraftServer server, UUID owner, Component message) {
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            player.sendSystemMessage(message);
        }
    }

    /* ------------------------------------------------------------------ reboot */

    /**
     * Re-arms every unfinished job after a restart.
     *
     * <p>Jobs whose deadline passed while the server was down fire on the next tick, so a player
     * who logs in after an outage finds their crates waiting rather than their timers rewound.
     * This is the whole payoff of storing an instant instead of a countdown.</p>
     */
    public static void rearmAll(MinecraftServer server) {
        WarehouseData data = WarehouseData.get(server);
        int armed = 0;
        for (Warehouse warehouse : data.warehouses()) {
            for (CraftJob job : new ArrayList<>(warehouse.crafts())) {
                arm(server, warehouse.owner(), job);
                armed++;
            }
        }
        WarehouseMod.LOGGER.info("[mwh] Re-armed {} assembly lines.", armed);
    }
}
