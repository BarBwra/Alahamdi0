package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumInventory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is down, asked the same way on either side - and the one rule both sides apply to them.
 *
 * <p>The server keys by UUID and owns the truth; the client keys by entity id, filled from the
 * server's packets, because that is what it has for the players it can see. Common code - the body
 * size below, the loot search, the interaction rules - asks {@link #isDowned} and gets the right
 * answer for whichever side it runs on, with no client class in sight.</p>
 *
 * <p><b>The body lies down.</b> A downed player is a low, short box with their eyes a hand's
 * width off the ground, which is what puts the downed player's own camera on the floor looking up,
 * and keeps the hitbox where the body is drawn. Both sides apply it, so the client predicts the
 * same collision box the server uses.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DownedState {

    private DownedState() {
    }

    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
    private static final Set<Integer> CLIENT = ConcurrentHashMap.newKeySet();

    public static final EntityDimensions BODY = EntityDimensions.scalable(0.6F, 0.5F);
    public static final float EYES = 0.3F;

    public static boolean isDowned(Entity entity) {
        if (entity instanceof DownedDummy) {
            return true;
        }
        if (!(entity instanceof Player)) {
            return false;
        }
        return entity.level().isClientSide ? CLIENT.contains(entity.getId()) : SERVER.contains(entity.getUUID());
    }

    static void setServer(UUID player, boolean downed) {
        if (downed) {
            SERVER.add(player);
        } else {
            SERVER.remove(player);
        }
    }

    /** Client side, from the server's packet. */
    public static void setClient(int entityId, boolean downed) {
        if (downed) {
            CLIENT.add(entityId);
        } else {
            CLIENT.remove(entityId);
        }
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    @SuppressWarnings("removal")
    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player && entity.level() != null && isDowned(entity)) {
            event.setNewSize(BODY, false);
            event.setNewEyeHeight(EYES);
        }
    }
}
