package com.barbwra.mlum.network;

import com.barbwra.mlum.vehicle.VehicleEntry;
import com.barbwra.mlum.vehicle.VehicleGarage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Summon, despawn, or ask for a fresh garage list.
 *
 * <p>The client sends only the entity id it wants. Ownership, the combat lock, the cooldown and
 * whether there is anywhere to put the thing are all decided by {@link VehicleGarage} on the
 * server - the button being enabled on screen means nothing here.</p>
 */
public record C2SVehicleAction(Action action, String entityId) {

    public enum Action {
        REFRESH, SUMMON, STORE
    }

    private static final Action[] ACTIONS = Action.values();

    public static C2SVehicleAction refresh() {
        return new C2SVehicleAction(Action.REFRESH, "");
    }

    public static C2SVehicleAction summon(String entityId) {
        return new C2SVehicleAction(Action.SUMMON, entityId);
    }

    public static C2SVehicleAction store() {
        return new C2SVehicleAction(Action.STORE, "");
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(action.ordinal());
        buf.writeUtf(entityId, VehicleEntry.MAX_ID);
    }

    public static C2SVehicleAction decode(FriendlyByteBuf buf) {
        Action action = ACTIONS[Math.floorMod(buf.readByte(), ACTIONS.length)];
        return new C2SVehicleAction(action, buf.readUtf(VehicleEntry.MAX_ID));
    }

    /** Why a summon was refused, in the menu's words. */
    public static String failure(VehicleGarage.Result result) {
        return switch (result) {
            case NOT_OWNED -> "ما تملك هذي المركبة";
            case UNKNOWN_ENTITY -> "مود المركبة مو مثبت";
            case COMBAT_LOCKED -> "ممنوع وأنت في قتال · ما تقدر تطلّع مركبة";
            case COOLDOWN -> "انتظر شوي قبل الاستدعاء";
            case NO_SPACE -> "ما فيه مكان كافي حولك";
            case WRONG_DIMENSION -> "ما تقدر تستدعي في هذا العالم";
            case BLOCKED_ZONE -> "منطقة ممنوع فيها الاستدعاء";
            case ALREADY_OUT -> "عندك مركبة برا · خزّنها أول";
            default -> "فشل الاستدعاء";
        };
    }

    public static void handle(C2SVehicleAction msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) {
            return;
        }
        switch (msg.action()) {
            case REFRESH -> VehicleGarage.sync(player);

            case STORE -> {
                // no putting it away mid-fight: not while you are in one, nor while it is being hit
                long mine = com.barbwra.mlum.vehicle.CombatTracker.remainingTicks(player);
                net.minecraft.world.entity.Entity active = VehicleGarage.findActive(player);
                long its = active == null ? 0L : com.barbwra.mlum.vehicle.CombatTracker.vehicleRemaining(active);
                if (mine > 0L) {
                    com.barbwra.mlum.util.Feedback.bad(player, "ما تقدر تخزن وأنت في قتال · باقي {n}" + (mine / 20 + 1) + "{/n} ثانية");
                } else if (its > 0L) {
                    com.barbwra.mlum.util.Feedback.bad(player, "المركبة تضربت قريب · تقدر تخزنها بعد {n}" + (its / 20 + 1) + "{/n} ثانية");
                } else if (VehicleGarage.storeActive(player)) {
                    com.barbwra.mlum.util.Feedback.ok(player, "تخزّنت المركبة");
                } else {
                    com.barbwra.mlum.util.Feedback.bad(player, "فقدت المركبة");
                }
                VehicleGarage.sync(player);
            }

            case SUMMON -> {
                VehicleGarage.Result result = VehicleGarage.summon(player, msg.entityId());
                if (result == VehicleGarage.Result.OK) {
                    com.barbwra.mlum.util.Feedback.ok(player, "استدعيت المركبة");
                } else {
                    com.barbwra.mlum.util.Feedback.bad(player, failure(result));
                }
                VehicleGarage.sync(player);
            }
        }
    }
}
