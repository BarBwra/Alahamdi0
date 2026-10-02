package com.barbwra.mlum.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.UUID;

/**
 * Everything that happens to a mount the moment it is summoned: it is tamed to its owner, saddled,
 * upgraded, and stamped so nobody else can ride it.
 *
 * <p><b>Upgrades live on the player, not on the horse.</b> A summoned mount is a fresh entity every
 * time - store it, summon it again and it is a different object - so anything kept on the animal
 * would be lost on the first store. Keeping the levels in the player's persistent tag means the
 * horse that comes out is always the horse they paid for.</p>
 *
 * <p>Ownership is written into the entity's own persistent data as well as its vanilla tame owner,
 * because the vanilla field is only present on {@link AbstractHorse}. The persistent tag lets the
 * same rule extend to any future rideable without touching this class.</p>
 */
public final class MountSetup {

    private MountSetup() {
    }

    /** Written on the summoned entity. Read by {@link MountRules} to refuse other riders. */
    public static final String KEY_OWNER = "mlum:mount_owner";
    /** Written on the player. */
    private static final String KEY_SPEED = "mlum:mount_speed";
    private static final String KEY_ARMOR = "mlum:mount_armor";
    private static final String KEY_COLOR = "mlum:mount_color";

    /** Undyed leather horse armour, so an un-coloured mount still looks deliberate. */
    private static final int DEFAULT_COLOR = 0xA06540;

    private static final UUID SPEED_MODIFIER = UUID.fromString("2f7c1a90-51de-4d1c-9a3b-7c2e5b8f0011");
    private static final UUID HEALTH_MODIFIER = UUID.fromString("2f7c1a90-51de-4d1c-9a3b-7c2e5b8f0012");
    private static final UUID JUMP_MODIFIER = UUID.fromString("2f7c1a90-51de-4d1c-9a3b-7c2e5b8f0013");

    /** +14% movement per level - five levels is a genuinely fast horse without being a glitch. */
    private static final double SPEED_PER_LEVEL = 0.14D;
    private static final double HEALTH_PER_LEVEL = 6.0D;
    private static final double JUMP_PER_LEVEL = 0.05D;

    /* ---------------------------------------------------------------- storage */

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    public static int color(Player player) {
        CompoundTag tag = root(player);
        return tag.contains(KEY_COLOR) ? tag.getInt(KEY_COLOR) : DEFAULT_COLOR;
    }

    /** Operator-set only - see the {@code /VehicleMenu mountcolor} command. */
    public static void setColor(Player player, int rgb) {
        root(player).putInt(KEY_COLOR, rgb & 0xFFFFFF);
    }

    /* ------------------------------------------------------------------ setup */

    /**
     * Called from {@code VehicleGarage.summon} after the entity is created and before it is added
     * to the world, so the horse is already tamed and equipped on the very first frame it exists.
     *
     * <p>Anything that is not a mount falls straight through - cars and boats are summoned by the
     * same path and must not be touched.</p>
     */
    public static void onSummon(ServerPlayer player, Entity entity) {
        entity.getPersistentData().putUUID(KEY_OWNER, player.getUUID());

        if (!(entity instanceof AbstractHorse horse)) {
            return;
        }

        horse.setTamed(true);
        horse.setOwnerUUID(player.getUUID());
        // Without this the horse still bucks the first few times it is mounted even when tamed.
        horse.setTemper(horse.getMaxTemper());

        // Slot 400 is the saddle, 401 the armour - the same indices the vanilla horse screen uses.
        horse.getSlot(400).set(new ItemStack(Items.SADDLE));

        /*
         * No leather armour unless an operator has actually assigned this player a colour. It used
         * to be equipped on every summon, which put a beige blanket on every horse on the server
         * and made the colour reward meaningless - a mount that looks special has to start out
         * looking ordinary. The saddle stays, because without one the horse cannot be ridden.
         */
        if (hasColor(player)) {
            horse.getSlot(401).set(armor(player));
        } else {
            horse.getSlot(401).set(ItemStack.EMPTY);
        }

        // upgrades removed: a horse is a horse. Colour and the owner glow are what stayed.
    }

    /** True once an operator has set a colour - the flag that also turns the armour on. */
    public static boolean hasColor(Player player) {
        return root(player).contains(KEY_COLOR);
    }

    private static ItemStack armor(Player player) {
        ItemStack stack = new ItemStack(Items.LEATHER_HORSE_ARMOR);
        if (stack.getItem() instanceof DyeableLeatherItem dyeable) {
            dyeable.setColor(stack, color(player));
        }
        return stack;
    }

    /**
     * Applies the owner's upgrade levels as attribute modifiers.
     *
     * <p>Fixed modifier UUIDs so re-applying is idempotent, and health is topped up afterwards -
     * raising max health without healing leaves the horse spawning on a fraction of its new bar,
     * which reads as the upgrade having done nothing.</p>
     */

    private static void add(AttributeInstance instance, UUID id, String name,
                            double amount, AttributeModifier.Operation operation) {
        if (instance == null) {
            return;
        }
        if (instance.getModifier(id) != null) {
            instance.removeModifier(id);
        }
        if (amount != 0.0D) {
            instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    /** Pushes the upgrade state to the owner's client. */

    /** The player this mount belongs to, or null when it was not summoned from the garage. */
    public static UUID ownerOf(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
    }
}
