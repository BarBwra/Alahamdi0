package com.barbwra.mlum.skill;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.TaczCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps every gun a player is carrying stamped with their تعشيق أكثر level.
 *
 * <p>A sweep rather than a hook on purchase. Guns arrive by a dozen routes - a chest, a kill, a
 * trade, a command, another mod's loot - and a stamp applied only at purchase time would miss all of
 * them, leaving a player who owns the skill holding a gun that does not know it. Nine slots plus the
 * bag, once a second, is nothing; being right in every case is worth more than being clever.</p>
 *
 * <p>It also strips the stamp again when the level drops, which is what lets
 * {@link SkillService#drop} genuinely undo the skill instead of leaving upgraded guns behind.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class SkillGuns {

    private SkillGuns() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        int level = SkillService.level(player, SkillEffects.ATTACHMENTS);
        Inventory inventory = player.getInventory();
        boolean changed = false;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !TaczCompat.isGun(stack)) {
                continue;
            }
            changed |= GunUpgrade.stamp(stack, level);
        }
        if (changed) {
            inventory.setChanged();
        }
    }
}
