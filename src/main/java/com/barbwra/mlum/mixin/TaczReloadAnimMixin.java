package com.barbwra.mlum.mixin;

import com.barbwra.mlum.skill.ReloadSkill;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.ObjectAnimationRunner;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The reload animation, played as fast as the reload itself.
 *
 * <p>The server feeds the rounds sooner (see {@link TaczReloadMixin}); without this the hands would
 * still be pulling the old magazine when the gun is already full. Each frame TACZ advances an
 * animation by the time since the last one - this hands it a longer step, but only for animations
 * whose name starts with {@code reload} ({@code reload_empty}, {@code reload_tactical}, ...), so
 * firing, inspecting and drawing the gun play at their normal speed.</p>
 */
@Mixin(value = ObjectAnimationRunner.class, remap = false)
public abstract class TaczReloadAnimMixin {

    @Shadow
    @Final
    private ObjectAnimation animation;

    @ModifyVariable(method = "updateProgress", at = @At("HEAD"), argsOnly = true)
    private long mlum$fasterReloadAnim(long delta) {
        if (delta <= 0L || animation == null || animation.name == null || !animation.name.startsWith("reload")) {
            return delta;
        }
        float factor = ReloadSkill.factor(Minecraft.getInstance().player);
        return factor == 1.0F ? delta : (long) (delta * (double) factor);
    }
}
