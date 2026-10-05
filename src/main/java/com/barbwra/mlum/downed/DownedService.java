package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.events.ServerEvents;
import com.barbwra.mlum.faction.Faction;
import com.barbwra.mlum.faction.FactionData;
import com.barbwra.mlum.loot.LootSearch;
import com.barbwra.mlum.network.C2SDownedAction;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CDistress;
import com.barbwra.mlum.network.S2CDowned;
import com.barbwra.mlum.util.ChargeTag;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Downed (الإصابة): a killing blow puts a player on the ground instead of killing them.
 *
 * <h2>The rules</h2>
 * <ul>
 *   <li>Down for {@code bleedOutSeconds}; after that a real death, with the death message the
 *       blow would have given ("slain by Zombie"). What they carried is left in a backpack on the
 *       ground where they lay, not scattered - see {@link DeathBag}.</li>
 *   <li>Zombies and every other mob ignore a downed player - they stop targeting them and their
 *       hits do nothing. Another player's attack finishes them, and so does the world: lava,
 *       drowning, the void.</li>
 *   <li>Anyone can loot the body - the bag's chest screen, their whole inventory and backpack - or
 *       revive it by holding F for {@code reviveSeconds}. The oxygen kit revives in
 *       {@code oxygenSeconds} and is spent; the defibrillator revives at once and spends charge.</li>
 *   <li>A revived player gets up weak for a few seconds, and if they go down again soon after,
 *       the shorter timer applies, so a fight cannot be won by being revived over and over.</li>
 *   <li>A downed player can press E to call their faction once in a while, or hold F to give up.</li>
 *   <li>Logging out while down saves the clock; logging back in finds them still down.</li>
 * </ul>
 *
 * <p>Everything a client asks for comes through {@link #act}, and is checked here: range, who is
 * down, which item is really in hand, and - for a revive - that F is still being held, because a
 * revive only advances while its heartbeats keep arriving.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class DownedService {

    private DownedService() {
    }

    private static final UUID SLOW = UUID.fromString("5c0b1e2a-7d44-4e0a-9c31-2a6f0d8e4b17");
    private static final String SAVE_KEY = "MlumDowned";
    private static final int BEAT_TIMEOUT = 8;

    private static final class Down {
        int remaining;
        final int total;
        @Nullable
        final DamageSource cause;
        /** Where they went down; they are held to it. */
        final double x;
        final double z;

        Down(int remaining, int total, @Nullable DamageSource cause, double x, double z) {
            this.remaining = remaining;
            this.total = total;
            this.cause = cause;
            this.x = x;
            this.z = z;
        }
    }

    /** How far a downed player may drift from where they fell before being put back. */
    private static final double DRIFT_SQ = 0.3D * 0.3D;

    private static final class Revive {
        final UUID reviver;
        final Entity target;
        final int needed;
        final boolean oxygen;
        int progress;
        int lastBeat;

        Revive(UUID reviver, Entity target, int needed, boolean oxygen, int now) {
            this.reviver = reviver;
            this.target = target;
            this.needed = needed;
            this.oxygen = oxygen;
            this.lastBeat = now;
        }
    }

    private static final Map<UUID, Down> DOWNS = new HashMap<>();
    /** Players whose next death is meant to be real. */
    private static final Set<UUID> FINISHING = new HashSet<>();
    private static final Map<UUID, Integer> REVIVED_AT = new HashMap<>();
    private static final Map<UUID, Integer> DISTRESS_AT = new HashMap<>();
    /** By the target's entity id: one reviver at a time per body. */
    private static final Map<Integer, Revive> REVIVES = new HashMap<>();

    public static boolean isDowned(Player player) {
        return DownedState.isDowned(player);
    }

    /* ================================================================== going down */

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        UUID id = player.getUUID();
        if (FINISHING.remove(id) || DOWNS.containsKey(id)) {
            // dying from the ground - bled out, gave up or finished off: everything goes in a bag
            DeathBag.expect(player);
            clear(player);
            player.getPersistentData().remove(SAVE_KEY);
            return;
        }
        if (!MlumConfig.downed() || player.isCreative() || player.isSpectator()) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) {
            return;
        }
        event.setCanceled(true);
        player.setHealth(1.0F);
        down(player, source);
    }

    private static void down(ServerPlayer player, @Nullable DamageSource cause) {
        int now = tick(player);
        Integer revived = REVIVED_AT.get(player.getUUID());
        boolean again = revived != null && now - revived < MlumConfig.redownWindowSeconds() * 20;
        int total = (again ? MlumConfig.redownSeconds() : MlumConfig.downedSeconds()) * 20;
        start(player, total, total, cause);
    }

    private static void start(ServerPlayer player, int remaining, int total, @Nullable DamageSource cause) {
        DOWNS.put(player.getUUID(), new Down(remaining, total, cause, player.getX(), player.getZ()));
        holster(player);
        DownedState.setServer(player.getUUID(), true);
        player.stopRiding();
        player.closeContainer();
        player.setSprinting(false);
        player.clearFire();
        LootSearch.cancelFromClient(player);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW) == null) {
            speed.addTransientModifier(new AttributeModifier(SLOW, "mlum downed", -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        player.refreshDimensions();
        dropTargets(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 0.6F);
        sync(player);
    }

    /** Back on their feet, or dead for real: every trace of the downed state goes. */
    private static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean was = DOWNS.remove(id) != null;
        DownedState.setServer(id, false);
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW);
        }
        REVIVES.remove(player.getId());
        REVIVES.values().removeIf(r -> r.reviver.equals(id));
        if (was) {
            player.refreshDimensions();
            sync(player);
        }
    }

    /* ================================================================== who may hurt them */

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !DOWNS.containsKey(player.getUUID())) {
            return;
        }
        UUID id = player.getUUID();
        if (FINISHING.contains(id)) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof Player) {
            if (attacker != player && MlumConfig.playersFinishDowned()) {
                FINISHING.add(id);
            } else {
                event.setCanceled(true);
            }
            return;
        }
        if (attacker instanceof LivingEntity) {
            event.setCanceled(true);   // zombies leave the downed alone
            return;
        }
        FINISHING.add(id);   // the world itself - lava, water, the void - finishes them
    }

    /** A finishing blow kills outright, whatever armour the body is wearing. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FINISHING.contains(player.getUUID())
                && DOWNS.containsKey(player.getUUID())) {
            event.setAmount(Float.MAX_VALUE);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() != null && DownedState.isDowned(event.getNewTarget())
                && event.getEntity() instanceof Mob) {
            event.setCanceled(true);
        }
    }

    private static void dropTargets(Entity body) {
        AABB box = body.getBoundingBox().inflate(32.0D);
        for (Mob mob : body.level().getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == body)) {
            mob.setTarget(null);
        }
    }

    /* ================================================================== nothing to do while down */

    private static boolean blocked(Entity entity) {
        return entity instanceof ServerPlayer player && DOWNS.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /* ================================================================== the clock */

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();

        if (!DOWNS.isEmpty()) {
            for (UUID id : new ArrayList<>(DOWNS.keySet())) {
                Down d = DOWNS.get(id);
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (d == null || player == null || player.isRemoved()) {
                    continue;
                }
                d.remaining--;
                if (d.remaining <= 0) {
                    bleedOut(player);
                    continue;
                }
                hold(player, d);
                if (d.remaining % 10 == 0) {
                    sync(player);
                    dropTargets(player);
                }
            }
        }

        // finished revives are applied after the walk: getting someone up clears their sessions,
        // and doing that mid-iteration would change the map under the iterator
        List<Revive> finished = new ArrayList<>();
        Iterator<Map.Entry<Integer, Revive>> it = REVIVES.entrySet().iterator();
        while (it.hasNext()) {
            Revive r = it.next().getValue();
            ServerPlayer reviver = server.getPlayerList().getPlayer(r.reviver);
            if (reviver == null || !canReach(reviver, r.target) || now - r.lastBeat > BEAT_TIMEOUT
                    || (r.oxygen && !holds(reviver, MlumConfig.oxygenItem()))) {
                it.remove();
                setProgress(r.target, 0.0F, "");
                continue;
            }
            r.progress++;
            if (r.progress >= r.needed) {
                it.remove();
                finished.add(r);
                continue;
            }
            if (r.progress % 4 == 0) {
                setProgress(r.target, r.progress / (float) r.needed, reviver.getGameProfile().getName());
            }
        }
        for (Revive r : finished) {
            ServerPlayer reviver = server.getPlayerList().getPlayer(r.reviver);
            if (reviver == null) {
                continue;
            }
            if (r.oxygen) {
                reviver.getMainHandItem().shrink(1);
            }
            revive(r.target, reviver);
        }
    }

    /**
     * Keeps a downed player where they fell. The client already zeroes their movement keys, but a
     * client cannot be trusted with that - and a knock or a current would slide them along too - so
     * the server puts them back whenever they drift. Falling is left alone.
     */
    private static void hold(ServerPlayer player, Down d) {
        double dx = player.getX() - d.x;
        double dz = player.getZ() - d.z;
        player.setSprinting(false);
        if (dx * dx + dz * dz > DRIFT_SQ) {
            player.setDeltaMovement(0.0D, Math.min(0.0D, player.getDeltaMovement().y), 0.0D);
            player.connection.teleport(d.x, player.getY(), d.z, player.getYRot(), player.getXRot());
        }
    }

    /**
     * Puts the guns away: the selected slot moves off the two gun slots, so nothing is held up on
     * the ground - and a TACZ gun, which fires through its own keys rather than the use button, has
     * nothing to fire with.
     */
    private static void holster(ServerPlayer player) {
        int slot = player.getInventory().selected;
        if (slot < com.barbwra.mlum.menu.MlumMenu.WEAPON_SLOTS) {
            player.getInventory().selected = com.barbwra.mlum.menu.MlumMenu.WEAPON_SLOTS;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(
                    com.barbwra.mlum.menu.MlumMenu.WEAPON_SLOTS));
        }
    }

    private static void bleedOut(ServerPlayer player) {
        Down d = DOWNS.get(player.getUUID());
        DamageSource cause = d != null && d.cause != null ? d.cause : player.damageSources().generic();
        clear(player);
        FINISHING.add(player.getUUID());
        // die() straight away rather than another hurt(): a huge hit would also wreck the armour
        // the player is about to drop. Recording the blow again keeps the original death message.
        player.getCombatTracker().recordDamage(cause, 1.0F);
        player.setHealth(0.0F);
        player.die(cause);
        FINISHING.remove(player.getUUID());
    }

    /* ================================================================== getting up */

    private static void revive(Entity target, ServerPlayer by) {
        if (target instanceof DownedDummy dummy) {
            Feedback.ok(by, "قوّمت الجثة التجريبية");
            dummy.level().playSound(null, dummy.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.4F, 1.4F);
            dummy.vanish();
            return;
        }
        if (!(target instanceof ServerPlayer player)) {
            return;
        }
        clear(player);
        FINISHING.remove(player.getUUID());
        player.getPersistentData().remove(SAVE_KEY);
        player.setHealth(Math.min(player.getMaxHealth(), MlumConfig.reviveHealth()));
        int weak = MlumConfig.reviveWeakSeconds() * 20;
        if (weak > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, weak, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, weak, 0));
        }
        REVIVED_AT.put(player.getUUID(), tick(player));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.4F, 1.4F);
        String by0 = by.getGameProfile().getName();
        if (by != player) {
            Feedback.ok(by, "قوّمت {b}" + player.getGameProfile().getName() + "{/b}");
            Feedback.ok(player, "{b}" + by0 + "{/b} قوّمك");
        }
    }

    /* ================================================================== what players ask for */

    public static void act(ServerPlayer actor, int action, int targetId) {
        int now = tick(actor);
        if (action == C2SDownedAction.GIVE_UP) {
            if (DOWNS.containsKey(actor.getUUID())) {
                bleedOut(actor);
            }
            return;
        }
        if (action == C2SDownedAction.DISTRESS) {
            distress(actor, now);
            return;
        }
        if (DOWNS.containsKey(actor.getUUID()) || actor.isSpectator()) {
            return;
        }
        if (action == C2SDownedAction.STOP) {
            Revive r = REVIVES.get(targetId);
            if (r != null && r.reviver.equals(actor.getUUID())) {
                REVIVES.remove(targetId);
                setProgress(r.target, 0.0F, "");
            }
            return;
        }
        Entity body = actor.level().getEntity(targetId);
        if (body == null || !DownedState.isDowned(body) || body == actor || !canReach(actor, body)) {
            return;
        }
        switch (action) {
            case C2SDownedAction.LOOT -> loot(actor, body);
            case C2SDownedAction.REVIVE -> holdRevive(actor, body, now);
            case C2SDownedAction.DEFIB -> defib(actor, body);
            default -> {
            }
        }
    }

    private static void loot(ServerPlayer actor, Entity body) {
        if (actor.containerMenu != actor.inventoryMenu) {
            return;
        }
        Component title = body instanceof Player p ? p.getDisplayName() : Component.literal("Test body");
        ServerEvents.openBodyScreen(actor, new DownedLoot(body), title, DownedLoot.ROWS, body.getId());
    }

    private static void holdRevive(ServerPlayer actor, Entity body, int now) {
        Revive running = REVIVES.get(body.getId());
        if (running != null) {
            if (running.reviver.equals(actor.getUUID())) {
                running.lastBeat = now;
            }
            return;   // someone else is already on it
        }
        boolean oxygen = holds(actor, MlumConfig.oxygenItem());
        int seconds = oxygen ? MlumConfig.oxygenSeconds() : MlumConfig.reviveSeconds();
        REVIVES.put(body.getId(), new Revive(actor.getUUID(), body, seconds * 20, oxygen, now));
        setProgress(body, 0.0F, actor.getGameProfile().getName());
    }

    private static void defib(ServerPlayer actor, Entity body) {
        if (!holds(actor, MlumConfig.defibItem())) {
            return;
        }
        ItemStack stack = actor.getMainHandItem();
        if (!ChargeTag.has(stack)) {
            ChargeTag.set(stack, ChargeTag.DEFIB, MlumConfig.defibCapacity(), MlumConfig.defibCapacity());
        }
        int cost = MlumConfig.defibCost();
        int charge = ChargeTag.charge(stack);
        if (charge < cost) {
            Feedback.bad(actor, "الصاعق ما فيه طاقة كافية · " + charge + "/" + cost);
            return;
        }
        ChargeTag.setCharge(stack, charge - cost);
        actor.level().playSound(null, body.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35F, 1.8F);
        REVIVES.remove(body.getId());
        revive(body, actor);
    }

    private static void distress(ServerPlayer player, int now) {
        if (!DOWNS.containsKey(player.getUUID())) {
            return;
        }
        Integer last = DISTRESS_AT.get(player.getUUID());
        int cooldown = MlumConfig.distressCooldownSeconds() * 20;
        if (last != null && now - last < cooldown) {
            Feedback.bad(player, "تقدر تستغيث مرة ثانية بعد " + ((cooldown - (now - last)) / 20 + 1) + " ثانية");
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Faction faction = FactionData.get(server).of(player.getUUID());
        if (faction == null) {
            Feedback.bad(player, "ما عندك منظمة تسمع نداءك");
            return;
        }
        DISTRESS_AT.put(player.getUUID(), now);
        S2CDistress call = new S2CDistress(player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(),
                MlumConfig.distressSeconds());
        int told = 0;
        for (UUID member : faction.members().keySet()) {
            ServerPlayer other = server.getPlayerList().getPlayer(member);
            if (other != null && other != player && other.connection != null && other.level() == player.level()) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> other), call);
                told++;
            }
        }
        Feedback.ok(player, told > 0 ? "وصل نداءك لـ " + told + " من منظمتك" : "ما أحد من منظمتك قريب يسمعك");
    }

    /* ================================================================== helpers */

    private static boolean canReach(ServerPlayer actor, Entity body) {
        if (body.isRemoved() || !body.isAlive() || body.level() != actor.level() || !DownedState.isDowned(body)) {
            return false;
        }
        if (DOWNS.containsKey(actor.getUUID()) || !actor.isAlive()) {
            return false;
        }
        double reach = MlumConfig.reviveRange() + 0.75D;
        return actor.distanceToSqr(body) <= reach * reach;
    }

    private static boolean holds(ServerPlayer player, String itemId) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().equals(itemId.trim());
    }

    private static void setProgress(Entity body, float progress, String reviver) {
        if (body instanceof DownedDummy dummy) {
            dummy.setReviveProgress(progress);
        } else if (body instanceof ServerPlayer player) {
            sendState(player, progress, reviver);
        }
    }

    /** The downed state to the player and everyone who can see them. */
    private static void sync(ServerPlayer player) {
        Revive r = REVIVES.get(player.getId());
        MinecraftServer server = player.getServer();
        String name = "";
        float progress = 0.0F;
        if (r != null && server != null) {
            ServerPlayer reviver = server.getPlayerList().getPlayer(r.reviver);
            name = reviver == null ? "" : reviver.getGameProfile().getName();
            progress = r.progress / (float) r.needed;
        }
        sendState(player, progress, name);
    }

    private static void sendState(ServerPlayer player, float progress, String reviver) {
        if (player.connection == null) {
            return;
        }
        Down d = DOWNS.get(player.getUUID());
        S2CDowned msg = d == null
                ? new S2CDowned(player.getId(), false, 0, 1, 0.0F, "")
                : new S2CDowned(player.getId(), true, d.remaining, d.total, progress, reviver);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), msg);
    }

    private static int tick(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server == null ? 0 : server.getTickCount();
    }

    /* ================================================================== joining, leaving, watching */

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && DOWNS.containsKey(target.getUUID())
                && event.getEntity() instanceof ServerPlayer watcher && watcher.connection != null) {
            Down d = DOWNS.get(target.getUUID());
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> watcher),
                    new S2CDowned(target.getId(), true, d.remaining, d.total, 0.0F, ""));
        }
    }

    /** Leaving while down keeps the clock; nobody escapes a death by logging out. */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Down d = DOWNS.get(player.getUUID());
        if (d != null) {
            CompoundTag saved = new CompoundTag();
            saved.putInt("Remaining", d.remaining);
            saved.putInt("Total", d.total);
            player.getPersistentData().put(SAVE_KEY, saved);
        }
        clear(player);
        DISTRESS_AT.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(SAVE_KEY) || !MlumConfig.downed()) {
            data.remove(SAVE_KEY);
            return;
        }
        CompoundTag saved = data.getCompound(SAVE_KEY);
        data.remove(SAVE_KEY);
        int total = Math.max(1, saved.getInt("Total"));
        int remaining = Math.max(20, saved.getInt("Remaining"));
        player.setHealth(Math.max(1.0F, Math.min(player.getHealth(), 1.0F)));
        start(player, remaining, total, null);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clear(player);
            sendState(player, 0.0F, "");
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DOWNS.containsKey(player.getUUID())) {
            sync(player);
        }
    }

    /* ================================================================== commands */

    /** {@code /mlum downed self}: go down on purpose, to see it from the ground. */
    public static boolean downOnPurpose(ServerPlayer player) {
        if (DOWNS.containsKey(player.getUUID())) {
            return false;
        }
        player.setHealth(1.0F);
        down(player, null);
        return true;
    }

    /** {@code /mlum downed revive <player>}. */
    public static boolean reviveByCommand(ServerPlayer player) {
        if (!DOWNS.containsKey(player.getUUID())) {
            return false;
        }
        revive(player, player);
        return true;
    }

    public static List<String> downedNames(MinecraftServer server) {
        List<String> names = new ArrayList<>();
        for (UUID id : DOWNS.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                names.add(p.getGameProfile().getName());
            }
        }
        return names;
    }
}
