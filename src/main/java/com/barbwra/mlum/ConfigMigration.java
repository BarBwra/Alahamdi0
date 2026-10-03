package com.barbwra.mlum;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Brings an existing server config up to date, once.
 *
 * <h2>Why this exists</h2>
 * <p>Forge never touches a value that is already in the file. A new default reaches fresh worlds
 * only, so a live server keeps the old number forever unless someone edits the file by hand - and
 * the old skill cards keep describing skills that no longer exist. {@code configVersion} records how
 * far a file has been brought; each step below runs once, then the number moves on.</p>
 *
 * <p>A value is only replaced when it still holds the <i>old default</i>. Anything an admin changed
 * on purpose is left as they set it.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ConfigMigration {

    private ConfigMigration() {
    }

    /** The version a fully up to date file carries. */
    private static final int CURRENT = 1;

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) {
        migrate(event.getConfig());
    }

    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        migrate(event.getConfig());
    }

    private static void migrate(ModConfig config) {
        Object spec = config.getSpec();
        if (spec != MlumConfig.SERVER_SPEC || !MlumConfig.SERVER_SPEC.isLoaded()) {
            return;
        }
        MlumConfig.Server s = MlumConfig.SERVER;
        int from = s.configVersion.get();
        if (from >= CURRENT) {
            return;
        }
        if (from < 1) {
            // 3.10: the search gets quicker and the fast search riskier; downed lasts six minutes;
            // the reload skill's numbers and the new quiet-hands skill card
            replace(s.lootSeconds, 2.0D, 1.5D);
            replace(s.lootFastSeconds, 0.25D, 0.75D);
            replace(s.lootNoiseChance, 0.25D, 0.5D);
            replace(s.downedSeconds, 180, 360);
            s.skills.set(skills(s.skills.get()));
        }
        s.configVersion.set(CURRENT);
        MlumConfig.SERVER_SPEC.save();
        MlumInventory.LOGGER.info("[{}] server config brought from version {} to {}", MlumInventory.MODID, from, CURRENT);
    }

    private static <T> void replace(ForgeConfigSpec.ConfigValue<T> value, T oldDefault, T newDefault) {
        if (Objects.equals(value.get(), oldDefault)) {
            value.set(newDefault);
        }
    }

    private static List<String> skills(List<? extends String> lines) {
        List<String> out = new ArrayList<>();
        boolean quiet = false;
        for (String line : lines) {
            if (line.startsWith("quiet_hands|")) {
                quiet = true;
            }
        }
        for (String line : lines) {
            if (line.startsWith("attachments|")) {
                // the skill itself changed - the old card described attachment slots
                out.add(MlumConfig.ATTACHMENTS_LINE);
            } else if (line.startsWith("scout|") && !line.contains("الفاضي")) {
                out.add(defaultLine("scout|", line));
            } else if (!quiet && line.startsWith("soon_")) {
                out.add(MlumConfig.QUIET_LINE);
                quiet = true;
            } else {
                out.add(line);
            }
        }
        if (!quiet) {
            out.add(MlumConfig.QUIET_LINE);
        }
        return out;
    }

    private static String defaultLine(String prefix, String fallback) {
        for (String line : MlumConfig.SERVER.skills.getDefault()) {
            if (line.startsWith(prefix)) {
                return line;
            }
        }
        return fallback;
    }
}
