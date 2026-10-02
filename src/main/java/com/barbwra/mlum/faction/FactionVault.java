package com.barbwra.mlum.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The faction's shared storage: several pages, each a grid whose height the faction level decides.
 *
 * <p><b>Slots are never destroyed.</b> A page is stored as a sparse map of slot index to stack, not
 * a fixed-size array, so nothing has to be resized when a faction levels up - and, far more
 * importantly, nothing is dropped if a faction ever loses a level or the curve is retuned. A stack
 * sitting in a row that is no longer open stays on disk, invisible, and reappears the moment the
 * rows come back. An array would have thrown it away silently, which is the single worst thing a
 * storage system can do.</p>
 *
 * <p><b>Access is per page, per rank.</b> Each page records the lowest rank that may take from it
 * and the lowest rank that may see it at all, which is what makes "he can see it but he cannot take
 * it" expressible. Both default to open, so a faction that never touches the settings behaves like
 * an ordinary shared chest.</p>
 *
 * <p>Buying a page records the purchase time; the page stays sealed until
 * {@link FactionLevel#pageUnlockMillis()} has passed. The unlock is checked against wall clock on
 * read rather than driven by a timer, so it keeps running while the server is down.</p>
 */
public final class FactionVault {

    /** Sparse slot contents per page, keyed by 1-based page number. */
    private final Map<Integer, Map<Integer, ItemStack>> pages = new HashMap<>();

    /** When each page was paid for, keyed by page. Absent means never bought. */
    private final Map<Integer, Long> purchasedAt = new HashMap<>();

    /** Lowest rank that may withdraw from a page. Absent means everyone. */
    private final Map<Integer, FactionRole> takeAccess = new HashMap<>();

    /** Lowest rank that may open a page at all. Absent means everyone. */
    private final Map<Integer, FactionRole> viewAccess = new HashMap<>();

    /* ---------------------------------------------------------------- availability */

    /**
     * Whether a page is paid for and past its timer.
     *
     * <p>Page one is always open - it is free and immediate, so a brand new faction has somewhere to
     * put things before it has any money at all.</p>
     */
    public boolean isUnlocked(int page, long now) {
        if (page <= 1) {
            return true;
        }
        Long bought = purchasedAt.get(page);
        return bought != null && now - bought >= FactionLevel.pageUnlockMillis();
    }

    /** True once paid for, whether or not the timer has run out. */
    public boolean isPurchased(int page) {
        return page <= 1 || purchasedAt.containsKey(page);
    }

    /** Milliseconds until a bought page opens; 0 when it is open or was never bought. */
    public long remainingLock(int page, long now) {
        if (page <= 1 || !purchasedAt.containsKey(page)) {
            return 0L;
        }
        long elapsed = now - purchasedAt.get(page);
        return Math.max(0L, FactionLevel.pageUnlockMillis() - elapsed);
    }

    /** Records payment. The caller is responsible for having taken the money first. */
    public void markPurchased(int page, long now) {
        if (page > 1) {
            purchasedAt.putIfAbsent(page, now);
        }
    }

    /**
     * Whether a page is usable right now: within the level's reach, bought, and unsealed.
     *
     * <p>All three have to hold. A faction can buy page three before reaching it, and it will sit
     * there paid for and empty until the level catches up - which is deliberate, because refusing
     * the sale would mean a faction cannot prepare for a level it is about to reach.</p>
     */
    public boolean isOpen(int page, int level, long now) {
        return FactionLevel.rowsOnPage(level, page) > 0 && isUnlocked(page, now);
    }

    /* --------------------------------------------------------------------- access */

    public FactionRole takeRank(int page) {
        return takeAccess.getOrDefault(page, FactionRole.GUEST);
    }

    public FactionRole viewRank(int page) {
        return viewAccess.getOrDefault(page, FactionRole.GUEST);
    }

    public void setTakeRank(int page, FactionRole role) {
        takeAccess.put(page, role == null ? FactionRole.GUEST : role);
    }

    public void setViewRank(int page, FactionRole role) {
        viewAccess.put(page, role == null ? FactionRole.GUEST : role);
    }

    /** Whether this rank may open the page and look inside. */
    public boolean canView(int page, FactionRole role) {
        return role != null && role.atLeast(viewRank(page));
    }

    /** Whether this rank may take items out. Viewing is implied but checked separately. */
    public boolean canTake(int page, FactionRole role) {
        return role != null && canView(page, role) && role.atLeast(takeRank(page));
    }

    /* -------------------------------------------------------------------- contents */

    public ItemStack get(int page, int slot) {
        Map<Integer, ItemStack> contents = pages.get(page);
        if (contents == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = contents.get(slot);
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public void set(int page, int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            Map<Integer, ItemStack> contents = pages.get(page);
            if (contents != null) {
                contents.remove(slot);
                if (contents.isEmpty()) {
                    pages.remove(page);
                }
            }
            return;
        }
        pages.computeIfAbsent(page, p -> new HashMap<>()).put(slot, stack);
    }

    /** Every page that holds at least one item, including ones currently out of reach. */
    public List<Integer> occupiedPages() {
        return new ArrayList<>(pages.keySet());
    }

    /* ------------------------------------------------------------------ persistence */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();

        ListTag pageList = new ListTag();
        for (Map.Entry<Integer, Map<Integer, ItemStack>> page : pages.entrySet()) {
            CompoundTag pageTag = new CompoundTag();
            pageTag.putInt("Page", page.getKey());
            ListTag slots = new ListTag();
            for (Map.Entry<Integer, ItemStack> slot : page.getValue().entrySet()) {
                if (slot.getValue() == null || slot.getValue().isEmpty()) {
                    continue;
                }
                CompoundTag slotTag = new CompoundTag();
                slotTag.putInt("Slot", slot.getKey());
                slot.getValue().save(slotTag);
                slots.add(slotTag);
            }
            pageTag.put("Slots", slots);
            pageList.add(pageTag);
        }
        tag.put("Pages", pageList);

        CompoundTag bought = new CompoundTag();
        purchasedAt.forEach((page, at) -> bought.putLong(String.valueOf(page), at));
        tag.put("Purchased", bought);

        CompoundTag take = new CompoundTag();
        takeAccess.forEach((page, role) -> take.putString(String.valueOf(page), role.name()));
        tag.put("TakeAccess", take);

        CompoundTag view = new CompoundTag();
        viewAccess.forEach((page, role) -> view.putString(String.valueOf(page), role.name()));
        tag.put("ViewAccess", view);

        return tag;
    }

    public static FactionVault load(CompoundTag tag) {
        FactionVault vault = new FactionVault();

        ListTag pageList = tag.getList("Pages", Tag.TAG_COMPOUND);
        for (int i = 0; i < pageList.size(); i++) {
            CompoundTag pageTag = pageList.getCompound(i);
            int page = pageTag.getInt("Page");
            ListTag slots = pageTag.getList("Slots", Tag.TAG_COMPOUND);
            for (int s = 0; s < slots.size(); s++) {
                CompoundTag slotTag = slots.getCompound(s);
                ItemStack stack = ItemStack.of(slotTag);
                if (!stack.isEmpty()) {
                    vault.set(page, slotTag.getInt("Slot"), stack);
                }
            }
        }

        CompoundTag bought = tag.getCompound("Purchased");
        for (String key : bought.getAllKeys()) {
            vault.purchasedAt.put(Integer.parseInt(key), bought.getLong(key));
        }

        CompoundTag take = tag.getCompound("TakeAccess");
        for (String key : take.getAllKeys()) {
            vault.takeAccess.put(Integer.parseInt(key), FactionRole.byName(take.getString(key)));
        }

        CompoundTag view = tag.getCompound("ViewAccess");
        for (String key : view.getAllKeys()) {
            vault.viewAccess.put(Integer.parseInt(key), FactionRole.byName(view.getString(key)));
        }

        return vault;
    }
}
