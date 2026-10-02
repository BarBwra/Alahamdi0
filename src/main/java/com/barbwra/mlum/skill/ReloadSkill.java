package com.barbwra.mlum.skill;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.function.ToIntFunction;

/**
 * The تعبئة أسرع skill: a gun reloads 20, 50 or 100 percent faster.
 *
 * <h2>How TACZ times a reload</h2>
 * <p>A reload is not a countdown. When it starts TACZ writes down the time, and every tick its gun
 * script asks "how long has this reload been going?" - {@code getReloadTime()} - and compares the
 * answer against the gun pack's numbers: feed the rounds at 1.4 s, finish at 1.87 s. Every gun,
 * every pack, every script goes through that one question.</p>
 *
 * <p>So the skill answers it with a longer time than has really passed. At +100% one real second
 * reads as two, the rounds go in at 0.7 s instead of 1.4 s, and nothing about the gun's data has to
 * change. The client does the same to the reload animation so the hands keep up with the rounds.</p>
 */
public final class ReloadSkill {

    private ReloadSkill() {
    }

    /** How much faster the clock runs, by level. Index 0 is no skill. */
    private static final float[] SPEED = {1.0F, 1.2F, 1.5F, 2.0F};

    /**
     * The skill level of a player as this client knows it - set by the client at start-up, so this
     * class never has to name a client-only type. Zero for anyone but the local player.
     */
    public static volatile ToIntFunction<Entity> clientLevel = e -> 0;

    public static float speed(int level) {
        return SPEED[Math.max(0, Math.min(SPEED.length - 1, level))];
    }

    /** The reload speed of whoever holds the gun, on whichever side is asking. */
    public static float factor(Entity shooter) {
        if (!(shooter instanceof Player player)) {
            return 1.0F;
        }
        int level = player.level().isClientSide
                ? clientLevel.applyAsInt(player)
                : SkillService.level(player, SkillEffects.ATTACHMENTS);
        return speed(level);
    }
}
