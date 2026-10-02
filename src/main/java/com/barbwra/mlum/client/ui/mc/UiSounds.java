package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.RegistryObject;

/** The menu's sounds, played on the master channel like any other UI sound. */
@OnlyIn(Dist.CLIENT)
public final class UiSounds {

    private UiSounds() {
    }

    public static void staticBurst(boolean soft) {
        play(soft ? ModSounds.UI_STATIC_SOFT : ModSounds.UI_STATIC);
    }

    public static void coin() {
        play(ModSounds.UI_COIN);
    }

    public static void tick(boolean up) {
        play(up ? ModSounds.UI_TICK_UP : ModSounds.UI_TICK_DOWN);
    }

    private static void play(RegistryObject<SoundEvent> sound) {
        if (!MlumConfig.uiSounds() || !sound.isPresent()) {
            return;
        }
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), 1.0F, 1.0F));
        } catch (Throwable ignored) {
            // a sound is never worth an exception
        }
    }
}
