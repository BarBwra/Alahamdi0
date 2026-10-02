package com.barbwra.mlum.bag;

import com.barbwra.mlum.compat.ShopCompat;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.network.C2SBagMove;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CBagState;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Every bag move, decided on the server.
 *
 * <h2>The shape of a move</h2>
 * <p>Read both grids, resolve the source to a real stack, work out its footprint from config, test
 * the destination, mutate, write back, resync. Any failure at any step falls through to a resync
 * with no mutation, so a refused move costs the player nothing and leaves the client corrected.</p>
 *
 * <h2>Where the two sections live</h2>
 * <p>The base 27 is stored on the player and the pack rows inside the worn backpack, so a move
 * between sections is two different writes. That asymmetry is the reason {@link #save} exists rather
 * than the callers each remembering which grid goes where.</p>
 */
public final class BagService {

    private BagService() {
    }

    /* ------------------------------------------------------------------ loading */

    /** Both grids, reflowed against the current config, with any overflow dropped at the player. */
    public static Bag load(ServerPlayer player) {
        BagGrid base = BagStore.read(player);
        BagGrid pack = BackpackAccess.readGrid(player);

        /*
         * Only the pack can genuinely overflow. A base entry that no longer fits is still sitting in
         * its vanilla slot - BagStore.read simply leaves it undrawn - so there is nothing to drop and
         * nothing to lose. The pack's rows have no other home, so anything that will not fit there
         * after a config change or a downgrade is handed back to the player.
         */
        base.reflow();
        List<ItemStack> overflow = pack.reflow();
        if (!overflow.isEmpty()) {
            for (ItemStack spilled : overflow) {
                if (!player.getInventory().add(spilled)) {
                    player.drop(spilled, false);
                }
            }
            player.sendSystemMessage(Component.literal("بعض الأغراض ما عادت تناسب شنطتك")
                    .withStyle(ChatFormatting.YELLOW));
            save(player, base, pack);
        }
        return new Bag(base, pack);
    }

    public static void save(ServerPlayer player, BagGrid base, BagGrid pack) {
        BagStore.write(player, base);
        BackpackAccess.writeGrid(player, pack);
    }

    /** The pair, so callers can pass both grids around without a two-element array. */
    public record Bag(BagGrid base, BagGrid pack) {

        public BagGrid of(BagSection section) {
            return section == BagSection.PACK ? pack : base;
        }
    }

    /* ------------------------------------------------------------------ syncing */

    /**
     * Sends the player their own bag.
     *
     * <p>Prices are resolved here, once per entry, because this is the only place with both a server
     * and a stack in hand. {@link ShopCompat} answers {@code -1} when MlumShop is absent.</p>
     */
    public static void sync(ServerPlayer player) {
        Bag bag = load(player);
        S2CBagState payload = S2CBagState.of(bag.base(), bag.pack(),
                stack -> ShopCompat.sellPricePerItem(player.getServer(), stack));
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
    }

    /* ------------------------------------------------------------------ moving */

    /**
     * Performs a requested move, or refuses it and resyncs.
     *
     * <h3>One rule for every landing</h3>
     * <p>What is under the destination footprint decides the move, exactly as it does in a vanilla
     * slot: <b>nothing</b> is a placement, <b>one stack of the same item</b> is a merge, <b>one stack
     * of something else</b> is a swap, and <b>two or more</b> is the only genuinely impossible case.
     * Before this, a destination that was not completely empty simply failed - which is why two
     * stacks of the same wood could never be joined no matter how they were dragged.</p>
     *
     * <p>{@code amount} is how much of the source stack is being moved, {@code 0} meaning all of it.
     * That is what makes a right-click place one at a time and a split pick up half; the server still
     * clamps it to what is actually there, so a client asking for more than exists moves what exists
     * and nothing more.</p>
     */
    public static void move(ServerPlayer player, C2SBagMove.Place from, int fromA, int fromB,
                            C2SBagMove.Place to, int toA, int toB, int amount) {
        if (from == C2SBagMove.Place.BAG_PACK && to == C2SBagMove.Place.SLOT) {
            packToSlot(player, fromA, fromB, toA);
            return;
        }
        if (!from.isBag() || !to.isBag()) {
            sync(player);
            return;
        }

        Bag bag = load(player);
        BagSection fromSection = from.section();
        BagSection toSection = to.section();
        BagGrid source = bag.of(fromSection);
        BagGrid target = bag.of(toSection);

        BagEntry moving = source.at(fromA, fromB);
        if (moving == null || moving.isEmpty()) {
            sync(player);
            return;
        }

        int have = moving.stack().getCount();
        int want = amount <= 0 ? have : Math.min(amount, have);

        // Backpacks may not go into the backpack's own rows: a pack inside itself is a container
        // that contains itself, and taking the outer one off would take its own contents with it.
        if (toSection == BagSection.PACK && BagConfig.isBackpack(moving.stack())) {
            deny(player, "ما تقدر تحط شنطة داخل شنطة");
            return;
        }

        ItemSize size = moving.size();
        if (!target.inBounds(toA, toB, size)) {
            sync(player);
            return;
        }
        java.util.List<BagEntry> blocking = target.overlapping(toA, toB, size, moving);

        boolean done;
        if (blocking.isEmpty()) {
            done = place(player, bag, fromSection, moving, toSection, toA, toB, want, have);
        } else if (blocking.size() == 1) {
            BagEntry dest = blocking.get(0);
            if (BagGrid.canMerge(dest.stack(), moving.stack())) {
                done = mergeInto(player, bag, fromSection, moving, dest, want);
            } else if (want >= have) {
                done = swap(player, bag, fromSection, moving, toSection, dest, toA, toB);
            } else {
                done = false;
            }
        } else {
            done = false;
        }

        if (!done) {
            sync(player);
            return;
        }
        save(player, bag.base(), bag.pack());
        sync(player);
    }

    /** Nothing in the way: a pure reposition when the whole stack stays put, a hand-over otherwise. */
    private static boolean place(ServerPlayer player, Bag bag, BagSection fromSection, BagEntry moving,
                                 BagSection toSection, int toA, int toB, int want, int have) {
        if (fromSection == toSection && want >= have) {
            // Within one section and moving all of it, only the position changes - nothing changes
            // owner, so the entry is simply told where it now sits.
            return bag.of(toSection).place(moving, toA, toB);
        }
        /*
         * Either the stack is being split, or it is crossing the separator - the base 27 are vanilla
         * inventory slots and the pack rows are NBT inside the backpack, so crossing means the stack
         * genuinely changes owner. Take first, then give: if the receiving side cannot take it the
         * items go straight back rather than vanishing between the two.
         */
        ItemStack carried = takeFrom(player, bag, fromSection, moving, want);
        if (carried.isEmpty()) {
            return false;
        }
        if (!putAt(player, bag, toSection, toA, toB, carried)) {
            refund(player, carried);
            deny(player, "ما فيه مكان كافي في الحقيبة");
            return false;
        }
        return true;
    }

    /** Same item on the destination cell: pour in as much as it will hold. */
    private static boolean mergeInto(ServerPlayer player, Bag bag, BagSection fromSection,
                                     BagEntry moving, BagEntry dest, int want) {
        int room = BagGrid.limit(dest.stack()) - dest.stack().getCount();
        int moved = Math.min(room, want);
        if (moved <= 0) {
            return false;
        }
        ItemStack carried = takeFrom(player, bag, fromSection, moving, moved);
        if (carried.isEmpty()) {
            return false;
        }
        dest.stack().grow(carried.getCount());
        return true;
    }

    /**
     * A different item on the destination cell: the two trade places.
     *
     * <p>Both footprints are tested against both final positions <i>before</i> anything moves, each
     * ignoring the other - otherwise a wide item would collide with the very stack that is about to
     * step out of its way.</p>
     */
    private static boolean swap(ServerPlayer player, Bag bag, BagSection fromSection, BagEntry moving,
                                BagSection toSection, BagEntry dest, int toA, int toB) {
        int srcRow = moving.row();
        int srcCol = moving.col();
        if (!bag.of(toSection).freeExcept(toA, toB, moving.size(), moving, dest)
                || !bag.of(fromSection).freeExcept(srcRow, srcCol, dest.size(), moving, dest)) {
            return false;
        }
        // the displaced item is going where the moving one came from, so it faces the same rule
        if (fromSection == BagSection.PACK && BagConfig.isBackpack(dest.stack())) {
            return false;
        }
        ItemStack a = takeFrom(player, bag, fromSection, moving, moving.stack().getCount());
        ItemStack b = takeFrom(player, bag, toSection, dest, dest.stack().getCount());
        if (a.isEmpty() || b.isEmpty()) {
            refund(player, a);
            refund(player, b);
            return false;
        }
        if (!putAt(player, bag, toSection, toA, toB, a) || !putAt(player, bag, fromSection, srcRow, srcCol, b)) {
            // Both slots were free a moment ago, so this cannot normally happen - but if it does,
            // the items go back to the player rather than being dropped on the floor of this method.
            refund(player, a);
            refund(player, b);
            return false;
        }
        return true;
    }

    /* ------------------------------------------------------------------ owners */

    /**
     * Removes up to {@code amount} from an entry and hands the removed stack back.
     *
     * <p>The two sections keep their items in different places, and this is the only method that
     * knows which: a base entry <i>is</i> a vanilla inventory slot (its {@code stack()} is the live
     * object Minecraft holds, so splitting it edits the real inventory), a pack entry lives in the
     * backpack's NBT. Everything above this line can then move items without caring.</p>
     */
    private static ItemStack takeFrom(ServerPlayer player, Bag bag, BagSection section,
                                      BagEntry entry, int amount) {
        ItemStack live = entry.stack();
        int n = Math.min(amount, live.getCount());
        if (n <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack out = live.split(n);
        if (live.isEmpty()) {
            if (section == BagSection.BASE) {
                BagStore.take(player, entry.source());
            }
            bag.of(section).remove(entry);
        }
        player.getInventory().setChanged();
        return out;
    }

    /** The other half of {@link #takeFrom}: puts a stack at an exact cell in a section. */
    private static boolean putAt(ServerPlayer player, Bag bag, BagSection section,
                                 int row, int col, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (section == BagSection.BASE) {
            int slot = BagStore.giveSlot(player, stack);
            if (slot < 0) {
                return false;
            }
            bag.base().entries().add(new BagEntry(player.getInventory().getItem(slot), row, col, slot));
            return true;
        }
        bag.pack().addAt(stack, row, col);
        return true;
    }

    /** Last resort for a stack that has left its cell and has nowhere legal to land. */
    private static void refund(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) && !stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /* ------------------------------------------------------------------ drop and gather */

    /**
     * Q on a bag cell: {@code amount} of it onto the ground, {@code 0} meaning the whole stack.
     *
     * <p>Base cells are vanilla slots and could in principle go through vanilla's own THROW click,
     * but pack cells cannot - they are not slots at all. One path for both is what makes Q behave the
     * same wherever the cursor is, which is the whole point of the grid looking uniform.</p>
     */
    public static void drop(ServerPlayer player, BagSection section, int row, int col, int amount) {
        Bag bag = load(player);
        BagEntry entry = bag.of(section).at(row, col);
        if (entry == null || entry.isEmpty()) {
            sync(player);
            return;
        }
        int n = amount <= 0 ? entry.stack().getCount() : Math.min(amount, entry.stack().getCount());
        ItemStack out = takeFrom(player, bag, section, entry, n);
        save(player, bag.base(), bag.pack());
        if (!out.isEmpty()) {
            // true: the thrower's name rides along, so the item cannot be picked straight back up
            player.drop(out, true);
        }
        sync(player);
    }

    /**
     * Double-click on a bag cell: every other stack of the same item poured into it.
     *
     * <p>Smallest stacks first, as vanilla does - it is what clears the scattered leftovers rather
     * than breaking up the one big stack you were about to carry.</p>
     *
     * <p><b>Within one section only.</b> Vanilla sweeps the whole inventory, but here the two halves
     * are different storage: the pack's rows travel with the backpack. A double-click that quietly
     * emptied the pack into the player would change what a player keeps when they take it off, which
     * is not something a tidy-up gesture should decide.</p>
     */
    public static void gather(ServerPlayer player, BagSection section, int row, int col) {
        Bag bag = load(player);
        BagEntry target = bag.of(section).at(row, col);
        if (target == null || target.isEmpty() || !target.stack().isStackable()) {
            sync(player);
            return;
        }
        int room = BagGrid.limit(target.stack()) - target.stack().getCount();
        if (room <= 0) {
            sync(player);
            return;
        }

        List<BagEntry> sources = new java.util.ArrayList<>();
        for (BagEntry e : bag.of(section).entries()) {
            if (e != target && !e.isEmpty() && ItemStack.isSameItemSameTags(e.stack(), target.stack())) {
                sources.add(e);
            }
        }
        sources.sort((a, b) -> Integer.compare(a.stack().getCount(), b.stack().getCount()));

        boolean any = false;
        for (BagEntry s : sources) {
            if (room <= 0) {
                break;
            }
            int moved = Math.min(room, s.stack().getCount());
            ItemStack carried = takeFrom(player, bag, section, s, moved);
            if (carried.isEmpty()) {
                continue;
            }
            target.stack().grow(carried.getCount());
            room -= carried.getCount();
            any = true;
        }
        if (any) {
            save(player, bag.base(), bag.pack());
        }
        sync(player);
    }

    /**
     * Puts an incoming stack anywhere it fits, merging first.
     *
     * <p>The path a chest or quick-access item takes into the bag. Returns whatever would not fit so
     * the caller can leave it where it was - the brief is explicit that a big item with nowhere to go
     * stays put rather than being partially moved.</p>
     */
    public static ItemStack insert(ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return incoming;
        }
        Bag bag = load(player);

        // Top up what is already there before taking a new cell - a second batch of the same thing
        // should join the first, not sit beside it.
        ItemStack rest = bag.base().merge(incoming.copy());
        if (rest.isEmpty()) {
            player.getInventory().setChanged();
            sync(player);
            return ItemStack.EMPTY;
        }
        // The base section is the vanilla inventory, so handing an item to it is an ordinary
        // inventory add. BagStore.read gives it a grid position on the next load.
        if (bag.base().hasRoomFor(rest) && BagStore.give(player, rest)) {
            sync(player);
            return ItemStack.EMPTY;
        }

        rest = bag.pack().merge(rest);
        if (!rest.isEmpty() && bag.pack().add(rest.copy())) {
            rest = ItemStack.EMPTY;
        }

        save(player, bag.base(), bag.pack());
        sync(player);
        return rest;
    }

    public static boolean hasRoomFor(ServerPlayer player, ItemStack stack) {
        Bag bag = load(player);
        return bag.base().hasRoomFor(stack) || bag.pack().hasRoomFor(stack);
    }

    /** Refuses a move with a red toast, and corrects the client. */
    private static void deny(ServerPlayer player, String arabic) {
        com.barbwra.mlum.util.Feedback.bad(player, arabic);
        sync(player);
    }

    /* ------------------------------------------------------------------ pack to a slot */

    /**
     * A backpack-row item dropped on a quick-access cell, a gear socket, a chest cell or a firearm
     * card. Those are real menu slots, so the item leaves the pack's NBT and goes into the slot's
     * own container - but only into an empty slot that accepts it, and never more than it holds.
     */
    private static void packToSlot(ServerPlayer player, int row, int col, int slotIndex) {
        // the same gate vanilla puts in front of every click: a broken chest or a vault the player
        // was removed from takes nothing more
        if (!(player.containerMenu instanceof com.barbwra.mlum.menu.MlumMenu menu)
                || !reachable(menu, slotIndex) || !menu.stillValid(player)) {
            sync(player);
            return;
        }
        net.minecraft.world.inventory.Slot slot = menu.slots.get(slotIndex);
        BagGrid pack = BackpackAccess.readGrid(player);
        BagEntry entry = pack.at(row, col);
        if (entry == null || entry.isEmpty()) {
            sync(player);
            return;
        }
        ItemStack stack = entry.stack();
        if (slot.hasItem()) {
            deny(player, "الخانة مشغولة. فضّها أول");
            return;
        }
        if (!slot.mayPlace(stack)) {
            deny(player, "هذي الخانة ما تقبل هذا الغرض");
            return;
        }
        int fit = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
        if (fit <= 0) {
            sync(player);
            return;
        }
        slot.set(stack.split(fit));
        if (stack.isEmpty()) {
            pack.remove(entry);
        }
        BackpackAccess.writeGrid(player, pack);
        menu.broadcastChanges();
        sync(player);
    }

    /**
     * The slots a pack item may be dropped on from the screen: the chest, quick access, the firearm
     * cards, gear and attachments. Never the base grid (that is a bag move), the ground list, the
     * worn-pack socket, or the glider cell - that one is a throw-away container parked off-screen.
     */
    private static boolean reachable(com.barbwra.mlum.menu.MlumMenu menu, int i) {
        if (i < 0 || i >= menu.slots.size()) {
            return false;
        }
        return (i >= menu.containerStart && i < menu.containerEnd)
                || (i >= menu.quickStart && i < menu.quickEnd)
                || (i >= menu.gunStart && i < menu.gunEnd)
                || (i >= menu.armorStart && i < menu.armorEnd)
                || i == menu.offhandIndex
                || (i >= menu.attachmentStart && i < menu.attachmentEnd);
    }

    /* ------------------------------------------------------------------ sorting */

    /**
     * The رتّب button: both sections repacked, biggest footprint first, same items together.
     *
     * <p>Positions only - nothing changes owner. If the repack would leave anything without a place
     * (it cannot, with the same items, but the grid is the judge) the old layout is kept.</p>
     */
    public static void sort(ServerPlayer player) {
        Bag bag = load(player);
        // Join the loose stacks first. Repacking alone only moved them next to each other, which is
        // why a bag full of wood stayed a bag full of wood-shaped cells however often it was sorted.
        consolidate(player, bag, BagSection.BASE);
        consolidate(player, bag, BagSection.PACK);
        boolean ok = repack(bag.base()) & repack(bag.pack());
        save(player, bag.base(), bag.pack());
        sync(player);
        if (ok) {
            com.barbwra.mlum.util.Feedback.ok(player, "ترتبت الحقيبة · الكبير أول");
        }
    }

    /**
     * Pours every partial stack of one item into the first one, within a single section.
     *
     * <p>Sections are kept apart on purpose: the pack's rows travel with the backpack, so quietly
     * moving items across the separator during a sort would change what a player loses when they take
     * the pack off.</p>
     */
    private static void consolidate(ServerPlayer player, Bag bag, BagSection section) {
        List<BagEntry> list = new java.util.ArrayList<>(bag.of(section).entries());
        for (int i = 0; i < list.size(); i++) {
            BagEntry into = list.get(i);
            if (into.isEmpty() || !into.stack().isStackable()) {
                continue;
            }
            for (int j = i + 1; j < list.size(); j++) {
                BagEntry rest = list.get(j);
                if (rest.isEmpty() || !ItemStack.isSameItemSameTags(into.stack(), rest.stack())) {
                    continue;
                }
                int room = BagGrid.limit(into.stack()) - into.stack().getCount();
                if (room <= 0) {
                    break;
                }
                ItemStack moved = takeFrom(player, bag, section, rest,
                        Math.min(room, rest.stack().getCount()));
                into.stack().grow(moved.getCount());
            }
        }
    }

    private static boolean repack(BagGrid grid) {
        List<BagEntry> entries = new java.util.ArrayList<>(grid.entries());
        if (entries.isEmpty()) {
            return true;
        }
        int[][] before = new int[entries.size()][];
        for (int i = 0; i < entries.size(); i++) {
            before[i] = new int[]{entries.get(i).row(), entries.get(i).col()};
        }
        List<BagEntry> order = new java.util.ArrayList<>(entries);
        order.sort((a, b) -> {
            int byArea = Integer.compare(b.size().cells(), a.size().cells());
            if (byArea != 0) {
                return byArea;
            }
            return idOf(a.stack()).compareTo(idOf(b.stack()));
        });
        grid.clear();
        for (BagEntry e : order) {
            int[] spot = grid.firstFree(e.size(), null);
            if (spot == null) {
                grid.clear();
                for (int i = 0; i < entries.size(); i++) {
                    entries.get(i).moveTo(before[i][0], before[i][1]);
                    grid.entries().add(entries.get(i));
                }
                return false;
            }
            e.moveTo(spot[0], spot[1]);
            grid.entries().add(e);
        }
        return true;
    }

    private static String idOf(ItemStack stack) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "" : id.toString();
    }

    /* ------------------------------------------------------------------ loot all */

    /**
     * خذ الكل: everything in the open chest into the bag, each item where its footprint fits - the
     * bag's own cells first, the pack after - and whatever has no room stays in the chest.
     */
    public static void lootAll(ServerPlayer player, net.minecraft.world.Container chest) {
        int moved = 0;
        int left = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = intoBag(player, stack.copy());
            if (rest.isEmpty()) {
                chest.setItem(i, ItemStack.EMPTY);
                moved++;
            } else {
                chest.setItem(i, rest);
                left++;
            }
        }
        chest.setChanged();
        sync(player);
        if (moved == 0 && left == 0) {
            return;
        }
        if (left > 0) {
            com.barbwra.mlum.util.Feedback.bad(player, "انقلت " + com.barbwra.mlum.util.Feedback.num(moved)
                    + " أغراض · " + com.barbwra.mlum.util.Feedback.num(left) + " ما لها مكان");
        } else {
            com.barbwra.mlum.util.Feedback.ok(player, "انقلت " + com.barbwra.mlum.util.Feedback.num(moved)
                    + " أغراض لحقيبتك");
        }
    }

    /** One stack into the bag: top up matching stacks, then a free base cell, then the pack. */
    private static ItemStack intoBag(ServerPlayer player, ItemStack incoming) {
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        ItemStack rest = incoming;
        if (rest.isStackable()) {
            for (int slot = BagStore.FIRST_SLOT; slot <= BagStore.LAST_SLOT && !rest.isEmpty(); slot++) {
                ItemStack held = inv.getItem(slot);
                if (held.isEmpty() || !ItemStack.isSameItemSameTags(held, rest)) {
                    continue;
                }
                int room = Math.min(held.getMaxStackSize(), inv.getMaxStackSize()) - held.getCount();
                if (room > 0) {
                    int m = Math.min(room, rest.getCount());
                    held.grow(m);
                    rest.shrink(m);
                }
            }
        }
        if (!rest.isEmpty() && BagStore.read(player).hasRoomFor(rest) && BagStore.give(player, rest.copy())) {
            rest = ItemStack.EMPTY;
        }
        if (!rest.isEmpty() && BackpackAccess.isWearing(player) && !BagConfig.isBackpack(rest)) {
            BagGrid pack = BackpackAccess.readGrid(player);
            rest = pack.merge(rest);
            if (!rest.isEmpty() && pack.add(rest.copy())) {
                rest = ItemStack.EMPTY;
            }
            BackpackAccess.writeGrid(player, pack);
        }
        inv.setChanged();
        return rest;
    }

    /**
     * Whether this stack may sit in a quick-access slot.
     *
     * <p>Firearms never may. That rule predates the bag and is enforced by {@code QuickSlot}; it is
     * mirrored here so the bag's own drop handling gives the same answer rather than routing a gun
     * into a slot the menu would then reject.</p>
     */
    public static boolean allowedInQuickAccess(@Nullable ItemStack stack) {
        return stack == null || stack.isEmpty() || !TaczCompat.isGun(stack);
    }
}
