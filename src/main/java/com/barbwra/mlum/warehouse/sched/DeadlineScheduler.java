package com.barbwra.mlum.warehouse.sched;

import com.barbwra.mlum.warehouse.WarehouseMod;

import java.util.PriorityQueue;

/**
 * One priority queue for every timed thing in the mod.
 *
 * <p>Assembly completions, mission leaks, hard deadlines, grace expiry, spoilage sweeps and market
 * samples all register here. Each server tick the queue is drained of whatever is due - and only
 * whatever is due. The cost is proportional to the number of events actually firing, not to the
 * number of things being waited on.</p>
 *
 * <p>The prototype instead ran two repeating blocks: one every second that walked every active
 * mission, and one every five seconds that walked every craft on the server and decremented a
 * counter on each. Both scanned the entire table whether or not anything was due, both mutated the
 * list they were iterating, and both froze whenever the server was down. This replaces all of it.</p>
 *
 * <p><b>The queue is deliberately not persisted.</b> Every deadline it holds is derived from an
 * absolute timestamp that already lives in {@code WarehouseData} - a craft's {@code completesAt}, a
 * mission's {@code expiresAt}. On boot the services walk their own persisted state and re-arm what
 * they need, which means the durable record is the timestamp and the queue is just an index over
 * it. Persisting the queue too would create a second source of truth that could disagree with the
 * first.</p>
 *
 * <p>Server thread only. No synchronisation, by design.</p>
 */
public final class DeadlineScheduler {

    private static final DeadlineScheduler INSTANCE = new DeadlineScheduler();

    public static DeadlineScheduler get() {
        return INSTANCE;
    }

    private DeadlineScheduler() {
    }

    /** A scheduled callback. Cancel it if the thing it was waiting on stops being relevant. */
    public static final class Handle implements Comparable<Handle> {

        final long at;
        final String label;
        final Runnable action;
        private boolean cancelled;

        Handle(long at, String label, Runnable action) {
            this.at = at;
            this.label = label;
            this.action = action;
        }

        public void cancel() {
            this.cancelled = true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public int compareTo(Handle other) {
            return Long.compare(at, other.at);
        }
    }

    private final PriorityQueue<Handle> queue = new PriorityQueue<>();

    /** Fires {@code action} the first tick at or after {@code epochMillis}. */
    public Handle at(long epochMillis, String label, Runnable action) {
        Handle handle = new Handle(epochMillis, label, action);
        queue.add(handle);
        return handle;
    }

    public Handle in(long delayMillis, String label, Runnable action) {
        return at(System.currentTimeMillis() + delayMillis, label, action);
    }

    /**
     * Drains everything due.
     *
     * <p>A callback that throws is logged and swallowed. One malformed mission must not be able to
     * stop every other player's crates from finishing - the prototype's tick loop had exactly that
     * failure mode, where a single unresolvable player UUID produced an error every second and
     * blocked the rest of the pass.</p>
     */
    public void tick(long now) {
        while (!queue.isEmpty() && queue.peek().at <= now) {
            Handle handle = queue.poll();
            if (handle.isCancelled()) {
                continue;
            }
            try {
                handle.action.run();
            } catch (Exception e) {
                WarehouseMod.LOGGER.error("[mwh] Deadline '{}' failed: {}", handle.label, e.toString(), e);
            }
        }
    }

    /** Dropped on server stop so a single-player world reloading does not double-fire. */
    public void clear() {
        queue.clear();
    }

    public int pending() {
        return queue.size();
    }
}
