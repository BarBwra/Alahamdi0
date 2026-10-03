package com.barbwra.mlum;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The menu's own sounds, rendered from the design's Web Audio graphs into
 * {@code assets/mlum/sounds/ui}: the TV static between menus (hard and soft), the coin when money
 * comes in, and the tick of picking an item up and putting it down.
 *
 * <p>Registered on both sides like any sound event - the registry is synced - but only ever played
 * on the client, through {@code UiSounds}.</p>
 */
public final class ModSounds {

    private ModSounds() {
    }

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MlumInventory.MODID);

    public static final RegistryObject<SoundEvent> UI_STATIC = register("ui.static");
    public static final RegistryObject<SoundEvent> UI_STATIC_SOFT = register("ui.static_soft");
    public static final RegistryObject<SoundEvent> UI_COIN = register("ui.coin");
    public static final RegistryObject<SoundEvent> UI_TICK_UP = register("ui.tick_up");
    public static final RegistryObject<SoundEvent> UI_TICK_DOWN = register("ui.tick_down");
    public static final RegistryObject<SoundEvent> UI_HOVER = register("ui.hover");
    public static final RegistryObject<SoundEvent> UI_CLICK = register("ui.click");
    public static final RegistryObject<SoundEvent> UI_OPEN = register("ui.open");
    public static final RegistryObject<SoundEvent> UI_CLOSE = register("ui.close");
    public static final RegistryObject<SoundEvent> UI_VAULT_OPEN = register("ui.vault_open");
    public static final RegistryObject<SoundEvent> UI_VAULT_PAGE = register("ui.vault_page");
    public static final RegistryObject<SoundEvent> UI_PURCHASE = register("ui.purchase");
    public static final RegistryObject<SoundEvent> HEARTBEAT = register("feel.heartbeat");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(MlumInventory.id(name)));
    }
}
