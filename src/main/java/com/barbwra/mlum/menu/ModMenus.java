package com.barbwra.mlum.menu;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    private ModMenus() {
    }

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MlumInventory.MODID);

    /**
     * One menu type covers the plain inventory and every container size - the row count travels
     * in the open-screen buffer so the client rebuilds an identical slot list.
     */
    public static final RegistryObject<MenuType<MlumMenu>> MAIN =
            MENUS.register("main", () -> IForgeMenuType.create(MlumMenu::new));

}
