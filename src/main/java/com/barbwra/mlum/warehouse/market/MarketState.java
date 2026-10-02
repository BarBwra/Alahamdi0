package com.barbwra.mlum.warehouse.market;

import com.barbwra.mlum.warehouse.WarehouseConfig;
import com.barbwra.mlum.warehouse.core.CargoType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

/**
 * The contraband market: one price index per cargo type, plus the history the terminal graphs.
 *
 * <p>Each sample moves an index by three forces:</p>
 * <ol>
 *   <li><b>Mean reversion</b> toward 1.0, so no type stays cheap or expensive forever.</li>
 *   <li><b>Noise</b>, a small random walk, so the line is never flat and reading it is a habit
 *       rather than a one-off check.</li>
 *   <li><b>Supply pressure</b>, driven by how much of that type the whole server has delivered
 *       recently. Flooding weapons crashes the weapons index and it takes hours to recover.</li>
 * </ol>
 *
 * <p>The third force is the point. It is what turns the market graph from decoration into
 * information, and it makes diversifying a strategy instead of flavour text: the prototype rolled
 * a crate's worth randomly at craft time and never looked at it again, so there was nothing for a
 * player to read, time or react to.</p>
 *
 * <p>Volume decays by half each sample rather than being tracked in timestamped buckets. It gives
 * the same "recent deliveries matter more" shape in one float per cargo type, and it cannot drift
 * out of sync with the clock across a restart.</p>
 */
public final class MarketState {

    /** 96 samples at the default 10-minute cadence is 16 hours of history. */
    public static final int HISTORY = 96;

    private static final float VOLUME_DECAY = 0.5F;
    /** Deliveries in one sample window that constitute "full" supply pressure. */
    private static final float VOLUME_NORMALIZER = 20.0F;

    private final Map<CargoType, Double> index = new EnumMap<>(CargoType.class);
    private final Map<CargoType, float[]> history = new EnumMap<>(CargoType.class);
    private final Map<CargoType, Float> volume = new EnumMap<>(CargoType.class);

    /** Ring cursor - the slot the next sample will be written to. */
    private int cursor;
    private int filled;
    private long lastSampleAt;

    private final Random random = new Random();

    public MarketState() {
        for (CargoType type : CargoType.values()) {
            index.put(type, 1.0D);
            history.put(type, new float[HISTORY]);
            volume.put(type, 0.0F);
        }
    }

    /* ------------------------------------------------------------------ reads */

    public double index(CargoType type) {
        return WarehouseConfig.marketEnabled() ? index.getOrDefault(type, 1.0D) : 1.0D;
    }

    public long lastSampleAt() {
        return lastSampleAt;
    }

    /**
     * History oldest-first, ready to plot left to right. Length is {@link #HISTORY} once the ring
     * has wrapped, shorter before that.
     */
    public float[] series(CargoType type) {
        float[] ring = history.get(type);
        int count = Math.min(filled, HISTORY);
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = ring[(cursor - count + i + HISTORY) % HISTORY];
        }
        return out;
    }

    /* ----------------------------------------------------------------- writes */

    /** Called when a delivery settles. Feeds supply pressure at the next sample. */
    public void recordDelivery(CargoType type, int crates) {
        volume.merge(type, (float) crates, Float::sum);
    }

    /**
     * Advances the market if a sample is due.
     *
     * <p>Catches up at most {@link #HISTORY} samples after downtime, so a server that was off for a
     * week does not spend a tick running ten thousand iterations - and does not hand players a
     * fully mean-reverted market either, which would erase whatever the last session did to it.</p>
     *
     * @return true when at least one sample was taken, i.e. the graph changed
     */
    public boolean tick(long now) {
        if (!WarehouseConfig.marketEnabled()) {
            return false;
        }
        long interval = WarehouseConfig.marketTickMinutes() * 60_000L;
        if (lastSampleAt == 0L) {
            lastSampleAt = now;
            sample();
            return true;
        }
        if (now - lastSampleAt < interval) {
            return false;
        }

        long due = Math.min(HISTORY, (now - lastSampleAt) / interval);
        for (long i = 0; i < due; i++) {
            sample();
        }
        lastSampleAt = now;
        return true;
    }

    private void sample() {
        double reversion = WarehouseConfig.marketReversion();
        double noise = WarehouseConfig.marketNoise();
        double pressure = WarehouseConfig.marketSupplyPressure();
        double floor = WarehouseConfig.marketFloor();
        double ceiling = WarehouseConfig.marketCeiling();

        for (CargoType type : CargoType.values()) {
            double current = index.get(type);
            float vol = volume.get(type);

            double supply = pressure * Math.min(1.0F, vol / VOLUME_NORMALIZER);
            double next = current
                    + reversion * (1.0D - current)
                    + random.nextGaussian() * noise
                    - supply;

            next = Mth.clamp(next, floor, ceiling);
            index.put(type, next);
            volume.put(type, vol * VOLUME_DECAY);
            history.get(type)[cursor] = (float) next;
        }

        cursor = (cursor + 1) % HISTORY;
        filled++;
    }

    /* -------------------------------------------------------------------- nbt */

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("cursor", cursor);
        tag.putInt("filled", filled);
        tag.putLong("last", lastSampleAt);
        for (CargoType type : CargoType.values()) {
            tag.putDouble(type.id() + "_index", index.get(type));
            tag.putFloat(type.id() + "_volume", volume.get(type));
            tag.put(type.id() + "_history", newFloatList(history.get(type)));
        }
        return tag;
    }

    private static net.minecraft.nbt.ListTag newFloatList(float[] values) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (float value : values) {
            list.add(net.minecraft.nbt.FloatTag.valueOf(value));
        }
        return list;
    }

    public static MarketState load(CompoundTag tag) {
        MarketState state = new MarketState();
        state.cursor = tag.getInt("cursor");
        state.filled = tag.getInt("filled");
        state.lastSampleAt = tag.getLong("last");
        for (CargoType type : CargoType.values()) {
            if (tag.contains(type.id() + "_index")) {
                state.index.put(type, tag.getDouble(type.id() + "_index"));
            }
            state.volume.put(type, tag.getFloat(type.id() + "_volume"));

            net.minecraft.nbt.ListTag list = tag.getList(type.id() + "_history", net.minecraft.nbt.Tag.TAG_FLOAT);
            float[] ring = state.history.get(type);
            for (int i = 0; i < Math.min(list.size(), HISTORY); i++) {
                ring[i] = list.getFloat(i);
            }
        }
        return state;
    }
}
