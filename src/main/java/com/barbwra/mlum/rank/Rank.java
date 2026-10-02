package com.barbwra.mlum.rank;

import java.util.List;

/**
 * One rank on the ladder, as the config describes it.
 *
 * <h2>The price is a string</h2>
 * <p>Ranks are bought with real money, not with the server's currency, so there is no number to do
 * arithmetic on and no transaction for a Minecraft mod to perform. {@code price} is free text - "50
 * ريال", "$9.99", "مجاني" - and the shop is a <b>display</b>: it shows what exists, what it costs
 * and what it gives, and points at wherever the server actually sells it. A mod that tried to take
 * payment would be both out of its depth and out of its business.</p>
 *
 * <h2>Order is the ladder</h2>
 * <p>The list position <i>is</i> the rank's standing - first is lowest. Nothing sorts by price,
 * because prices are text and because a server may want a rank that costs nothing to sit above one
 * that does.</p>
 */
public record Rank(String id, String name, int color, String price, String blurb, List<String> perks) {

    /** What a player with no rank at all is treated as. */
    public static final String DEFAULT_ID = "default";

    public static Rank fallback() {
        return new Rank(DEFAULT_ID, "Default", 0x9E9B8A, "",
                "الرتبة الأساسية لكل لاعب.", List.of());
    }

    public boolean isDefault() {
        return DEFAULT_ID.equalsIgnoreCase(id);
    }
}
