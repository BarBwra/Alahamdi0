package com.barbwra.mlum.warehouse.service;

import com.barbwra.mlum.warehouse.core.CargoType;
import com.barbwra.mlum.warehouse.core.Crate;
import com.barbwra.mlum.warehouse.core.Warehouse;
import com.barbwra.mlum.warehouse.market.MarketState;

import java.util.List;
import java.util.UUID;

/**
 * Crates in and out of a warehouse, plus what they are currently worth.
 *
 * <p><b>Capacity is enforced where crates are created, not where they are drawn.</b> The prototype
 * derived a page count from the Logistics level and drew at most six pages, but production appended
 * to storage unconditionally - so any crate past slot 216 existed, was counted, aged and eventually
 * spoiled, while being permanently invisible and unsellable. Capacity here is a precondition of
 * gaining a crate at all.</p>
 */
public final class StorageService {

    private StorageService() {
    }

    private static final long DAY_MILLIS = 86_400_000L;

    /* ------------------------------------------------------------------ create */

    /**
     * Files a finished crate into storage.
     *
     * <p>Shelf life is stamped here, from the owner's Logistics level at the moment the crate is
     * produced. Buying the upgrade later does not retroactively extend crates already on the shelf,
     * which is the behaviour a player expects from a storage improvement.</p>
     *
     * @return the stored crate, or {@code null} when the warehouse is full
     */
    public static Crate store(Warehouse warehouse, CargoType cargo, int tier, int baseValue, long now) {
        if (!warehouse.hasSpace()) {
            return null;
        }
        long expires = now + (long) warehouse.shelfLifeDays() * DAY_MILLIS;
        Crate crate = new Crate(UUID.randomUUID(), cargo, tier, baseValue, now, expires, false);
        warehouse.addCrate(crate);
        return crate;
    }

    /* ----------------------------------------------------------------- cleanup */

    /**
     * Deletes crates that have passed their expiry.
     *
     * <p>Unlike the prototype, which left spoiled crates occupying storage until a player noticed
     * and clicked each one individually, this runs on a timer. A warehouse left alone does not
     * silently fill up with rot the owner has to clear by hand before they can produce again.</p>
     *
     * @return how many were removed
     */
    public static int sweepSpoiled(Warehouse warehouse, long now) {
        List<Crate> spoiled = warehouse.spoiled(now);
        for (Crate crate : spoiled) {
            // A crate committed to a run in flight is not on the shelf and is not swept.
            if (!crate.reserved()) {
                warehouse.removeCrate(crate.id());
            }
        }
        return spoiled.size();
    }

    /* -------------------------------------------------------------- valuation */

    /**
     * What one crate would fetch right now: its frozen base value, adjusted for the live market and
     * for how much shelf life it has left.
     */
    public static int currentValue(Crate crate, MarketState market, long now) {
        double value = crate.baseValue()
                * market.index(crate.cargo())
                * crate.freshness(now);
        return (int) Math.floor(value);
    }

    /** Total shelf value, for the terminal header. Reserved crates are excluded - they have left. */
    public static long totalValue(Warehouse warehouse, MarketState market, long now) {
        long total = 0L;
        for (Crate crate : warehouse.crates()) {
            if (!crate.reserved() && !crate.isSpoiled(now)) {
                total += currentValue(crate, market, now);
            }
        }
        return total;
    }

    /* ---------------------------------------------------------------- counting */

    public static int countFresh(Warehouse warehouse, long now) {
        int count = 0;
        for (Crate crate : warehouse.crates()) {
            if (!crate.isSpoiled(now)) {
                count++;
            }
        }
        return count;
    }

    /** Crates within a day of spoiling - what the overview screen warns about. */
    public static int countExpiringSoon(Warehouse warehouse, long now) {
        int count = 0;
        for (Crate crate : warehouse.crates()) {
            long left = crate.millisRemaining(now);
            if (left > 0L && left <= DAY_MILLIS) {
                count++;
            }
        }
        return count;
    }
}
