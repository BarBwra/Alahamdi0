package com.barbwra.mlum.mixin;

import com.barbwra.mlum.skill.GunUpgrade;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The only reason this mod touches TACZ's bytecode: the تعشيق أكثر skill.
 *
 * <h2>Why a mixin was unavoidable</h2>
 * <p>A gun's mounting points are not on the item, they are in its data pack entry, and TACZ checks
 * them in two places that both have to agree:</p>
 * <pre>
 *   installAttachment: if (!allowAttachment(gun, part)) return;        // refuses to fit it
 *   getAttachment:     if (!allowAttachmentType(gun, type)) return EMPTY;  // refuses to read it
 * </pre>
 * <p>So writing the attachment into the gun's NBT directly does not work either - TACZ hands back
 * an empty stack for anything the data pack does not list, and the part would apply no stats and
 * render nothing. Relaxing the check itself is the only route, and this is the narrowest version of
 * it: two returns, both only ever flipped from false to true, and only for a gun carrying the
 * skill's own tag.</p>
 *
 * <h2>What it cannot fix</h2>
 * <p>Whether the gun's <i>model</i> has somewhere to hang the part. TACZ positions attachments from
 * mount points in the gun's model, and a rifle with no rail has none - the scope will work and will
 * be invisible, or sit at the origin. That is a data-pack question, not a code one.</p>
 */
@Mixin(value = AbstractGunItem.class, remap = false)
public abstract class TaczAttachmentMixin {

    /**
     * "Does this gun have this kind of mounting point?" - the check behind the empty slot and the X.
     *
     * <p>Only ever widens the answer. A type the gun already accepts returns true before this runs
     * and is left alone, so no existing behaviour changes for a gun without the tag.</p>
     */
    @Inject(method = "allowAttachmentType", at = @At("RETURN"), cancellable = true)
    private void mlum$unlockType(ItemStack gun, AttachmentType type, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && type != null && GunUpgrade.allows(gun, type.name())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * "Does this specific part fit this specific gun?" - stricter than the type check, and the one
     * {@code installAttachment} consults before it writes anything.
     *
     * <p>Widened the same way, and it has to be: opening the mounting point without this would leave
     * a slot that accepts the drop and then silently refuses to keep it.</p>
     */
    @Inject(method = "allowAttachment", at = @At("RETURN"), cancellable = true)
    private void mlum$unlockPart(ItemStack gun, ItemStack attachment, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || attachment == null || attachment.isEmpty()) {
            return;
        }
        AttachmentType type = com.barbwra.mlum.compat.TaczAttachments.typeOf(attachment);
        if (type != null && GunUpgrade.allows(gun, type.name())) {
            cir.setReturnValue(true);
        }
    }
}
