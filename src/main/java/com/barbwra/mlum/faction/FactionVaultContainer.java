package com.barbwra.mlum.faction;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * One page of a faction's vault, presented as a {@link Container} so a normal menu can hold it.
 *
 * <p><b>A window onto the sparse map, not a copy of it.</b> Every read and write goes straight
 * through to {@link FactionVault}; nothing is loaded into an array on open and flushed back on
 * close. Two members with the same page open therefore see one set of items rather than two
 * snapshots racing to overwrite each other - and a server that stops between those two moments
 * cannot lose a chest's worth of contents, because there was never an unflushed copy to lose.</p>
 *
 * <p><b>Every mutation marks the save data dirty.</b> {@code SavedData.setDirty} is a flag rather
 * than I/O, so doing it per changed slot costs nothing and removes the question of which code path
 * was supposed to remember.</p>
 */
public final class FactionVaultContainer implements Container {

    private final FactionData data;
    private final Faction faction;
    private final int page;
    private final int rows;
    /** Opened by an operator's command: membership is not required. */
    private final boolean admin;

    public FactionVaultContainer(FactionData data, Faction faction, int page, int rows, boolean admin) {
        this.data = data;
        this.faction = faction;
        this.page = page;
        this.rows = Math.max(0, rows);
        this.admin = admin;
    }

    public Faction faction() {
        return faction;
    }

    public int page() {
        return page;
    }

    @Override
    public int getContainerSize() {
        return rows * FactionLevel.COLUMNS;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= getContainerSize()) {
            return ItemStack.EMPTY;
        }
        return faction.vault().get(page, slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.split(count);
        // split mutates the stack in place, so the remainder has to be written back - including
        // the empty case, which is what removes the entry from the sparse map entirely
        faction.vault().set(page, slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        faction.vault().set(page, slot, ItemStack.EMPTY);
        setChanged();
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= getContainerSize()) {
            return;
        }
        faction.vault().set(page, slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        data.setDirty();
    }

    /**
     * Whether this player may still have the page open - asked by vanilla every tick and before
     * every click.
     *
     * <p>A vault has no block in the world to stand next to, so there is no distance to check - but
     * who may look is not settled once at opening. A member who is kicked, leaves, or loses the rank
     * for this page must lose the screen at that moment, or they could keep emptying it; and a
     * disbanded faction's page must close before anything is put into a vault that no longer exists.
     * The rule itself lives in {@link FactionVaultAccess#mayView}, the same one that opened it.</p>
     */
    @Override
    public boolean stillValid(Player player) {
        return data.byId(faction.id()) == faction
                && (admin || FactionVaultAccess.mayView(faction, page, player.getUUID()))
                // a rented page shuts the moment its rent runs out
                && (page <= 1 || faction.vault().rentActive(System.currentTimeMillis()));
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < getContainerSize(); i++) {
            faction.vault().set(page, i, ItemStack.EMPTY);
        }
        setChanged();
    }
}
