package com.barbwra.mlum.level;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Progression, built on vanilla experience rather than beside it.
 *
 * <p><b>Why this was rewritten.</b> The previous version kept its own point total in a Forge
 * persistent tag and <i>mirrored</i> the resulting level onto {@code experienceLevel} so that
 * Skript could read it. Points were the master copy and the vanilla field was a copy, which meant
 * every write from outside the mod - {@code /xp}, a plugin, an enchantment table - was a race. Worse,
 * login called a sync that recomputed {@code experienceLevel} from the stored points
 * <b>unconditionally</b>, so any outside change that had not yet been adopted was silently thrown
 * away the next time the player joined. That is the whole of the "set his level, he relogs, it is
 * back to the old one" bug, and it is not fixable while two numbers are allowed to disagree.</p>
 *
 * <p><b>So now there is one number.</b> {@code experienceLevel} is the level - not a mirror of one.
 * Vanilla saves it, vanilla syncs it to the client, {@code /xp} edits it, and Skript reads it,
 * all without this mod being involved. There is nothing left to reconcile.</p>
 *
 * <p><b>Difficulty comes from the rate, not the curve.</b> Vanilla's level costs are untouched, so
 * enchanting and anvils behave exactly as players expect; what changes is how much experience the
 * world hands out, scaled in one place by {@link XpBoost}. Turning the rate down is what makes
 * levelling hard, and it is also what leaves room for a 2x bonus to feel like one.</p>
 *
 * <p>The only thing still stored per player is which reward tiers have already paid out - that is a
 * record of what happened, not a second copy of the level, so it cannot contradict anything.</p>
 */
public final class PlayerLevel {

    private PlayerLevel() {
    }

    /** Highest reward tier already paid out, so a reward never fires twice. */
    private static final String KEY_CLAIMED = "mlum:level_claimed";

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            data.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return data.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /* ---------------------------------------------------------------- reading */

    public static int level(Player player) {
        return player.experienceLevel;
    }

    /** 0..1 through the current level - vanilla's own field. */
    public static float progress(Player player) {
        return player.experienceProgress;
    }

    /** Experience still owed for this level, for the readout. */
    public static int costOfNext(Player player) {
        return player.getXpNeededForNextLevel();
    }

