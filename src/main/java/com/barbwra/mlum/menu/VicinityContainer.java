package com.barbwra.mlum.menu;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * A virtual {@link Container} whose slots are windows onto real {@link ItemEntity}s lying on the
 * ground near the player.
 *
 * <p><b>How the sync works (and why it costs almost nothing):</b> the container is exactly as wide
 * as the number of visible rows - never as wide as the world. Every scan tick the server rebinds
 * those few slots to the nearest item entities and copies their stacks into {@code mirror}.
 * Vanilla's {@code AbstractContainerMenu.broadcastChanges()} then diffs {@code mirror} against
 * {@code lastSlots} and emits a {@code ClientboundContainerSetSlotPacket} only for slots that
 * actually changed. No bespoke packet, no per-tick entity list on the wire, and nothing at all is
 * sent to players who do not have the screen open.</p>
 *
 * <p>On the client the entity array is {@code null} and the mirror is driven purely by those
 * vanilla slot packets, so the same class works on both sides.</p>
 */
public class VicinityContainer implements Container {

    private final int size;
    private final NonNullList<ItemStack> mirror;

    /** Server only. {@code entities[i]} is the world entity backing slot {@code i}, or null. */
    @Nullable
    private final ItemEntity[] entities;
    @Nullable
    private final ServerPlayer owner;

    public VicinityContainer(int size) {
        this(size, null);
    }

    public VicinityContainer(int size, @Nullable ServerPlayer owner) {
        this.size = Math.max(0, size);
        this.mirror = NonNullList.withSize(this.size, ItemStack.EMPTY);
        this.owner = owner;
        this.entities = owner == null ? null : new ItemEntity[this.size];
    }

    public boolean isServerSide() {
        return entities != null;
    }

    /**
     * Points the visible window at {@code found}, starting at {@code offset}.
     * Server side only; called from the menu's scan tick.
     */
    public void bind(List<ItemEntity> found, int offset) {
        if (entities == null) {
            return;
        }
        for (int i = 0; i < size; i++) {
            int idx = offset + i;
            ItemEntity entity = idx >= 0 && idx < found.size() ? found.get(idx) : null;
            if (entity != null && (!entity.isAlive() || entity.getItem().isEmpty())) {
                entity = null;
            }
            entities[i] = entity;
            mirror.set(i, entity == null ? ItemStack.EMPTY : entity.getItem().copy());
        }
    }

    /** The world entity behind a slot, or null on the client / for an empty row. */
    @Nullable
    public ItemEntity entityAt(int index) {
        if (entities == null || index < 0 || index >= size) {
            return null;
        }
        ItemEntity entity = entities[index];
        return entity != null && entity.isAlive() ? entity : null;
    }

    /* ------------------------------------------------------------- Container */

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : mirror) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < size ? mirror.get(index) : ItemStack.EMPTY;
    }

    /**
     * Takes {@code count} items out of the ground entity behind this slot. Shrinking the live
     * stack and calling {@link Player#take} gives the normal pickup animation and sound, so
     * looting from the panel feels identical to walking over the item.
     */
    @Override
    public ItemStack removeItem(int index, int count) {
        if (index < 0 || index >= size || count <= 0) {
            return ItemStack.EMPTY;
        }
        if (entities == null) {
            // client: just keep the predicted view consistent
            ItemStack removed = ContainerHelper.removeItem(mirror, index, count);
            if (!removed.isEmpty()) {
                setChanged();
            }
            return removed;
        }

        ItemEntity entity = entityAt(index);
        if (entity == null) {
            mirror.set(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        if (owner != null && !withinReach(entity)) {
            // moved out of range between the click and the packet arriving
            entities[index] = null;
            mirror.set(index, ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        ItemStack live = entity.getItem();
        int taken = Math.min(count, live.getCount());
        if (taken <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack result = live.split(taken);

        if (owner != null) {
            owner.take(entity, taken);
        }
        if (live.isEmpty()) {
            entity.discard();
            entities[index] = null;
            mirror.set(index, ItemStack.EMPTY);
        } else {
            // a fresh instance so SynchedEntityData actually flags the change
            entity.setItem(live.copy());
            mirror.set(index, entity.getItem().copy());
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack current = getItem(index);
        return current.isEmpty() ? ItemStack.EMPTY : removeItem(index, current.getCount());
    }

    /**
     * Ground slots never accept items ({@code VicinitySlot#mayPlace} is false), so the only caller
     * is the client applying a server slot packet - which is exactly what the mirror is for.
     */
    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= size) {
            return;
        }
        if (entities == null) {
            mirror.set(index, stack);
        }
    }

    @Override
    public void setChanged() {
        // nothing persistent to flush - the world entities are the storage
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < size; i++) {
            mirror.set(i, ItemStack.EMPTY);
            if (entities != null) {
                entities[i] = null;
            }
        }
    }

    private boolean withinReach(ItemEntity entity) {
        if (owner == null) {
            return true;
        }
        if (entity.level() != owner.level()) {
            return false;
        }
        double slack = VicinityScanner.reachSlack();
        double radius = com.barbwra.mlum.MlumConfig.vicinityRadius() + slack;
        return entity.distanceToSqr(owner.position()) <= radius * radius;
    }
}
