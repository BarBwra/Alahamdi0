package com.barbwra.mlum.warehouse.mission;

import java.util.List;

/**
 * What a convoy is worth, and why.
 *
 * <p>Built once, on the server, while the player is looking at the selling preview. The number they
 * see and the number they are paid come from this same object - it is stored on the {@link Mission}
 * at dispatch and read back at settlement, so nothing that happens in between (someone logging in,
 * the market moving, the event ending) can change what the run pays.</p>
 *
 * <p><b>There is no convoy-size multiplier.</b> It was removed on request. Carrying more crates
 * already pays more, because it is more cargo; paying a <i>rate</i> bonus on top of that just
 * punished anyone who sold in small batches, which is a decision the player should be free to make
 * for their own reasons - a smaller run is faster to reach the drop with and cheaper to lose.</p>
 *
 * <p>{@link #breakdown()} is the human-readable derivation, written to the mission record so a
 * disputed payout can be explained later without re-deriving anything.</p>
 */
public record PayoutQuote(long cargoValue,
                          double routeMultiplier,
                          double populationMultiplier,
                          double eventMultiplier,
                          int total,
                          List<String> breakdown) {

    /** One line per multiplier, for the selling screen's profit preview. */
    public String breakdownText() {
        return String.join("\n", breakdown);
    }
}
