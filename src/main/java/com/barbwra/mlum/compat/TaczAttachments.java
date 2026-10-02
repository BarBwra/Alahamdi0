package com.barbwra.mlum.compat;

import com.barbwra.mlum.MlumInventory;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.item.ItemStack;

/**
 * Every call this mod makes into TACZ's attachment API, in one place.
 *
 * <p><b>Why this one file is allowed to import TACZ.</b> The rest of the mod reads guns through raw
 * NBT precisely so it never has to. That works for a value stored on the stack - the ammo count, the
 * fire mode - but it cannot answer the question the attachment UI is built around: <i>does this gun
 * accept this attachment?</i> That is not on the item. It lives in the gun's data pack entry, and
 * the only way to it is {@link IGun#allowAttachment}. So the coupling is real, and it is confined
 * here rather than spread across the menu and the screen.</p>
 *
 * <p><b>Nothing here throws.</b> Every method is null-guarded and wrapped, because a pack can be
 * assembled without TACZ, an item can be a gun-shaped thing from another mod, and a TACZ update can
 * change behaviour underneath us. The failure mode is always "no attachments", never a crash in the
 * middle of drawing an inventory.</p>
 */
public final class TaczAttachments {

    private TaczAttachments() {
    }

    /**
     * The six slots a gun gets, in the order they are drawn.
     *
     * <p>{@code NONE} is TACZ's null type and is deliberately absent. All six real types are here
     * rather than the four the sketch showed: a pack that ships suppressors and lasers - and this
     * one does - would otherwise have no way to fit them at all once TACZ's own screen is gone.</p>
     */
    public static final AttachmentType[] SLOTS = {
            AttachmentType.SCOPE,
            AttachmentType.MUZZLE,
            AttachmentType.EXTENDED_MAG,
            AttachmentType.GRIP,
            AttachmentType.STOCK,
            AttachmentType.LASER,
    };

    public static final int COUNT = SLOTS.length;

    /** Whether TACZ is actually on the classpath. Checked once; a pack cannot gain it mid-run. */
    private static final boolean PRESENT = probe();

    private static boolean probe() {
        try {
            Class.forName("com.tacz.guns.api.item.IGun");
            return true;
        } catch (Throwable missing) {
            MlumInventory.LOGGER.info("[{}] TACZ not present - attachment slots are disabled",
                    MlumInventory.MODID);
            return false;
        }
    }

    public static boolean available() {
        return PRESENT;
    }

    /** The short label a slot shows when it is empty. */
    public static String labelOf(AttachmentType type) {
        return switch (type) {
            case SCOPE -> "SCOPE";
            case MUZZLE -> "MUZZLE";
            case EXTENDED_MAG -> "MAG";
            case GRIP -> "GRIP";
            case STOCK -> "STOCK";
            case LASER -> "LASER";
            default -> "";
        };
    }

    /* ------------------------------------------------------------------ guns */

    private static IGun gunOf(ItemStack stack) {
        if (!PRESENT || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return IGun.getIGunOrNull(stack);
        } catch (Throwable broken) {
            return null;
        }
    }

    public static boolean isGun(ItemStack stack) {
        return gunOf(stack) != null;
    }

    /** What is currently fitted in that slot, or empty. */
    public static ItemStack installed(ItemStack gun, AttachmentType type) {
        IGun iGun = gunOf(gun);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack fitted = iGun.getAttachment(gun, type);
            return fitted == null ? ItemStack.EMPTY : fitted;
        } catch (Throwable broken) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Whether this gun has that kind of mounting point at all.
     *
     * <p>This is what draws the X. A rifle with no rail cannot take a scope no matter what the
     * player is holding, and saying so before they try is the whole point of the slot.</p>
     */
    public static boolean acceptsType(ItemStack gun, AttachmentType type) {
        IGun iGun = gunOf(gun);
        if (iGun == null) {
            return false;
        }
        try {
            return iGun.allowAttachmentType(gun, type);
        } catch (Throwable broken) {
            return false;
        }
    }

    /**
     * Whether this specific attachment fits this specific gun.
     *
     * <p>Stricter than {@link #acceptsType}: a gun may take scopes in general and still refuse one
     * particular scope, which TACZ expresses through its exclusive-attachment list. This is the
     * check behind the highlight, so the highlight never promises a fit that will be refused.</p>
     */
    public static boolean accepts(ItemStack gun, ItemStack attachment) {
        IGun iGun = gunOf(gun);
        if (iGun == null || attachment == null || attachment.isEmpty()) {
            return false;
        }
        try {
            return iGun.allowAttachment(gun, attachment);
        } catch (Throwable broken) {
            return false;
        }
    }

    /** Fits an attachment. The caller must have checked {@link #accepts} first. */
    public static void install(ItemStack gun, ItemStack attachment) {
        IGun iGun = gunOf(gun);
        if (iGun == null || attachment == null || attachment.isEmpty()) {
            return;
        }
        try {
            iGun.installAttachment(gun, attachment);
        } catch (Throwable broken) {
            MlumInventory.LOGGER.warn("[{}] TACZ refused to install an attachment: {}",
                    MlumInventory.MODID, broken.toString());
        }
    }

    /** Strips whatever is in that slot. TACZ does not hand the item back, so read it first. */
    public static void remove(ItemStack gun, AttachmentType type) {
        IGun iGun = gunOf(gun);
        if (iGun == null) {
            return;
        }
        try {
            iGun.unloadAttachment(gun, type);
        } catch (Throwable broken) {
            MlumInventory.LOGGER.warn("[{}] TACZ refused to remove an attachment: {}",
                    MlumInventory.MODID, broken.toString());
        }
    }

    /* ----------------------------------------------------------- attachments */

    /** The type of an attachment item, or null when the stack is not one. */
    public static AttachmentType typeOf(ItemStack stack) {
        if (!PRESENT || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) {
                return null;
            }
            AttachmentType type = attachment.getType(stack);
            return type == null || type == AttachmentType.NONE ? null : type;
        } catch (Throwable broken) {
            return null;
        }
    }

    public static boolean isAttachment(ItemStack stack) {
        return typeOf(stack) != null;
    }

    /** Index into {@link #SLOTS} for a type, or -1. */
    public static int slotIndexOf(AttachmentType type) {
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == type) {
                return i;
            }
        }
        return -1;
    }
}
