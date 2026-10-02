package com.barbwra.mlum.vehicle;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.compat.SbwCompat;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CVehicleLockState;
import com.barbwra.mlum.util.Feedback;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Enforces {@link VehicleLock}, and keeps an owned vehicle fuelled.
 *
 * <h2>Two gates, not one</h2>
 * <p>{@link EntityMountEvent} catches somebody walking up and pressing use. It does <b>not</b> catch
 * a vehicle mod moving a player between its own seats, which happens over that mod's own network
 * channel and fires no Forge event at all - the same trap {@link VehicleOwnership} documents. So
 * there is also a per-tick sweep, which is what actually makes the lock hold.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class VehicleAccess {

    private VehicleAccess() {
    }

    /** Last time each player was told, so a held-down use key does not become a machine gun. */
    private static final Map<UUID, Long> TOLD = new WeakHashMap<>();
    private static final long TELL_EVERY_TICKS = 40L;

    /* ------------------------------------------------------------------ getting in */

    /**
     * The door handle, and the one place this mod's rule can be applied before the vehicle mod's.
     *
     * <p>Forge fires this <b>before</b> {@code Entity#interact}, which matters twice over. It is
     * where Superb Warfare's own blind lock gets cleared - a vehicle still carrying it would
     * otherwise refuse its owner with {@code FAIL} and never reach {@link EntityMountEvent} at all -
     * and it is where a refusal can be reported with the right reason instead of that mod's generic
     * "locked" tip.</p>
     */
    @SubscribeEvent
    public static void onEntityInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity vehicle = event.getTarget();
        if (!SbwCompat.isVehicle(vehicle)) {
            return;
        }
        VehicleLock.clearForeignLock(vehicle);
        if (VehicleLock.mayRide(player, vehicle)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        refuse(player, vehicle);
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isMounting() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntityMounting() instanceof ServerPlayer player)) {
            return;
        }
        Entity vehicle = event.getEntityBeingMounted();
        if (!SbwCompat.isVehicle(vehicle) || VehicleLock.mayRide(player, vehicle)) {
            return;
        }
        event.setCanceled(true);
        refuse(player, vehicle);
    }

    /**
     * The sweep. Anybody sitting in a vehicle they are not allowed in is put back on the ground.
     *
     * <p>Cheap by construction: it only looks at players who are actually riding something, which is
     * almost never more than a handful, and does nothing at all for a vehicle nobody owns.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % 10 != 0) {
            return;
        }
        Entity vehicle = SbwCompat.ridden(player);
        if (vehicle == null) {
            pushState(player, null);
            return;
        }
        if (!VehicleLock.mayRide(player, vehicle)) {
            player.stopRiding();
            refuse(player, vehicle);
            pushState(player, null);
            return;
        }
        pushState(player, vehicle);
        // While somebody is in it, keep the tank full. Doing it here rather than on a timer means a
        // vehicle parked in a loaded chunk is not being topped up forever for nobody.
        if (MlumConfig.infiniteVehicleEnergy()) {
            SbwCompat.refuel(vehicle);
        }
    }

    /* ------------------------------------------------------------------ telling the client */

    /** What was last sent to each player, so an unchanged state is not re-sent twice a second. */
    private static final Map<UUID, Integer> SENT = new WeakHashMap<>();

    /**
     * Pushes the lock state to the rider.
     *
     * <p>Necessary because the mode and the owner stamp both live in the vehicle's
     * {@code getPersistentData()}, which Forge does not synchronise - the client's copy is empty, so
     * the HUD had nothing to read and drew nothing at all. See {@link S2CVehicleLockState}.</p>
     *
     * <p>Driven from the tick sweep rather than from mount and dismount events, because the sweep
     * already runs for exactly the players this concerns and cannot miss a transition the way a pair
     * of events can - a seat change inside the vehicle mod fires neither.</p>
     */
    private static void pushState(ServerPlayer player, @Nullable Entity vehicle) {
        int packed;
        S2CVehicleLockState state;
        if (vehicle == null) {
            packed = -1;
            state = S2CVehicleLockState.NONE;
        } else {
            VehicleLock.Mode mode = VehicleLock.mode(vehicle);
            UUID owner = VehicleOwnership.ownerOf(vehicle);
            boolean mine = owner != null && owner.equals(player.getUUID());
            // an unowned vehicle has no lock worth drawing, so it reads as "not riding" to the HUD
            boolean show = owner != null;
            packed = show ? mode.ordinal() * 2 + (mine ? 1 : 0) : -1;
            state = show ? new S2CVehicleLockState(true, mode.ordinal(), mine) : S2CVehicleLockState.NONE;
        }
        Integer last = SENT.get(player.getUUID());
        if (last != null && last == packed) {
            return;
        }
        SENT.put(player.getUUID(), packed);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), state);
    }

    /** Forces the next sweep to re-send, after something changed the state out of band. */
    public static void invalidate(ServerPlayer player) {
        SENT.remove(player.getUUID());
    }

    private static void refuse(ServerPlayer player, Entity vehicle) {
        long now = player.level().getGameTime();
        Long last = TOLD.get(player.getUUID());
        if (last != null && now - last < TELL_EVERY_TICKS) {
            return;
        }
        TOLD.put(player.getUUID(), now);
        player.level().playSound(null, vehicle.blockPosition(), SoundEvents.CHEST_LOCKED,
                SoundSource.PLAYERS, 0.7F, 1.0F);
        Feedback.bad(player, VehicleLock.refusal(vehicle));
    }

    /* ------------------------------------------------------------------ turning the key */

    /**
     * The owner pressed L.
     *
     * <p><b>From the driver's seat only.</b> Not from across the map, and not from the garage
     * screen - you turn the key while you are in the thing. That also removes the whole question of
     * which vehicle was meant when more than one is out.</p>
     *
     * <p>Owner only, deliberately: an organisation-mode vehicle lets members <i>ride</i>, not decide
     * who else can. One person owns the key.</p>
     */
    public static void toggle(ServerPlayer player) {
        Entity vehicle = SbwCompat.ridden(player);
        if (vehicle == null) {
            Feedback.bad(player, "لازم تكون داخل المركبة عشان تقفلها");
            return;
        }
        UUID owner = VehicleOwnership.ownerOf(vehicle);
        if (owner != null && !owner.equals(player.getUUID()) && !player.hasPermissions(2)) {
            Feedback.bad(player, "هذي مو مركبتك");
            return;
        }
        VehicleLock.Mode mode = VehicleLock.cycle(vehicle);
        player.level().playSound(null, vehicle.blockPosition(),
                mode == VehicleLock.Mode.OPEN ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.PLAYERS, 0.6F, 1.2F);
        Feedback.ok(player, "المركبة صارت {b}" + mode.label + "{/b}");
        // anybody who is no longer allowed goes out with the change, rather than at the next sweep
        for (Entity rider : new java.util.ArrayList<>(vehicle.getPassengers())) {
            if (rider instanceof Player passenger && !VehicleLock.mayRide(passenger, vehicle)) {
                rider.stopRiding();
            }
            // every rider's HUD is now stale, including the one who pressed the key
            if (rider instanceof ServerPlayer riding) {
                invalidate(riding);
                pushState(riding, vehicle);
            }
        }
        invalidate(player);
        pushState(player, SbwCompat.ridden(player));
    }
}