    public static int intoLevel(Player player) {
        return Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /* ---------------------------------------------------------------- writing */

    /**
     * Awards experience through vanilla's own path.
     *
     * <p>Deliberately <i>not</i> pre-multiplied here. {@code giveExperiencePoints} fires
     * {@code PlayerXpEvent.XpChange}, and that is where every multiplier is applied - so a kill
     * reward, a mined ore and a furnace emptying its orbs are all scaled by the same rule, with no
     * award site able to forget.</p>
     */
    public static void award(ServerPlayer player, int amount, String reason) {
        if (amount == 0) {
            return;
        }
        player.giveExperiencePoints(amount);
        MlumInventory.LOGGER.debug("[{}] {} {}{} xp ({})", MlumInventory.MODID,
                player.getGameProfile().getName(), amount > 0 ? "+" : "", amount, reason);
    }

    /**
     * Takes experience away, without letting a player fall into permanent debt.
     *
     * <p>Vanilla will happily drive the level negative; a player who dies to another player
     * repeatedly would end up unable to climb out, which stops being a penalty and starts being a
     * reason to quit.</p>
     */
    public static void penalise(ServerPlayer player, int amount) {
        if (amount <= 0) {
            return;
        }
        player.giveExperiencePoints(-amount);
        if (player.experienceLevel < 0) {
            player.experienceLevel = 0;
            player.experienceProgress = 0.0F;
            player.totalExperience = 0;
        }
    }

    /* ---------------------------------------------------------------- rewards */

    /**
     * Fires every reward tier crossed on the way up to {@code newLevel}.
     *
     * <p>Iterating the whole span matters: one large award can cross several tiers at once, and
     * skipping the ones in between would silently swallow rewards the player earned.</p>
     */
    public static void payRewards(ServerPlayer player, int newLevel) {
        int claimed = root(player).getInt(KEY_CLAIMED);
        if (newLevel <= claimed) {
            return;
        }
        for (MlumConfig.LevelReward reward : MlumConfig.levelRewards()) {
            if (reward.level() <= claimed || reward.level() > newLevel) {
                continue;
            }
            if (!reward.command().isBlank()) {
                runReward(player, reward);
            }
            if (!reward.description().isBlank()) {
                // Shape only - chat reorders it itself, and autoDisplay would flip it.
                player.sendSystemMessage(com.barbwra.mlum.util.ArabicChat.of(
                        "§6★ §f" + reward.description()));
            }
        }
        root(player).putInt(KEY_CLAIMED, newLevel);
    }

    /**
     * Runs a reward's command from the console.
     *
     * <p>The mod hands out nothing itself - the same contract the quest claim hook uses. Whatever
     * the reward actually is lives in Skript, so it can be redesigned without touching this.</p>
     */
    private static void runReward(ServerPlayer player, MlumConfig.LevelReward reward) {
        String command = reward.command().replace("%player%", player.getGameProfile().getName());
        try {
            player.server.getCommands().performPrefixedCommand(
                    player.server.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    command);
        } catch (Exception failed) {
            MlumInventory.LOGGER.error("[{}] level reward command '{}' failed: {}",
                    MlumInventory.MODID, command, failed.toString());
        }
    }

    /* ------------------------------------------------------------------- sync */

    /**
     * Sends the reward track to the client.
     *
     * <p>The level and the progress bar are <b>not</b> in here any more - the client reads those
     * straight off its own player, because vanilla already keeps them in step. Only the configured
     * tier list has to travel, and only because it lives in the server config.</p>
     */
    public static void sync(ServerPlayer player) {
        ModNetwork.sendLevelState(player, player.totalExperience, player.experienceLevel,
                intoLevel(player), costOfNext(player), track(), rules(player));
    }

    /** Everyone online, after something that changes the rules for all - the global boost. */
    public static void syncAll(net.minecraft.server.MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    /** The level tab's "how points are earned" list, from the live config. */
    private static java.util.List<com.barbwra.mlum.network.S2CLevelState.Rule> rules(ServerPlayer player) {
        java.util.List<com.barbwra.mlum.network.S2CLevelState.Rule> out = new java.util.ArrayList<>();
        int kill = MlumConfig.pointsPerMonster();
        if (kill > 0) {
            out.add(new com.barbwra.mlum.network.S2CLevelState.Rule("+" + kill, 0, "قتل زومبي", false));
        }
        // mining, one line per ore - deepslate variants fold into their plain twin
        java.util.Map<String, Integer> ores = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, Integer> e : MlumConfig.minePoints().entrySet()) {
            String name = oreName(e.getKey());
            if (e.getValue() > 0 && !ores.containsKey(name)) {
                ores.put(name, e.getValue());
            }
        }
        int shown = 0;
        for (java.util.Map.Entry<String, Integer> e : ores.entrySet()) {
            if (shown++ >= 2) {
                break;
            }
            out.add(new com.barbwra.mlum.network.S2CLevelState.Rule("+" + e.getValue(), 0, "تعدين " + e.getKey(), false));
        }
        int penalty = MlumConfig.playerKillPenalty();
        if (penalty > 0) {
            out.add(new com.barbwra.mlum.network.S2CLevelState.Rule("-" + penalty, 1, "قتل لاعب", false));
        }
        double vip = MlumConfig.vipXpFactor();
        if (vip > 1.0D) {
            out.add(new com.barbwra.mlum.network.S2CLevelState.Rule("×" + trim(vip), 2,
                    XpBoost.isVip(player) ? "مع VIP · مفعّل لك" : "مع VIP (إلا قتل اللاعبين)", false));
        }
        boolean global = XpBoost.globalActive();
        double factor = global ? XpBoost.globalFactor() : MlumConfig.globalBoostFactor();
        out.add(new com.barbwra.mlum.network.S2CLevelState.Rule("×" + trim(factor), 3,
                global ? "مضاعفة عامة · شغالة الحين" : "مضاعفة عامة · متوقفة الحين", !global));
        return out;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static String oreName(String blockId) {
        String path = blockId.contains(":") ? blockId.substring(blockId.indexOf(':') + 1) : blockId;
        path = path.replace("deepslate_", "").replace("nether_", "");
        if (path.contains("ancient_debris")) {
            return "حطام قديم";
        }
        if (path.contains("iron")) {
            return "حديد";
        }
        if (path.contains("gold")) {
            return "ذهب";
        }
        if (path.contains("diamond")) {
            return "ألماس";
        }
        if (path.contains("emerald")) {
            return "زمرد";
        }
        if (path.contains("copper")) {
            return "نحاس";
        }
        if (path.contains("coal")) {
            return "فحم";
        }
        if (path.contains("lapis")) {
            return "لازورد";
        }
        if (path.contains("redstone")) {
            return "ريدستون";
        }
        if (path.contains("quartz")) {
            return "كوارتز";
        }
        return path.replace("_ore", "").replace('_', ' ');
    }

    /** The configured reward track in the shape the packet carries. */
    private static java.util.List<com.barbwra.mlum.network.S2CLevelState.Tier> track() {
        java.util.List<com.barbwra.mlum.network.S2CLevelState.Tier> out = new java.util.ArrayList<>();
        for (MlumConfig.LevelReward reward : MlumConfig.levelRewards()) {
            out.add(new com.barbwra.mlum.network.S2CLevelState.Tier(
                    reward.level(), reward.icon(), reward.description()));
        }
        return out;
    }

    /**
     * Operator override, in levels.
     *
     * <p>Sets vanilla's own field and nothing else, which is the entire point of the rewrite: there
     * is no second copy to keep in step, so this survives a relog because vanilla saves it.</p>
     */
    public static void setLevel(ServerPlayer player, int level) {
        int target = Math.max(0, level);
        player.setExperienceLevels(target);
        root(player).putInt(KEY_CLAIMED, target);
        sync(player);
    }
}
