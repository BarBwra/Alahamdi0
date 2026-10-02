package com.barbwra.mlum.warehouse;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Server authorisation. The mod does nothing on a server that has not been given a key.
 *
 * <p><b>How it works.</b> The server operator puts a key in {@code config/mwh-server.toml}. On
 * start-up that key is salted, hashed with SHA-256, and compared against the digests baked into
 * {@link #AUTHORIZED} below. If it does not match, every entry point - opening a terminal, starting
 * an assembly line, dispatching a run, every command - refuses. Clients need no key at all: the
 * terminal is server-driven, so a player with the jar and no authorised server has an inert mod.</p>
 *
 * <p><b>What this is and is not.</b> This is a deterrent, not DRM. The check runs inside a jar the
 * other party possesses, so anyone willing to decompile it can delete this class and rebuild -
 * that is true of every offline licence check in every Java mod, and no amount of obfuscation
 * changes it. What it does buy is that casual redistribution fails: someone who copies the jar onto
 * their own server gets a warehouse that will not open, with a log line telling them why, and they
 * have to actively tamper with your code to get past it. Making that step deliberate rather than
 * accidental is the realistic goal.</p>
 *
 * <p><b>If you want this to be genuinely unbreakable</b> the check has to live somewhere the other
 * party does not control - the server calls out to a machine you own and will not function without
 * a response. That needs a small web endpoint and makes your mod dependent on it being up. Say the
 * word and I will build it; the offline gate below is the version that needs nothing from you.</p>
 *
 * <p>To issue a new key: pick a string, compute {@code SHA-256(SALT + "|" + key)}, and add the hex
 * digest to {@link #AUTHORIZED}. Keys themselves never appear in the source, so shipping this file
 * does not leak them.</p>
 */
public final class License {

    private License() {
    }

    private static final String SALT = "mwh::barbwra::2026::v1";

    /**
     * SHA-256 digests of the keys allowed to run this mod.
     *
     * <p>The first is the production key for Mlife. The second is a local development key so the
     * mod still runs in a test world without the live key having to be on a dev machine.</p>
     */
    private static final List<String> AUTHORIZED = List.of(
            "b09099656cd2d1ed48c5776b2fe2764b033e42d1061ce3791dd9217a4800c0a9",
            "e597d45366f51579006a50900240ee8f751e7c86ff6ed459daaa3fb726b6ad0b"
    );

    private static boolean checked;
    private static boolean valid;

    /**
     * Validates the configured key. Cached after the first call - the digest cannot change without
     * a restart, and this is consulted on every terminal action.
     */
    public static boolean isValid() {
        if (!checked) {
            checked = true;
            valid = AUTHORIZED.contains(digest(WarehouseConfig.licenseKey()));
            if (valid) {
                WarehouseMod.LOGGER.info("[mwh] Licence accepted. Warehouse systems online.");
            } else {
                WarehouseMod.LOGGER.error("[mwh] ================================================");
                WarehouseMod.LOGGER.error("[mwh] NO VALID LICENCE - every warehouse feature is off.");
                WarehouseMod.LOGGER.error("[mwh] Set 'licenseKey' in config/mwh-server.toml.");
                WarehouseMod.LOGGER.error("[mwh] This mod is authored by BarBwra and is licensed");
                WarehouseMod.LOGGER.error("[mwh] per-server. Contact the author for a key.");
                WarehouseMod.LOGGER.error("[mwh] ================================================");
            }
        }
        return valid;
    }

    /** Re-runs the check, for use after a config reload. */
    public static void invalidate() {
        checked = false;
    }

    private static String digest(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest((SALT + "|" + key.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString().toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec; if it is missing, fail closed.
            return "";
        }
    }

    /**
     * Gate for anything a player can trigger. Tells them once, plainly, rather than failing silently.
     *
     * @return true when the action may proceed
     */
    public static boolean gate(ServerPlayer player) {
        if (isValid()) {
            return true;
        }
        if (player != null) {
            player.sendSystemMessage(Component.literal(
                    "§c✖ §fنظام المستودع غير مفعّل على هذا السيرفر."));
            player.sendSystemMessage(Component.literal(
                    "§8Warehouse system is not licensed for this server. §7— BarBwra"));
        }
        return false;
    }
}
