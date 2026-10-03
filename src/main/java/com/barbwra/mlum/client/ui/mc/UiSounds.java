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

    private static long lastHover;

    /** Moving onto a new button. Quiet, and never more than one every 45ms so a sweep is not a buzz. */
    public static void hover() {
        long now = System.currentTimeMillis();
        if (now - lastHover < 45L) {
            return;
        }
        lastHover = now;
        play(ModSounds.UI_HOVER, 0.35F, 1.0F);
    }

    public static void click() {
        play(ModSounds.UI_CLICK, 0.8F, 0.95F + (float) Math.random() * 0.1F);
    }

    public static void open() {
        play(ModSounds.UI_OPEN, 0.6F, 1.0F);
    }

    public static void close() {
        play(ModSounds.UI_CLOSE, 0.55F, 1.0F);
    }

    private static void play(RegistryObject<SoundEvent> sound) {
        play(sound, 1.0F, 1.0F);
    }

    private static void play(RegistryObject<SoundEvent> sound, float volume, float pitch) {
        if (!MlumConfig.uiSounds() || !sound.isPresent()) {
            return;
        }
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.get(), pitch, volume));
        } catch (Throwable ignored) {
            // a sound is never worth an exception
        }
    }
}
