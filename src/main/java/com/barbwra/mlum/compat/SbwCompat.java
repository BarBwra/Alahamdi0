package com.barbwra.mlum.compat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * Recognising a Superb Warfare vehicle without compiling against Superb Warfare.
 *
 * <p>Every vehicle in the mod descends from one class,
 * {@code com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity}, so the whole question is a
 * walk up the superclass chain looking for that name. That covers every current vehicle and every
 * one the mod adds later, because they all inherit it.</p>
 *
 * <p><b>Why a name check rather than a {@code compileOnly} jar.</b> Superb Warfare is written in
 * Kotlin and ships its own runtime; pulling it onto the compile classpath to answer one boolean
 * would tie this mod's build to that mod's version for no gain. The name is public API in practice -
 * it is what every addon already keys off - and a name check simply returns false on a server where
 * the mod is absent, which is exactly the behaviour wanted.</p>
 *
 * <p>The result is cached per class object, so the chain walk happens once per vehicle type rather
 * than once per frame. That matters: this is called from HUD rendering.</p>
 */
public final class SbwCompat {

    private SbwCompat() {
    }

    private static final String VEHICLE_BASE =
            "com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity";

    /**
     * Cache keyed by the entity's concrete class. A {@link java.util.WeakHashMap} would let classes
     * unload, but entity classes live for the process, and a plain map avoids the synchronisation
     * cost on the render thread.
     */
    private static final java.util.Map<Class<?>, Boolean> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** True when this entity is a Superb Warfare vehicle of any kind. */
    public static boolean isVehicle(@Nullable Entity entity) {
        if (entity == null) {
            return false;
        }
        return CACHE.computeIfAbsent(entity.getClass(), SbwCompat::walk);
    }

    private static boolean walk(Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            if (VEHICLE_BASE.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    /* ---- health, by reflection like the fuel below ---- */

    private static final java.util.Map<Class<?>, java.lang.reflect.Method[]> HEALTH = new java.util.concurrent.ConcurrentHashMap<>();

    private static java.lang.reflect.Method[] healthMethods(Entity entity) {
        java.lang.reflect.Method[] found = HEALTH.computeIfAbsent(entity.getClass(), type -> {
            java.lang.reflect.Method get = find(type, "getHealth");
            java.lang.reflect.Method set = find(type, "setHealth", float.class);
            // the vehicle's own damage routine: hit marker for the shooter, last attacker, effects
            java.lang.reflect.Method hurt = find(type, "onHurt", float.class, Entity.class, boolean.class);
            java.lang.reflect.Method max = find(type, "getMaxHealth");
            return new java.lang.reflect.Method[]{get, set, hurt, max};
        });
        return found;
    }

    @Nullable
    private static java.lang.reflect.Method find(Class<?> type, String name, Class<?>... args) {
        try {
            return type.getMethod(name, args);
        } catch (NoSuchMethodException | SecurityException e) {
            return null;
        }
    }

    /** A vehicle's health, or -1 when it cannot be read. */
    public static float health(@Nullable Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getHealth();
        }
        if (!isVehicle(entity)) {
            return -1.0F;
        }
        java.lang.reflect.Method get = healthMethods(entity)[0];
        if (get == null) {
            return -1.0F;
        }
        try {
            return ((Number) get.invoke(entity)).floatValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1.0F;
        }
    }

    /** A vehicle's full health, or -1 when it cannot be read. */
    public static float maxHealth(@Nullable Entity entity) {
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            return living.getMaxHealth();
        }
        if (!isVehicle(entity)) {
            return -1.0F;
        }
        java.lang.reflect.Method max = healthMethods(entity)[3];
        if (max == null) {
            return -1.0F;
        }
        try {
            return ((Number) max.invoke(entity)).floatValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1.0F;
        }
    }

    /** How full the tank is, 0..1, or -1 for a vehicle with no tank. */
    public static float energyFraction(@Nullable Entity entity) {
        java.lang.reflect.Method[] m = entity == null ? null : energyMethods(entity);
        if (m == null) {
            return -1.0F;
        }
        try {
            if (!(Boolean) m[3].invoke(entity)) {
                return -1.0F;
            }
            int max = (Integer) m[2].invoke(entity);
            return max <= 0 ? -1.0F : Math.max(0.0F, Math.min(1.0F, (Integer) m[0].invoke(entity) / (float) max));
        } catch (Throwable refused) {
            return -1.0F;
        }
    }

