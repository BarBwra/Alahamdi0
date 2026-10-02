package com.barbwra.mlum.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The ground item scan.
 *
 * <p>Deliberately dumb and cheap: one AABB entity query, a spherical filter, a sort by distance
 * and a hard cap. It only ever runs for a player who currently has the tactical screen open, and
 * only once every {@code scanIntervalTicks} ticks (default 10, i.e. twice a second) - so the cost
 * is bounded by "players with the inventory open", not by player count.</p>
 */
public final class VicinityScanner {

    private VicinityScanner() {
    }

    /**
     * Extra reach allowed when actually taking an item, so that walking a hair out of range
     * between rendering a row and clicking it does not eat the click.
     */
    public static double reachSlack() {
        return 1.5D;
    }

    public static List<ItemEntity> scan(ServerPlayer player, double radius, int cap) {
        final Vec3 origin = player.position();
        final double radiusSq = radius * radius;

        AABB box = new AABB(
                origin.x - radius, origin.y - radius, origin.z - radius,
                origin.x + radius, origin.y + radius, origin.z + radius);

        List<ItemEntity> found = player.level().getEntitiesOfClass(ItemEntity.class, box,
                entity -> entity.isAlive()
                        && !entity.getItem().isEmpty()
                        && entity.distanceToSqr(origin) <= radiusSq);

        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(origin)));

        if (found.size() > cap) {
            return new ArrayList<>(found.subList(0, cap));
        }
        return found;
    }
}
