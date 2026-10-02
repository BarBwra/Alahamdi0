package com.barbwra.mlum.mixin;

import com.barbwra.mlum.skill.ReloadSkill;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The reload clock, run faster for a player with the تعبئة أسرع skill.
 *
 * <p>{@code getReloadTime()} is how long the current reload has been going, and every TACZ gun
 * script decides when to feed the rounds and when to finish from it. Scaling the answer is the whole
 * skill; see {@link ReloadSkill}. Zero - no reload running - stays zero.</p>
 */
@Mixin(value = ModernKineticGunScriptAPI.class, remap = false)
public abstract class TaczReloadMixin {

    @Inject(method = "getReloadTime", at = @At("RETURN"), cancellable = true)
    private void mlum$fasterReload(CallbackInfoReturnable<Long> cir) {
        long time = cir.getReturnValueJ();
        if (time <= 0L) {
            return;
        }
        float factor = ReloadSkill.factor(((ModernKineticGunScriptAPI) (Object) this).getShooter());
        if (factor != 1.0F) {
            cir.setReturnValue((long) (time * (double) factor));
        }
    }
}
