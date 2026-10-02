package com.barbwra.mlum.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;

import javax.annotation.Nullable;

/**
 * Opens a {@link MlumMenu} for a given backing container.
 * Pair it with {@code buf -> buf.writeByte(provider.getRows())} when calling
 * {@code NetworkHooks.openScreen}.
 */
public class MlumMenuProvider implements MenuProvider {

    private final Component title;
    private final Container container;
    private final int rows;

    public MlumMenuProvider(Component title, @Nullable Container container, int rows) {
        this.title = title;
        this.rows = rows;
        this.container = container != null ? container : new SimpleContainer(0);
    }

    public int getRows() {
        return rows;
    }

    @Override
    public Component getDisplayName() {
        return title;
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new MlumMenu(id, inventory, container, rows,
                new SimpleContainerData(MlumMenu.DATA_COUNT));
    }
}
