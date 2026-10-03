package com.barbwra.mlum.client.feel;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.ModSounds;
import com.barbwra.mlum.downed.DownedState;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Low health you can feel. Under a third of your hearts the edges of the screen close in dark red,
 * a heartbeat starts and quickens as you get lower, and the world goes quieter - as if your ears
 * were ringing. Healing lets it all go again.
 *
 * <p>The red edge is the game's own vignette texture drawn with its own darkening blend, only with
 * red held back, so it costs nothing and needs no shader. The muffle scales the volume of each
 * world sound as it starts; menu sounds, music and the heartbeat are left alone.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class HealthFeel {

    private HealthFeel() {
    }

    private static final ResourceLocation VIGNETTE = new ResourceLocation("textures/misc/vignette.png");
    /** Below this share of max health it begins; at {@link #FULL} it is as strong as it gets. */
    private static final float START = 0.35F;
    private static final float FULL = 0.1F;

    /** 0..1, eased toward the target so a hit or a heal fades rather than jumps. */
    private static float level;
    private static long lastBeat;
    private static long beatAt;

    private static float target(LocalPlayer player) {
        if (player == null || !player.isAlive() || player.isCreative() || player.isSpectator() || DownedState.isDowned(player)) {
            return 0.0F;
        }
        float share = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
        return Mth.clamp((START - share) / (START - FULL), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float want = MlumConfig.healthFeel() ? target(mc.player) : 0.0F;
        level += (want - level) * 0.12F;
        if (level < 0.01F) {
            level = 0.0F;
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        long now = System.currentTimeMillis();
        long gap = (long) Mth.lerp(level, 1150.0F, 560.0F);
        if (now - lastBeat >= gap && ModSounds.HEARTBEAT.isPresent()) {
            lastBeat = now;
            beatAt = now;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.HEARTBEAT.get(), 1.0F, 0.25F + 0.55F * level));
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Pre event) {
        if (level <= 0.0F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = event.getWindow().getGuiScaledWidth();
        int h = event.getWindow().getGuiScaledHeight();
        // each beat tightens the edge for a moment
        float since = (System.currentTimeMillis() - beatAt) / 1000.0F;
        float pulse = since < 0.35F ? 1.0F - since / 0.35F : 0.0F;
        float k = Mth.clamp(level * (0.8F + 0.25F * pulse), 0.0F, 1.0F);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        g.setColor(0.12F * k, 0.9F * k, 0.9F * k, 1.0F);
        g.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        if (k > 0.5F) {
            // a second, softer pass deepens the corners when it is bad
            float k2 = (k - 0.5F) * 2.0F;
            g.setColor(0.25F * k2, 0.6F * k2, 0.6F * k2, 1.0F);
            g.blit(VIGNETTE, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        if (mc.options.hideGui) {
            return;
        }
        // and a faint grey wash over everything, draining the colour a little
        int wash = (int) (k * 0x30) << 24 | 0x2A2A2A;
        g.fill(0, 0, w, h, wash);
    }

    /* ------------------------------------------------------------------ the muffle */

    @SubscribeEvent
    public static void onSound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (level <= 0.05F || sound == null || !MlumConfig.healthMuffle() || sound instanceof TickableSoundInstance
                || sound instanceof Muffled) {
            return;
        }
        SoundSource source = sound.getSource();
        if (source == SoundSource.MASTER || source == SoundSource.MUSIC || source == SoundSource.RECORDS
                || MlumInventory.MODID.equals(sound.getLocation().getNamespace())) {
            return;
        }
        event.setSound(new Muffled(sound, 1.0F - 0.6F * level));
    }

    /** Another sound, quieter. Everything else is passed through untouched. */
    private static final class Muffled implements SoundInstance {
        private final SoundInstance inner;
        private final float scale;

        Muffled(SoundInstance inner, float scale) {
            this.inner = inner;
            this.scale = scale;
        }

        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * scale;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
            return inner.getStream(library, sound, looping);
        }
    }
}
