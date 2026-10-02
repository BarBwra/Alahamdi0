package com.barbwra.mlum.menu;

import com.barbwra.mlum.compat.TaczAttachments;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The attachment slots, as a window onto the two guns rather than as storage of their own.
 *
 * <p><b>This owns nothing.</b> Every read goes straight to {@code IGun.getAttachment} on the gun
 * sitting in the firearm slot, and every write goes straight back through {@code installAttachment}
 * or {@code unloadAttachment}. There is no backing array and nothing to keep in step.</p>
 *
 * <p>That is deliberate, and it is the difference between this working and this being a source of
 * duplication bugs. If the six slots held their own copies, then taking the gun out of firearm slot
 * 1 would leave a scope behind with no gun to belong to - it would have to be spilled somewhere,
 * dropped, or silently destroyed, and every one of those is a way to lose a player's attachment.
 * Reading through to the gun means attachments follow it automatically: move it, drop it, store it
 * in a chest, and the scope is still on it because it was never anywhere else.</p>
 *
 * <p>Twelve slots: six types for each of the two firearms, laid out gun-major so slot {@code i}
 * belongs to gun {@code i / 6} and type {@code SLOTS[i % 6]}.</p>
 */
public class AttachmentContainer implements Container {

    /** Six types per gun, two guns. */
    public static final int PER_GUN = TaczAttachments.COUNT;
    public static final int SIZE = PER_GUN * MlumMenu.WEAPON_SLOTS;

    private final Inventory inventory;

    public AttachmentContainer(Inventory inventory) {
        this.inventory = inventory;
    }

    /** Which firearm a given attachment slot belongs to. */
    public static int gunOf(int index) {
        return index / PER_GUN;
    }

    public static AttachmentType typeOf(int index) {
        return TaczAttachments.SLOTS[index % PER_GUN];
    }

    /** The gun that slot is fitted to. Empty when that firearm cell is empty. */
    public ItemStack gunStack(int index) {
        int gun = gunOf(index);
        return gun < 0 || gun >= MlumMenu.WEAPON_SLOTS ? ItemStack.EMPTY : inventory.getItem(gun);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < SIZE; i++) {
            if (!getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        if (index < 0 || index >= SIZE) {
            return ItemStack.EMPTY;
        }
        return TaczAttachments.installed(gunStack(index), typeOf(index));
    }

    /**
     * Taking an attachment off the gun.
     *
     * <p>TACZ's {@code unloadAttachment} does not hand the item back, so the fitted stack is read
     * first and returned as the removal - otherwise pulling a scope out of a rifle would delete it
     * rather than put it on the cursor.</p>
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        return removeItemNoUpdate(index);
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack gun = gunStack(index);
        ItemStack fitted = TaczAttachments.installed(gun, typeOf(index));
        if (fitted.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = fitted.copy();
        TaczAttachments.remove(gun, typeOf(index));
        setChanged();
        return taken;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        ItemStack gun = gunStack(index);
        if (gun.isEmpty()) {
            return;
        }
        if (stack == null || stack.isEmpty()) {
            TaczAttachments.remove(gun, typeOf(index));
        } else {
            // one attachment per mounting point, so whatever was there comes off first
            TaczAttachments.remove(gun, typeOf(index));
            TaczAttachments.install(gun, stack.copy());
        }
        setChanged();
    }

    /**
     * An attachment is a single fitting, never a stack.
     *
     * <p>Vanilla will otherwise happily let a player drop five scopes onto one rail and lose four
     * of them, because only the installed one is read back.</p>
     */
    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void setChanged() {
        // the gun stack itself is what changed; the menu re-reads it every tick
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getInventory() == inventory;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SIZE; i++) {
            ItemStack gun = gunStack(i);
            if (!gun.isEmpty()) {
                TaczAttachments.remove(gun, typeOf(i));
            }
        }
    }
}
