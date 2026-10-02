package com.barbwra.mlum.skill;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.bag.WalletService;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CSkills;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The skills tab's rules: what is for sale, what each player owns, and buying.
 *
 * <p>Levels live in the player's persistent data, beside the bag layout and the worn backpack, so
 * they survive death and relog without a save file of their own. The mod sells and remembers;
 * what a level actually does is decided by the server's own scripts, told through
 * {@code buyCommand} - the same contract the quest rewards use.</p>
 */
public final class SkillService {

    private SkillService() {
    }

    public static final int MAX_LEVEL = 3;
    private static final String KEY = "MlumSkills";
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /**
     * One skill for sale, parsed from the config.
     *
     * <p>{@code soon} is a card that is drawn and cannot be bought - a placeholder for a perk that is
     * planned but not built. It exists so the grid keeps its shape while the list is being worked on,
     * rather than reflowing every time one is added.</p>
     */
    public record Skill(String id, String name, String description, String icon, String[] effects,
                        long[] prices, boolean soon) {
    }

    private static List<? extends String> parsedFrom;
    private static List<Skill> parsed = List.of();

    public static List<Skill> skills() {
        List<? extends String> raw = MlumConfig.skillLines();
        if (raw == parsedFrom) {
            return parsed;
        }
        List<Skill> out = new ArrayList<>();
        for (String line : raw) {
            String[] p = line.split("\\|", -1);
            if (p.length < 10 || p[0].isBlank()) {
                MlumInventory.LOGGER.warn("[{}] ignoring skill '{}' - expected "
                        + "id|name|description|icon|level1|level2|level3|price1|price2|price3[|soon]",
                        MlumInventory.MODID, line);
                continue;
            }
            try {
                // the eleventh field is optional, so every line written before it existed still reads
                boolean soon = p.length > 10 && Boolean.parseBoolean(p[10].trim());
                out.add(new Skill(p[0].trim(), p[1].trim(), p[2].trim(), p[3].trim(),
                        new String[]{p[4].trim(), p[5].trim(), p[6].trim()},
                        new long[]{Long.parseLong(p[7].trim()), Long.parseLong(p[8].trim()), Long.parseLong(p[9].trim())},
                        soon));
            } catch (NumberFormatException bad) {
                MlumInventory.LOGGER.warn("[{}] ignoring skill '{}' - a price is not a number", MlumInventory.MODID, line);
            }
        }
        parsedFrom = raw;
        parsed = List.copyOf(out);
        return parsed;
    }

    @Nullable
    public static Skill byId(String id) {
        for (Skill s : skills()) {
            if (s.id().equalsIgnoreCase(id)) {
                return s;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ storage */

    private static CompoundTag store(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (!persisted.contains(KEY, Tag.TAG_COMPOUND)) {
            persisted.put(KEY, new CompoundTag());
        }
        return persisted.getCompound(KEY);
    }

    public static int level(Player player, String id) {
        return Math.max(0, Math.min(MAX_LEVEL, store(player).getInt(id)));
    }

    public static void setLevel(Player player, String id, int level) {
        int clamped = Math.max(0, Math.min(MAX_LEVEL, level));
        if (clamped == 0) {
            store(player).remove(id);
        } else {
            store(player).putInt(id, clamped);
        }
    }

    /** How many skills this player has at the top level. */
    public static int maxed(Player player) {
        int n = 0;
        for (Skill s : skills()) {
            if (level(player, s.id()) >= MAX_LEVEL) {
                n++;
            }
        }
        return n;
    }

    /* ------------------------------------------------------------------ buying */

    public static void buy(ServerPlayer player, String id) {
        Skill skill = byId(id);
        if (skill == null) {
            sync(player);
            return;
        }
        if (skill.soon()) {
            Feedback.bad(player, "{b}" + skill.name() + "{/b} لسه ما نزلت");
            sync(player);
            return;
        }
        int level = level(player, skill.id());
        if (level >= MAX_LEVEL) {
            Feedback.bad(player, "{b}" + skill.name() + "{/b} مكتملة");
            sync(player);
            return;
        }
        if (level == MAX_LEVEL - 1 && maxed(player) >= MlumConfig.maxedSkills()) {
            Feedback.bad(player, "وصلت الحد · تقدر تكمّل " + Feedback.num(MlumConfig.maxedSkills()) + " مهارات بس");
            sync(player);
            return;
        }
        long price = skill.prices()[level];
        if (!WalletService.take(player, price)) {
            long short_ = Math.max(0L, price - WalletService.balance(player));
            Feedback.bad(player, "فلوسك ما تكفي · ناقصك " + Feedback.num(short_));
            sync(player);
            return;
        }
        setLevel(player, skill.id(), level + 1);
        runCommand(player, skill.id(), level + 1);
        Feedback.ok(player, "اشتريت {b}" + skill.name() + "{/b} · المستوى " + Feedback.num(level + 1));
        sync(player);
    }

    /**
     * Drops a skill back to level 0 for a fee, freeing the maxed slot it was holding.
     *
     * <p><b>A fee, not a refund.</b> What was spent on the levels is gone; this buys the right to
     * change your mind. Making it pay back would turn the "only three may be maxed" rule into a
     * free respec, and the rule is the whole point of the tab.</p>
     */
    public static void drop(ServerPlayer player, String id) {
        Skill skill = byId(id);
        if (skill == null || skill.soon()) {
            sync(player);
            return;
        }
        int level = level(player, skill.id());
        if (level <= 0) {
            Feedback.bad(player, "ما عندك {b}" + skill.name() + "{/b} أصلاً");
            sync(player);
            return;
        }
        long cost = MlumConfig.skillRefundCost();
        if (!WalletService.take(player, cost)) {
            long missing = Math.max(0L, cost - WalletService.balance(player));
            Feedback.bad(player, "حذف المهارة يكلف " + Feedback.num(cost) + " · ناقصك " + Feedback.num(missing));
            sync(player);
            return;
        }
        setLevel(player, skill.id(), 0);
        runCommand(player, skill.id(), 0);
        Feedback.ok(player, "حذفت {b}" + skill.name() + "{/b} · دفعت " + Feedback.num(cost));
        sync(player);
    }

    /** The console hook, so the server's scripts apply what the level does. */
    public static void runCommand(ServerPlayer player, String skill, int level) {
        String template = MlumConfig.skillCommand();
        MinecraftServer server = player.getServer();
        if (template == null || template.isBlank() || server == null) {
            return;
        }
        String name = player.getGameProfile().getName();
        if (!SAFE_NAME.matcher(name).matches()) {
            name = player.getUUID().toString();
        }
        String safeSkill = skill.replaceAll("[^A-Za-z0-9_\\-]", "");
        String command = template.replace("%player%", name).replace("%skill%", safeSkill)
                .replace("%level%", String.valueOf(level));
        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
        server.getCommands().performPrefixedCommand(console, command);
    }

    /* ------------------------------------------------------------------ syncing */

    public static void sync(ServerPlayer player) {
        if (player == null || player.connection == null) {
            return;
        }
        List<S2CSkills.Entry> entries = new ArrayList<>();
        for (Skill s : skills()) {
            entries.add(new S2CSkills.Entry(s.id(), s.name(), s.description(), s.icon(),
                    List.of(s.effects()), s.prices().clone(), level(player, s.id()), s.soon()));
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSkills(entries, MlumConfig.maxedSkills(), MlumConfig.skillRefundCost()));
    }
}
