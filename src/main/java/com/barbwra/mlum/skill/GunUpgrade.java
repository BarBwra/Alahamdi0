package com.barbwra.mlum.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * The تعشيق أكثر skill, written onto the gun rather than onto the player.
 *
 * <h2>Why the gun carries it</h2>
 * <p>TACZ decides what a gun accepts in {@code allowAttachmentType(ItemStack, AttachmentType)}, and
 * both {@code installAttachment} and {@code getAttachment} gate on it - so an attachment the gun's
 * data pack does not list cannot be fitted, and if it somehow were, it would read back as empty.
 * Relaxing that check is the only way to grant extra mounting points, and the only thing the check
 * is handed is <b>the gun stack</b>. There is no player in scope.</p>
 *
 * <p>So the level rides on the weapon. {@link SkillGuns} stamps it onto whatever the owner is
 * carrying and strips it again when the skill is dropped, which also gives the upgrade a sensible
 * meaning when a gun changes hands: it goes with the gun, and the new owner's own level re-stamps
 * it the moment they pick it up.</p>
 *
 * <h2>What each level opens</h2>
 * <p>Fixed sets rather than "the first N the gun is missing", because the check is asked about one
 * type at a time and has no way to know which others the gun already has without asking TACZ
 * recursively.</p>
 */
public final class GunUpgrade {

    private GunUpgrade() {
    }

    /** Read by the TACZ mixin, so the name is part of the contract between them. */
    public static final String TAG = "MlumAttach";

    /**
     * Type names in the order the levels open them, two per level. Plain strings so this class stays
     * free of TACZ imports - the mixin is the only place that has the enum.
     */
    private static final String[][] BY_LEVEL = {
            {"LASER", "MUZZLE"},
            {"EXTENDED_MAG", "GRIP"},
            {"SCOPE", "STOCK"},
    };

    /** The level stamped on this gun, 0 when none. */
    public static int levelOf(ItemStack gun) {
        if (gun == null || gun.isEmpty()) {
            return 0;
        }
        CompoundTag tag = gun.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(BY_LEVEL.length, tag.getInt(TAG)));
    }

    /** Stamps, or strips when {@code level} is 0. Returns true when the stack actually changed. */
    public static boolean stamp(ItemStack gun, int level) {
        if (gun == null || gun.isEmpty()) {
            return false;
        }
        int clamped = Math.max(0, Math.min(BY_LEVEL.length, level));
        if (levelOf(gun) == clamped) {
            return false;
        }
        if (clamped <= 0) {
            CompoundTag tag = gun.getTag();
            if (tag != null) {
                tag.remove(TAG);
            }
        } else {
            gun.getOrCreateTag().putInt(TAG, clamped);
        }
        return true;
    }

    /** Whether a gun stamped at {@code level} should be allowed this attachment type. */
    public static boolean unlocks(int level, String typeName) {
        for (int i = 0; i < level && i < BY_LEVEL.length; i++) {
            for (String open : BY_LEVEL[i]) {
                if (open.equals(typeName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The whole test, for the mixin: does this stack's stamp open this type? */
    public static boolean allows(ItemStack gun, String typeName) {
        return unlocks(levelOf(gun), typeName);
    }
}
