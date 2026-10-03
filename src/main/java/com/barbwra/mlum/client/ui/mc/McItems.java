package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.ItemSize;
import com.barbwra.mlum.bag.ItemTier;
import com.barbwra.mlum.client.ClientBagState;
import com.barbwra.mlum.client.ClientMoney;
import com.barbwra.mlum.client.hud.TaczGunHud;
import com.barbwra.mlum.client.ui.view.Item;
import com.barbwra.mlum.compat.TaczAttachments;
import com.barbwra.mlum.compat.TaczCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Game items to the views' {@link Item}: name, rarity, size, count, durability, category, price.
 *
 * <p>Cached per stack instance. The game replaces a stack object whenever a slot changes, so the same
 * instance with the same count and damage is the same item as last frame - which turns a screenful
 * of items into a screenful of map lookups. The long description (lore, the item's own lines,
 * enchantments) is only ever built for the one item being inspected.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class McItems {

    private McItems() {
    }

    private static final class Cached {
        final ItemStack stack;
        final int count;
        final int damage;
        final Item item;
        String description;

        Cached(ItemStack stack, Item item) {
            this.stack = stack;
            this.count = stack.getCount();
            this.damage = stack.getDamageValue();
            this.item = item;
        }

        boolean valid(ItemStack s) {
            return s == stack && s.getCount() == count && s.getDamageValue() == damage;
        }
    }

    private static final Map<ItemStack, Cached> CACHE = new java.util.IdentityHashMap<>();
    private static final LinkedHashMap<ItemStack, Boolean> ORDER = new LinkedHashMap<>(256, 0.75F, true);
    private static final int MAX = 600;

    public static void clear() {
        CACHE.clear();
        ORDER.clear();
    }

    /** The view item for a stack, or null for an empty one. */
    public static Item of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Cached c = CACHE.get(stack);
        if (c != null && c.valid(stack)) {
            ORDER.get(stack);
            return c.item;
        }
        Item it = build(stack);
        CACHE.put(stack, new Cached(stack, it));
        ORDER.put(stack, Boolean.TRUE);
        if (ORDER.size() > MAX) {
            ItemStack eldest = ORDER.keySet().iterator().next();
            ORDER.remove(eldest);
            CACHE.remove(eldest);
        }
        return it;
    }

    /** A copy with a different count - a quest reward or a milestone showing "x 150". */
    public static Item withCount(ItemStack stack, int count) {
        Item base = of(stack);
        if (base == null) {
            return null;
        }
        Item it = copy(base);
        it.count = count;
        return it;
    }

    /** The same item, drawn dimmed - locked milestones. */
    public static Item tinted(Item base, int tint) {
        if (base == null) {
            return null;
        }
        Item it = copy(base);
        it.tint = tint;
        return it;
    }

    static Item copy(Item b) {
        Item it = new Item();
        it.handle = b.handle;
        it.name = b.name;
        it.rarity = b.rarity;
        it.count = b.count;
        it.durability = b.durability;
        it.category = b.category;
        it.extra = b.extra;
        it.description = b.description;
        it.price = b.price;
        it.w = b.w;
        it.h = b.h;
        it.gun = b.gun;
        it.art = b.art;
        it.ammo = b.ammo;
        it.ammoName = b.ammoName;
        it.magazine = b.magazine;
        it.tint = b.tint;
        return it;
    }

    private static Item build(ItemStack stack) {
        Item it = new Item();
        it.handle = stack;
        it.name = UiText.logical(stack.getHoverName().getString());
        it.rarity = ItemTier.of(stack).ordinal();
        it.count = stack.getCount();
        if (stack.isDamageableItem() && stack.isDamaged() && stack.getMaxDamage() > 0) {
            it.durability = 1.0F - stack.getDamageValue() / (float) stack.getMaxDamage();
        }
        ItemSize size = BagConfig.sizeOf(stack);
        it.w = size.width();
        it.h = size.height();
        it.gun = TaczCompat.isGun(stack);
        if (it.gun) {
            it.art = TaczGunHud.hudTexture(stack, TaczCompat.loadedRounds(stack) == 0);
            ItemStack round = TaczAttachments.ammoOf(stack);
            if (!round.isEmpty()) {
                it.ammo = round;
                it.ammoName = UiText.logical(round.getHoverName().getString());
            }
            it.magazine = TaczAttachments.magazineSize(stack);
        }
        it.category = category(stack, it.gun);
        double price = ClientBagState.priceOf(stack);
        it.price = price < 0 ? -1L : Math.round(price);
        it.description = "";
        return it;
    }

    /** The inspected item, with its long description filled in (built once per stack). */
    public static Item inspect(ItemStack stack) {
        Item base = of(stack);
        if (base == null) {
            return null;
        }
        Cached c = CACHE.get(stack);
        if (c != null && c.description == null) {
            c.description = describe(stack);
            base.description = c.description;
        }
        return base;
    }

    private static String category(ItemStack stack, boolean gun) {
        net.minecraft.world.item.Item item = stack.getItem();
        if (gun) {
            return "سلاح ناري";
        }
        if (ClientMoney.item() != null && stack.is(ClientMoney.item())) {
            return "عملة";
        }
        if (BagConfig.isBackpack(stack)) {
            return BagConfig.isVipOnly(stack) ? "شنطة · VIP" : "شنطة";
        }
        if (TaczCompat.isAmmo(stack)) {
            return "ذخيرة";
        }
        if (TaczAttachments.available() && TaczAttachments.isAttachment(stack)) {
            return "قطعة سلاح";
        }
        if (item instanceof ArmorItem) {
            return "درع";
        }
        if (item instanceof ShieldItem) {
            return "درع يدوي";
        }
        if (item instanceof SwordItem) {
            return "سلاح أبيض";
        }
        if (item instanceof DiggerItem) {
            return "أداة";
        }
        if (item instanceof PotionItem) {
            return "جرعة";
        }
        if (stack.isEdible()) {
            return "أكل";
        }
        if (item instanceof BlockItem) {
            return "بلوك";
        }
        return "متنوع";
    }

    /** Lore first, then what the item says about itself, then its enchantments. */
    private static String describe(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        try {
            CompoundTag display = stack.getTagElement("display");
            if (display != null && display.contains("Lore", Tag.TAG_LIST)) {
                ListTag lore = display.getList("Lore", Tag.TAG_STRING);
                for (int i = 0; i < lore.size() && lines.size() < 4; i++) {
                    Component line = Component.Serializer.fromJson(lore.getString(i));
                    if (line != null) {
                        add(lines, line.getString());
                    }
                }
            }
            if (lines.isEmpty()) {
                List<Component> own = new ArrayList<>();
                Minecraft mc = Minecraft.getInstance();
                stack.getItem().appendHoverText(stack, mc.level, own, TooltipFlag.NORMAL);
                for (int i = 0; i < own.size() && lines.size() < 4; i++) {
                    add(lines, own.get(i).getString());
                }
            }
            Map<Enchantment, Integer> ench = EnchantmentHelper.getEnchantments(stack);
            if (!ench.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> e : ench.entrySet()) {
                    names.add(e.getKey().getFullname(e.getValue()).getString());
                }
                add(lines, String.join("، ", names));
            }
        } catch (Throwable broken) {
            // a mod's tooltip code throwing costs the description, never the screen
        }
        return String.join(" · ", lines);
    }

    private static void add(List<String> lines, String raw) {
        String s = UiText.logical(raw).trim();
        if (!s.isEmpty()) {
            lines.add(s);
        }
    }
}
