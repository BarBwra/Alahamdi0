package com.barbwra.mlum.rank;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CRanks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Who holds which rank, and what the ladder looks like.
 *
 * <h2>Granted, never bought in game</h2>
 * <p>Nothing here spends the server's currency. Ranks are sold outside the game and handed over with
 * {@code /mlum_inventory rank set}, which is what a donation store's webhook or an operator runs.
 * The shop screen is a catalogue.</p>
 *
 * <p>Stored in the player's persistent data beside the wallet and the worn pack, so it survives
 * death and relog and needs no save file keyed by a UUID that changes when somebody is renamed.</p>
 */
public final class RankService {

    private RankService() {
    }

    private static final String KEY = "MlumRank";

    /* ------------------------------------------------------------------ the ladder */

    private static List<? extends String> parsedFrom;
    private static List<Rank> parsed = List.of();

    /**
     * The configured ladder, lowest first.
     *
     * <p>Re-parsed only when the underlying config list object changes, the same trick the skills
     * use - this is read once per frame by the top bar.</p>
     */
    public static List<Rank> ranks() {
        List<? extends String> raw = MlumConfig.rankLines();
        if (raw == parsedFrom) {
            return parsed;
        }
        List<Rank> out = new ArrayList<>();
        for (String line : raw) {
            String[] p = line.split("\\|", -1);
            if (p.length < 5 || p[0].isBlank()) {
                MlumInventory.LOGGER.warn("[{}] ignoring rank '{}' - expected "
                        + "id|name|colour|price|blurb[|perk;perk;perk]", MlumInventory.MODID, line);
                continue;
            }
            int color;
            try {
                color = Integer.parseInt(p[2].trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] rank '{}' has no readable colour - using bone",
                        MlumInventory.MODID, p[0]);
                color = 0xECE6D4;
            }
            List<String> perks = new ArrayList<>();
            if (p.length > 5 && !p[5].isBlank()) {
                for (String perk : p[5].split(";")) {
                    if (!perk.isBlank()) {
                        perks.add(perk.trim());
                    }
                }
            }
            out.add(new Rank(p[0].trim(), p[1].trim(), color, p[3].trim(), p[4].trim(), List.copyOf(perks)));
        }
        parsedFrom = raw;
        parsed = List.copyOf(out);
        return parsed;
    }

    @Nullable
    public static Rank byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Rank r : ranks()) {
            if (r.id().equalsIgnoreCase(id)) {
                return r;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ who holds what */

    public static String rankIdOf(Player player) {
        if (player == null) {
            return Rank.DEFAULT_ID;
        }
        String id = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getString(KEY);
        return id.isEmpty() ? Rank.DEFAULT_ID : id;
    }

    /** The player's rank, falling back to the ladder's first entry and then to a built-in default. */
    public static Rank rankOf(Player player) {
        Rank held = byId(rankIdOf(player));
        if (held != null) {
            return held;
        }
        List<Rank> all = ranks();
        return all.isEmpty() ? Rank.fallback() : all.get(0);
    }

    public static void setRank(ServerPlayer player, String id) {
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (id == null || id.isBlank() || Rank.DEFAULT_ID.equalsIgnoreCase(id)) {
            persisted.remove(KEY);
        } else {
            persisted.putString(KEY, id);
        }
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        sync(player);
    }

    /* ------------------------------------------------------------------ syncing */

    /** The whole catalogue plus this player's own rank - see {@link S2CRanks} for why it is one packet. */
    public static void sync(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new S2CRanks(ranks(), rankIdOf(player), MlumConfig.moneyPackLines(), MlumConfig.storeNote()));
    }
}
