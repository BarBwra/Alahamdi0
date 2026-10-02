package com.barbwra.mlum.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Stored energy on a single item: the defibrillator's charge and a battery's.
 *
 * <p>Kept on the stack itself, so a half-spent battery that goes back into the bag is still half
 * spent when it comes out again, and travels with the item through chests, deaths and trades. The
 * HUD's belt and the bag both read it from here, so the two can never disagree about what a
 * battery holds.</p>
 */
public final class ChargeTag {

    private ChargeTag() {
    }

    public static final String CHARGE = "MlumCharge";
    public static final String MAX = "MlumChargeMax";
    /** {@code defib} or {@code battery}; decides how the HUD draws the charge. */
    public static final String KIND = "MlumChargeKind";

    public static final String DEFIB = "defib";
    public static final String BATTERY = "battery";

    public static boolean has(ItemStack stack) {
        return max(stack) > 0;
    }

    public static int charge(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(tag.getInt(CHARGE), tag.getInt(MAX)));
    }

    public static int max(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? 0 : Math.max(0, tag.getInt(MAX));
    }

    public static String kind(ItemStack stack) {
        CompoundTag tag = stack == null ? null : stack.getTag();
        return tag == null ? "" : tag.getString(KIND);
    }

    public static float fraction(ItemStack stack) {
        int max = max(stack);
        return max <= 0 ? 0.0F : charge(stack) / (float) max;
    }

    public static void set(ItemStack stack, String kind, int charge, int max) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(KIND, kind);
        tag.putInt(MAX, Math.max(0, max));
        tag.putInt(CHARGE, Math.max(0, Math.min(charge, max)));
    }

    public static void setCharge(ItemStack stack, int charge) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(CHARGE, Math.max(0, Math.min(charge, tag.getInt(MAX))));
    }
}
