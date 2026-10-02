package com.barbwra.mlum.compat;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Recognises Timeless and Classics Zero guns <b>without compiling against TACZ</b>.
 *
 * <p>A hard dependency would mean shipping the TACZ jar to build, and would break the pack the day
 * TACZ renames a class. Registry ids and NBT keys are far more stable.</p>
 *
 * <p><b>Strict by default.</b> An earlier build had a "if TACZ is not installed, accept anything"
 * escape hatch so the two slots would not sit dead in a pack without TACZ. That check could resolve
 * the wrong way during early loading and cache itself, which let every item into the weapon slots.
 * The escape hatch is gone: a stack gets in only if it genuinely looks like a gun, and the two
 * slots simply stay empty in a pack without TACZ. Add ids to {@code weapons.allowedItems} to teach
 * it about another gun mod.</p>
 */
public final class TaczCompat {

    private TaczCompat() {
    }

    public static final String MODID = "tacz";

    /** NBT keys TACZ writes onto a gun stack. Any one of them is enough. */
    private static final String[] GUN_TAGS = {"GunId", "GunFireMode", "GunCurrentAmmoCount", "GunKey", "GunHasBulletInBarrel"};

    /** True when {@code stack} may go into one of the two weapon slots. */
    public static boolean isGun(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) {
            return false;
        }

        // 1. explicitly allow-listed item id (default: tacz:modern_kinetic_gun, the single item
        //    every kinetic gun in TACZ uses, with the actual model stored in NBT)
        if (MlumConfig.gunItemIds().contains(id.toString())) {
            return true;
        }
        if (!MODID.equals(id.getNamespace())) {
            return false;
        }

        // 2. any TACZ item whose registry path reads as a gun
        String path = id.getPath();
        if (path.contains("gun") || path.contains("rifle") || path.contains("pistol")
                || path.contains("shotgun") || path.contains("sniper") || path.contains("smg")) {
            return true;
        }

        // 3. any TACZ item carrying gun NBT
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        for (String key : GUN_TAGS) {
            if (tag.contains(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loaded round count for the weapon card, or -1 when the stack does not expose one.
     * TACZ keeps it on the stack, so this needs no API call.
     */
    public static int ammoCount(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return -1;
        }
        if (tag.contains("GunCurrentAmmoCount")) {
            return tag.getInt("GunCurrentAmmoCount");
        }
        return -1;
    }

    /**
     * The round already chambered, which TACZ counts separately from the magazine.
     *
     * <p>Its own HUD adds it to the magazine count, so a gun reading 30+1 shows 031. Leaving it out
     * would make our readout disagree with the one players are used to by exactly one round.</p>
     */
    public static boolean bulletInBarrel(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("HasBulletInBarrel");
    }

    /** Magazine plus chamber - the big number on the HUD card. */
    public static int loadedRounds(ItemStack stack) {
        int magazine = ammoCount(stack);
        if (magazine < 0) {
            return -1;
        }
        return magazine + (bulletInBarrel(stack) ? 1 : 0);
    }

    /**
     * Spare rounds held on the gun itself, or -1. Only guns using TACZ's "dummy ammo" have this.
     */
    public static int reserveRounds(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("DummyAmmo")) {
            return -1;
        }
        return tag.getInt("DummyAmmo");
    }

    /** The ammo type a gun stack names, when it names one at all. */
    public static String ammoId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return "";
        }
        for (String key : new String[]{"AmmoId", "GunAmmoId", "AmmoID"}) {
            if (tag.contains(key)) {
                return tag.getString(key);
            }
        }
        return "";
    }

    /** True when the stack looks like TACZ ammunition rather than a gun or an attachment. */
    public static boolean isAmmo(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !MODID.equals(id.getNamespace())) {
            return false;
        }
        String path = id.getPath();
        return path.contains("ammo") || path.contains("bullet") || path.contains("magazine");
    }

    /**
     * Total spare rounds the player is carrying for this gun - the second half of a {@code 30/200}
     * readout.
     *
     * <p>Three sources, best first:</p>
     * <ol>
     *   <li>{@code DummyAmmo} on the gun, which is exact when the server uses TACZ's virtual ammo.</li>
     *   <li>Inventory items whose {@code AmmoId} matches the gun's, when the gun names one.</li>
     *   <li>Every TACZ ammo item in the inventory, summed.</li>
     * </ol>
     *
     * <p><b>Why the third case exists and what it costs.</b> The authoritative gun-to-ammo mapping
     * lives in TACZ's <i>data</i> pack, on the logical server, and is not in the client's resource
     * manager - so a client cannot resolve which calibre a gun eats without a hard dependency on
     * TACZ. Rather than show nothing, the fallback counts all TACZ ammo. On a pack where a player
     * carries one calibre it is exactly right; where they carry several it over-counts. That is a
     * better failure than a blank readout, and it is why case 2 is preferred whenever the gun
     * happens to carry the tag.</p>
     */
    public static int reserveInInventory(net.minecraft.world.entity.player.Player player, ItemStack gun) {
        int onGun = reserveRounds(gun);
        if (onGun >= 0) {
            return onGun;
        }

        String wanted = ammoId(gun);
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!isAmmo(stack)) {
                continue;
            }
            if (!wanted.isEmpty()) {
                CompoundTag tag = stack.getTag();
                String have = tag == null ? "" : tag.getString("AmmoId");
                if (!wanted.equals(have)) {
                    continue;
                }
            }
            total += stack.getCount();
        }
        return total;
    }

    /** {@code SEMI}, {@code BURST}, {@code AUTO} or empty when the stack does not say. */
    public static String fireMode(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString("GunFireMode");
    }

    /** The gun's registry id, e.g. {@code tacz:ak47}. Empty when this is not a TACZ gun. */
    public static String gunId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString("GunId");
    }
}
