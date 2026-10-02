package com.barbwra.mlum.client;

import com.barbwra.mlum.network.S2CRanks;
import com.barbwra.mlum.rank.Rank;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** The rank ladder and this player's place on it, as last sent by the server. */
@OnlyIn(Dist.CLIENT)
public final class ClientRanks {

    private ClientRanks() {
    }

    /** One row of the money store, parsed from {@code label|amount|price}. */
    public record Pack(String label, long amount, String price) {
    }

    private static volatile List<Rank> ranks = List.of();
    private static volatile List<Pack> packs = List.of();
    private static volatile String held = Rank.DEFAULT_ID;
    private static volatile String note = "";

    public static void accept(S2CRanks msg) {
        ranks = msg.ranks();
        held = msg.held();
        note = msg.note();
        List<Pack> out = new ArrayList<>();
        for (String line : msg.moneyPacks()) {
            String[] p = line.split("\\|", -1);
            if (p.length < 3) {
                continue;
            }
            long amount;
            try {
                amount = Long.parseLong(p[1].trim());
            } catch (NumberFormatException bad) {
                continue;
            }
            out.add(new Pack(p[0].trim(), amount, p[2].trim()));
        }
        packs = List.copyOf(out);
    }

    public static List<Rank> ranks() {
        return ranks;
    }

    public static List<Pack> packs() {
        return packs;
    }

    public static String note() {
        return note;
    }

    /** Where the player sits on the ladder, or -1 when the ladder has not arrived. */
    public static int heldIndex() {
        for (int i = 0; i < ranks.size(); i++) {
            if (ranks.get(i).id().equalsIgnoreCase(held)) {
                return i;
            }
        }
        return ranks.isEmpty() ? -1 : 0;
    }

    @Nullable
    public static Rank heldRank() {
        int i = heldIndex();
        return i < 0 ? null : ranks.get(i);
    }

    public static void clear() {
        ranks = List.of();
        packs = List.of();
        held = Rank.DEFAULT_ID;
        note = "";
    }
}