    /**
     * Takes {@code amount} from a vehicle, through its own damage routine when it has one so the
     * shooter gets the hit marker and the wreck counts as theirs, otherwise straight off its health.
     *
     * @return false when this vehicle cannot be damaged this way
     */
    public static boolean damage(@Nullable Entity vehicle, float amount, @Nullable Entity attacker) {
        if (!isVehicle(vehicle) || amount <= 0.0F) {
            return false;
        }
        java.lang.reflect.Method[] m = healthMethods(vehicle);
        try {
            if (m[2] != null) {
                m[2].invoke(vehicle, amount, attacker, true);
                return true;
            }
            if (m[0] != null && m[1] != null) {
                float now = ((Number) m[0].invoke(vehicle)).floatValue();
                m[1].invoke(vehicle, Math.max(0.0F, now - amount));
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            com.barbwra.mlum.MlumInventory.LOGGER.debug("could not damage {}: {}", vehicle, e.toString());
        }
        return false;
    }

    /** The Superb Warfare vehicle this player is riding, or null. */
    @Nullable
    public static Entity ridden(@Nullable Player player) {
        if (player == null) {
            return null;
        }
        Entity vehicle = player.getVehicle();
        return isVehicle(vehicle) ? vehicle : null;
    }

    /** True when the player is riding a Superb Warfare vehicle in any seat. */
    public static boolean isRiding(@Nullable Player player) {
        return ridden(player) != null;
    }

    /**
     * True when the player is the one steering.
     *
     * <p>Uses vanilla's {@link Entity#getControllingPassenger()} rather than the mod's own seat
     * list, for the same reason the ownership check does: it is the question the game itself asks,
     * so it stays right for boats, horses and any vehicle mod added later.</p>
     */
    public static boolean isDriving(@Nullable Player player) {
        Entity vehicle = ridden(player);
        return vehicle != null && vehicle.getControllingPassenger() == player;
    }

    /* ================================================================== energy */

    /**
     * Superb Warfare's fuel, reached by reflection.
     *
     * <p>{@code VehicleEntity} exposes {@code getEnergy}, {@code setEnergy}, {@code getMaxEnergy}
     * and {@code hasEnergyStorage} as plain public methods, so this needs no NBT guessing - the
     * numbers come from the mod's own accessors and stay right if it changes how they are stored.
     * Reflection rather than a {@code compileOnly} jar for the reason at the top of this class: the
     * mod is Kotlin and pulling it onto the compile path would tie this build to its version.</p>
     *
     * <p>Every lookup is cached per class and every call is wrapped. A signature that moves costs
     * the feature, never a crash.</p>
     */
    private static final java.util.Map<Class<?>, java.lang.reflect.Method[]> ENERGY = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.lang.reflect.Method[] NONE = new java.lang.reflect.Method[0];

    @Nullable
    private static java.lang.reflect.Method[] energyMethods(Entity entity) {
        if (!isVehicle(entity)) {
            return null;
        }
        java.lang.reflect.Method[] found = ENERGY.computeIfAbsent(entity.getClass(), type -> {
            try {
                // get / set / max / has, in that order
                return new java.lang.reflect.Method[]{
                        type.getMethod("getEnergy"),
                        type.getMethod("setEnergy", int.class),
                        type.getMethod("getMaxEnergy"),
                        type.getMethod("hasEnergyStorage"),
                };
            } catch (Throwable missing) {
                com.barbwra.mlum.MlumInventory.LOGGER.warn(
                        "[{}] {} has no energy accessors - the full-tank rule will skip it: {}",
                        com.barbwra.mlum.MlumInventory.MODID, type.getName(), missing.toString());
                return NONE;
            }
        });
        return found.length == 0 ? null : found;
    }

    /**
     * Fills the tank. Returns true when it actually did something.
     *
     * <p>Some vehicles have no energy storage at all - a towed gun, a trailer - and they answer
     * {@code hasEnergyStorage} with false rather than throwing, so they are skipped rather than
     * special-cased by name.</p>
     */
    public static boolean refuel(@Nullable Entity entity) {
        java.lang.reflect.Method[] m = entity == null ? null : energyMethods(entity);
        if (m == null) {
            return false;
        }
        try {
            if (!(Boolean) m[3].invoke(entity)) {
                return false;
            }
            int max = (Integer) m[2].invoke(entity);
            if (max <= 0 || (Integer) m[0].invoke(entity) >= max) {
                return false;
            }
            m[1].invoke(entity, max);
            return true;
        } catch (Throwable refused) {
            ENERGY.put(entity.getClass(), NONE);
            return false;
        }
    }

    /* ================================================================== the mod's own lock */

    /**
     * Superb Warfare's own {@code Locked} flag, which is a plain on/off.
     *
     * <p>Kept in step with this mod's three-way lock so the two never disagree on screen: SBW draws
     * its own padlock, and a vehicle this mod considers open should not still be showing one.</p>
     */
    public static void setSbwLocked(@Nullable Entity entity, boolean locked) {
        if (!isVehicle(entity)) {
            return;
        }
        try {
            entity.getClass().getMethod("setLocked", boolean.class).invoke(entity, locked);
        } catch (Throwable ignored) {
            // the flag is cosmetic here; this mod enforces its own rule either way
        }
    }
}
