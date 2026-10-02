package com.barbwra.mlum.warehouse.service;

import com.barbwra.mlum.warehouse.catalog.Catalog;
import com.barbwra.mlum.warehouse.catalog.UpgradeNode;
import com.barbwra.mlum.warehouse.core.UpgradePath;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.data.WarehouseData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The upgrade tree, gated on deliveries and paid for in currency.
 *
 * <p><b>Every gate is re-checked here, at purchase time, on the server.</b> That is the entire
 * point of this class. In the prototype the delivery thresholds were evaluated only by the code
 * that <i>drew</i> the upgrade icons; the click handler then asked whether the clicked item's lore
 * contained the words "ready to unlock" and, if so, wrote the level straight into storage. Item
 * lore is client-visible state attached to an object the player can influence, so the requirement
 * system was decorative - and because the GUI was identified by its inventory title, the handler
 * could be reached from a renamed container entirely outside the terminal.</p>
 *
 * <p>Here the client sends a path and a level. Nothing else it says is read.</p>
 */
public final class UpgradeService {

    private UpgradeService() {
    }

    public enum PurchaseResult {
        OK,
        NO_WAREHOUSE,
        /** No node defined at this path and level. */
        UNKNOWN_NODE,
        /** The level below this one is not owned yet. */
        OUT_OF_ORDER,
        /** Already owned. */
        ALREADY_OWNED,
        /** Delivery thresholds not reached. */
        REQUIREMENTS_NOT_MET,
        /** Could not pay. */
        INSUFFICIENT_FUNDS
    }

    /** One requirement line with the player's current standing against it. */
    public record Progress(UpgradeNode.Requirement requirement, int current) {

        public boolean isMet() {
            return current >= requirement.count();
        }

        public float fraction() {
            int need = requirement.count();
            return need <= 0 ? 1.0F : Math.min(1.0F, (float) current / need);
        }
    }

    /* ---------------------------------------------------------------- querying */

    public static List<Progress> progress(Warehouse warehouse, UpgradeNode node) {
        List<Progress> out = new ArrayList<>(node.requirements().size());
        for (UpgradeNode.Requirement requirement : node.requirements()) {
            out.add(new Progress(requirement,
                    warehouse.deliveredCount(requirement.cargo(), requirement.tiers())));
        }
        return out;
    }

    public static boolean requirementsMet(Warehouse warehouse, UpgradeNode node) {
        for (UpgradeNode.Requirement requirement : node.requirements()) {
            if (warehouse.deliveredCount(requirement.cargo(), requirement.tiers()) < requirement.count()) {
                return false;
            }
        }
        return true;
    }

    /** True when this node is the next one on its path and its deliveries are done. */
    public static boolean isPurchasable(Warehouse warehouse, UpgradeNode node) {
        return warehouse.upgradeLevel(node.path()) == node.level() - 1
                && requirementsMet(warehouse, node);
    }

    /* --------------------------------------------------------------- purchasing */

    /**
     * Buys one node. Ordered so that nothing is charged unless everything else already passed.
     */
    public static PurchaseResult purchase(MinecraftServer server, ServerPlayer player,
                                          UpgradePath path, int level) {
        WarehouseData data = WarehouseData.get(server);
        Warehouse warehouse = data.warehouse(player.getUUID());
        if (warehouse == null) {
            return PurchaseResult.NO_WAREHOUSE;
        }

        UpgradeNode node = Catalog.get().upgrade(path, level);
        if (node == null) {
            return PurchaseResult.UNKNOWN_NODE;
        }

        int owned = warehouse.upgradeLevel(path);
        if (owned >= level) {
            return PurchaseResult.ALREADY_OWNED;
        }
        if (owned != level - 1) {
            return PurchaseResult.OUT_OF_ORDER;
        }
        if (!requirementsMet(warehouse, node)) {
            return PurchaseResult.REQUIREMENTS_NOT_MET;
        }
        if (!EconomyService.take(player, node.cost())) {
            return PurchaseResult.INSUFFICIENT_FUNDS;
        }

        warehouse.setUpgradeLevel(path, level);
        data.setDirty();
        return PurchaseResult.OK;
    }
}
