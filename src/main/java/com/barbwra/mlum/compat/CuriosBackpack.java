package com.barbwra.mlum.compat;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Optional;

/**
 * The worn backpack, kept in the Curios {@code back} slot.
 *
 * <h2>The slot is the storage, not a mirror of it</h2>
 * <p>The obvious version of this feature copies the pack into Curios so its renderer draws it. That
 * is a <b>duplication bug</b>: the same pack exists in this mod's data <i>and</i> in a real slot the
 * player can reach into, and they walk away with two.</p>
 *
 * <p>So there is no copy. The bag's backpack socket reads and writes the Curios slot directly - one
 * stack, in one place. Curios draws it on the player's back because it is genuinely wearing it, and
 * the bag shows it because it is genuinely in the socket. Neither can get out of step with the
 * other, because there is nothing to keep in step.</p>
 *
 * <h2>The slot is {@code back}</h2>
 * <p>Not {@code backpack}. Curios ships {@code back} itself, Superb Warfare registers it too, and
 * both it and {@code survivorsarsenal} tag their packs into {@code data/curios/tags/items/back.json}
 * - so it is the slot the backpacks on this server already go in.</p>
 *
 * <h2>Nothing here throws</h2>
 * <p>A pack assembled without Curios has to keep working. Every method answers "not available" and
 * {@code BackpackAccess} falls back to its own storage, which is what the mod did before.</p>
 */
public final class CuriosBackpack {

    private CuriosBackpack() {
    }

    /** Curios' own id for the slot a backpack goes on. */
    public static final String SLOT = "back";

    private static Boolean present;

    /** Whether Curios is installed. Checked once - a pack cannot gain a mod mid-run. */
    public static boolean available() {
        if (present == null) {
            boolean loaded;
            try {
                loaded = ModList.get() != null && ModList.get().isLoaded("curios");
            } catch (Throwable notReady) {
                return false;   // asked before the mod list exists; do not cache that
            }
            present = loaded;
            MlumInventory.LOGGER.info("[{}] Curios {} - the backpack socket is {}",
                    MlumInventory.MODID, loaded ? "found" : "not present",
                    loaded ? "the Curios '" + SLOT + "' slot" : "this mod's own storage");
        }
        return present;
    }

    private static Optional<IDynamicStackHandler> handler(Player player) {
        if (player == null || !available()) {
            return Optional.empty();
        }
        try {
            return CuriosApi.getCuriosInventory(player)
                    .resolve()
                    .flatMap(inv -> inv.getStacksHandler(SLOT))
                    .map(ICurioStacksHandler::getStacks)
                    .filter(stacks -> stacks.getSlots() > 0);
        } catch (Throwable broken) {
            return Optional.empty();
        }
    }

    /** True when this player actually has the slot - another mod may have removed it. */
    public static boolean hasSlot(Player player) {
        return handler(player).isPresent();
    }

    /**
     * What is in the slot.
     *
     * <p>A <b>copy</b>, matching {@code BackpackAccess.worn}'s contract. The handler hands out its
     * live stack, and callers of this are expected to be able to edit what they get without it
     * taking effect until they write it back.</p>
     */
    public static ItemStack worn(Player player) {
        return handler(player).map(h -> h.getStackInSlot(0).copy()).orElse(ItemStack.EMPTY);
    }

    public static void setWorn(Player player, ItemStack stack) {
        handler(player).ifPresent(h ->
                h.setStackInSlot(0, stack == null ? ItemStack.EMPTY : stack.copy()));
    }

    /**
     * The stack's own tag, for the change detector in {@code MlumMenu.broadcastChanges}.
     *
     * <p>That detector compares tag <i>identity</i> to notice the pack being swapped behind the
     * bag's back without deserialising it every tick. Reading through to the live stack keeps that
     * trick working.</p>
     */
    @javax.annotation.Nullable
    public static net.minecraft.nbt.Tag wornTag(Player player) {
        return handler(player).map(h -> (net.minecraft.nbt.Tag) h.getStackInSlot(0).getTag()).orElse(null);
    }
}
