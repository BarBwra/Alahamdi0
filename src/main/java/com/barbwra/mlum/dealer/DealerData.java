package com.barbwra.mlum.dealer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * The dealership's stock: its sections and the vehicles on sale in each, kept with the world as
 * {@code mlum_dealer}. Everything here is written from inside the game by an operator - there is no
 * file to edit and nothing to reload.
 */
public final class DealerData extends SavedData {

    private static final String FILE = "mlum_dealer";

    /** A section of the showroom: cars, armoured, bikes - whatever the operator calls it. */
    public static final class Category {
        public final int id;
        public String name;

        Category(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    /** One vehicle on sale. */
    public static final class Listing {
        public final int id;
        /** The entity type, e.g. {@code superbwarfare:humvee}. */
        public String entity;
        public String name;
        public int category;
        public long price;
        /** The vanilla experience level a buyer needs. */
        public int level;
        /** Sold as a number of uses rather than for good - see {@code VehicleEntry.consumable}. */
        public boolean limited;
        public int count;
        /** When it was put on sale (wall clock), so the showroom can mark new arrivals. */
        public long added;

        Listing(int id) {
            this.id = id;
        }
    }

    private final List<Category> categories = new ArrayList<>();
    private final List<Listing> listings = new ArrayList<>();
    private int nextCategory = 1;
    private int nextListing = 1;

    public static DealerData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("no overworld");
        }
        return overworld.getDataStorage().computeIfAbsent(DealerData::load, DealerData::new, FILE);
    }

    public List<Category> categories() {
        return categories;
    }

    public List<Listing> listings() {
        return listings;
    }

    public Category category(int id) {
        for (Category c : categories) {
            if (c.id == id) {
                return c;
            }
        }
        return null;
    }

    public Listing listing(int id) {
        for (Listing l : listings) {
            if (l.id == id) {
                return l;
            }
        }
        return null;
    }

    public Category addCategory(String name) {
        Category c = new Category(nextCategory++, name);
        categories.add(c);
        setDirty();
        return c;
    }

    public boolean removeCategory(int id) {
        for (Listing l : listings) {
            if (l.category == id) {
                return false;
            }
        }
        boolean removed = categories.removeIf(c -> c.id == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Listing addListing() {
        Listing l = new Listing(nextListing++);
        l.added = System.currentTimeMillis();
        listings.add(l);
        setDirty();
        return l;
    }

    public boolean removeListing(int id) {
        boolean removed = listings.removeIf(l -> l.id == id);
        if (removed) {
            setDirty();
        }
        return removed;
    }

    /** Moves a listing one place up or down within the whole list (which is also its section's order). */
    public void move(int id, int by) {
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).id == id) {
                int j = Math.max(0, Math.min(listings.size() - 1, i + by));
                if (j != i) {
                    listings.add(j, listings.remove(i));
                    setDirty();
                }
                return;
            }
        }
    }

    /** What every client is sent: the sections and the stock, in order. */
    public CompoundTag catalog() {
        CompoundTag tag = new CompoundTag();
        ListTag cats = new ListTag();
        for (Category c : categories) {
            CompoundTag t = new CompoundTag();
            t.putInt("Id", c.id);
            t.putString("Name", c.name);
            cats.add(t);
        }
        tag.put("Cats", cats);
        ListTag items = new ListTag();
        for (Listing l : listings) {
            items.add(write(l));
        }
        tag.put("Items", items);
        return tag;
    }

    private static CompoundTag write(Listing l) {
        CompoundTag t = new CompoundTag();
        t.putInt("Id", l.id);
        t.putString("Entity", l.entity);
        t.putString("Name", l.name);
        t.putInt("Cat", l.category);
        t.putLong("Price", l.price);
        t.putInt("Level", l.level);
        t.putBoolean("Limited", l.limited);
        t.putInt("Count", l.count);
        t.putLong("Added", l.added);
        return t;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.merge(catalog());
        tag.putInt("NextCat", nextCategory);
        tag.putInt("NextItem", nextListing);
        return tag;
    }

    public static DealerData load(CompoundTag tag) {
        DealerData d = new DealerData();
        ListTag cats = tag.getList("Cats", Tag.TAG_COMPOUND);
        for (int i = 0; i < cats.size(); i++) {
            CompoundTag t = cats.getCompound(i);
            d.categories.add(new Category(t.getInt("Id"), t.getString("Name")));
        }
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            CompoundTag t = items.getCompound(i);
            Listing l = new Listing(t.getInt("Id"));
            l.entity = t.getString("Entity");
            l.name = t.getString("Name");
            l.category = t.getInt("Cat");
            l.price = t.getLong("Price");
            l.level = t.getInt("Level");
            l.limited = t.getBoolean("Limited");
            l.count = t.getInt("Count");
            l.added = t.getLong("Added");
            d.listings.add(l);
        }
        d.nextCategory = Math.max(1, tag.getInt("NextCat"));
        d.nextListing = Math.max(1, tag.getInt("NextItem"));
        return d;
    }
}
