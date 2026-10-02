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

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(MlumInventory.id(name)));
    }
}
