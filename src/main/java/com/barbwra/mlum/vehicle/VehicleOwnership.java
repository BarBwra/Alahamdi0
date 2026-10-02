package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.util.ArabicText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The player who summoned a vehicle is the only one who may drive it.
 *
 * <p><b>Why the driver seat and not the whole vehicle.</b> Locking the entity outright would make a
 * squad vehicle useless - nobody could be carried. Locking only the controls gives the behaviour a
 * squad actually wants: the owner drives, everyone else rides.</p>
 *
 * <p><b>Why this works without depending on the vehicle mod.</b> Every rideable entity answers
 * {@link Entity#getControllingPassenger()}, and Limitless Vehicle overrides it to return the seat-0
 * operator on the server. So "is this player driving?" is a vanilla question, and the rule applies
 * unchanged to a horse, a boat, or a vehicle from a mod that is not installed yet. Reading the mod's
 * own {@code getDriver()} would have bought nothing and cost a compile dependency.</p>
 *
 * <p><b>Why a tick check rather than cancelling the mount.</b> A non-owner is allowed to climb in -
 * they may be taking a passenger seat, and at mount time there is no reliable way to know which seat
 * they landed in. More importantly, Limitless Vehicle lets a seated player <i>switch</i> seats over
 * its own network channel, which fires no Forge mount event at all; a mount-time veto would be
 * walked straight through. Asking "who is holding the controls right now" catches every route in,
 * including ones no mod has written yet. The check costs nothing when nobody is riding.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class VehicleOwnership {

    private VehicleOwnership() {
    }

    /**
     * Lives in the entity's Forge persistent data, which is written to the region file for free and
     * survives a restart - unlike a map keyed by entity UUID, which would not.
     */
    private static final String KEY_OWNER = "mlum:vehicle_owner";

    /** Ejections are announced at most once every two seconds, so a held key cannot spam chat. */
    private static final long MESSAGE_COOLDOWN_TICKS = 40L;
    private static final String KEY_LAST_TOLD = "mlum:vehicle_denied_at";

    /* --------------------------------------------------------------------- stamping */

    /**
     * Records who summoned this vehicle. Called before the entity enters the world, so there is no
     * window in which it exists unowned and anyone nearby could claim the wheel.
     */
    public static void claim(Player owner, Entity vehicle) {
        vehicle.getPersistentData().putUUID(KEY_OWNER, owner.getUUID());
    }

    /** The summoner's id, or null for a vehicle this mod did not hand out. */
    @Nullable
    public static UUID ownerOf(Entity vehicle) {
        var tag = vehicle.getPersistentData();
        return tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
    }

    /** True when this player is allowed to hold the controls of this vehicle. */
    public static boolean mayDrive(Player player, Entity vehicle) {
        UUID owner = ownerOf(vehicle);
        // An unclaimed vehicle is not this mod's business - a boat crafted by hand stays free.
        if (owner == null || owner.equals(player.getUUID())) {
            return true;
        }
        return player.hasPermissions(2);
    }

    /* ----------------------------------------------------------------- enforcement */

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || !MlumConfig.ownerOnlyDriver()) {
            return;
        }

        Entity vehicle = player.getVehicle();
        // The overwhelmingly common case, and the reason this is cheap enough to run every tick.
        if (vehicle == null || vehicle.getControllingPassenger() != player) {
            return;
        }
        if (mayDrive(player, vehicle)) {
            return;
        }

        player.stopRiding();
        tell(player);
    }

    /**
     * Explains the ejection, rather than leaving the player wondering why the vehicle spat them out.
     *
     * <p>Rate limited through the player's own persistent data: a player who keeps clicking the
     * driver seat gets one line, not one per tick.</p>
     */
    private static void tell(ServerPlayer player) {
        long now = player.level().getGameTime();
        var tag = player.getPersistentData();
        if (tag.contains(KEY_LAST_TOLD) && now - tag.getLong(KEY_LAST_TOLD) < MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        tag.putLong(KEY_LAST_TOLD, now);
        player.displayClientMessage(
                Component.literal(ArabicText.autoDisplay("هذه المركبة ليست لك - يمكنك الركوب كراكب فقط"))
                        .withStyle(ChatFormatting.RED),
                true);
    }
}
