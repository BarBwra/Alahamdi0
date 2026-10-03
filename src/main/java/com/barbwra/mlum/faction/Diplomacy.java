package com.barbwra.mlum.faction;

import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.bag.WalletService;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CDiplomacy;
import com.barbwra.mlum.util.ArabicText;
import com.barbwra.mlum.util.Feedback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

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
 * Factions dealing with factions: alliances, wars and bounties.
 *
 * <h2>Alliances</h2>
 * <p>From level {@value #ALLY_LEVEL} a faction may have one ally, two from level
 * {@value #ALLY_LEVEL_TWO}. The leader asks, the other leader accepts. Allies cannot hurt each
 * other and hear each other's distress calls. Either leader can break it at any time; both then wait
 * a day before they may ally with anyone.</p>
 *
 * <h2>Wars</h2>
 * <p>From level {@value #WAR_LEVEL} a leader may declare war on another faction for 48 hours, and the
 * whole server hears of it. Every member of one side killed by the other moves 1% of the loser's
 * bank to the killer's faction - never more, over the whole war, than a tenth of what that bank held
 * when the war began. The same two factions cannot go to war again for a week. A faction runs one
 * war it declared at a time.</p>
 *
 * <h2>Bounties</h2>
 * <p>The leader or deputy puts money from the faction bank on a player's head, up to
 * {@value #BOUNTY_PER_LEVEL} per faction level, one at a time. Whoever kills that player is paid it
 * into their own balance - except the target's own faction and its allies, so a bounty cannot be
 * collected by a friend. Unclaimed after {@value #BOUNTY_DAYS} days it goes back to the bank.</p>
 *
 * <p>Kills are credited to whoever last hurt the dead player within seven minutes, so a player who
 * was downed and then bled out still counts as the downer's kill.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID)
public final class Diplomacy {

    private Diplomacy() {
    }

    public static final int ALLY_LEVEL = 3;
    public static final int ALLY_LEVEL_TWO = 7;
    public static final long ALLY_COOLDOWN_MS = 24L * 3_600_000L;
    public static final long REQUEST_MS = 10L * 60_000L;

    public static final int WAR_LEVEL = 4;
    public static final long WAR_MS = 48L * 3_600_000L;
    public static final long WAR_COOLDOWN_MS = 7L * 24L * 3_600_000L;

    public static final int BOUNTY_PER_LEVEL = 5_000;
    public static final int BOUNTY_MIN = 500;
    public static final int BOUNTY_DAYS = 7;

    private static final long CREDIT_MS = 7L * 60_000L;

    /** An alliance asked for and not yet answered. Not saved: ten minutes is not worth a file. */
    private record Request(UUID from, UUID to, long at) {
    }

    private static final List<Request> REQUESTS = new ArrayList<>();
    /** Who last hurt each player, and when - for crediting kills that land after a downing. */
    private static final Map<UUID, UUID> LAST_HIT_BY = new HashMap<>();
    private static final Map<UUID, Long> LAST_HIT_AT = new HashMap<>();

    /* ================================================================== queries */

    public static int allySlots(Faction f) {
        return f.level() >= ALLY_LEVEL_TWO ? 2 : f.level() >= ALLY_LEVEL ? 1 : 0;
    }

    public static boolean allied(MinecraftServer server, @Nullable UUID a, @Nullable UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        for (DiplomacyData.Alliance al : DiplomacyData.get(server).alliances) {
            if (al.has(a) && al.has(b)) {
                return true;
            }
        }
        return false;
    }

    /** The factions allied with this one. */
    public static List<UUID> alliesOf(MinecraftServer server, UUID faction) {
        List<UUID> out = new ArrayList<>();
        for (DiplomacyData.Alliance al : DiplomacyData.get(server).alliances) {
            if (al.has(faction)) {
                out.add(al.other(faction));
            }
        }
        return out;
    }

    @Nullable
    public static DiplomacyData.War warBetween(MinecraftServer server, UUID a, UUID b) {
        long now = System.currentTimeMillis();
        for (DiplomacyData.War w : DiplomacyData.get(server).wars) {
            if (w.between(a, b) && now < w.end) {
                return w;
            }
        }
        return null;
    }

    /* ================================================================== the screen */

    public static void open(ServerPlayer player) {
        send(player, "open", payload(player));
    }

    private static void send(ServerPlayer player, String kind, CompoundTag tag) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CDiplomacy(kind, tag));
    }

    /** Refreshes the screen for every online member of these factions. */
    private static void refresh(MinecraftServer server, UUID... factions) {
        FactionData data = FactionData.get(server);
        Set<UUID> ids = new HashSet<>(List.of(factions));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Faction f = data.of(p.getUUID());
            if (f != null && ids.contains(f.id())) {
                send(p, "update", payload(p));
            }
        }
    }

    private static CompoundTag payload(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        FactionData data = FactionData.get(server);
        DiplomacyData d = DiplomacyData.get(server);
        long now = System.currentTimeMillis();
        CompoundTag tag = new CompoundTag();
        Faction me = data.of(player.getUUID());
        tag.putBoolean("InFaction", me != null);
        if (me != null) {
            FactionRole role = me.roleOf(player.getUUID());
            tag.putString("Name", me.name());
            tag.putInt("Level", me.level());
            tag.putInt("Bank", me.bank());
            tag.putBoolean("Leader", role == FactionRole.LEADER);
            tag.putBoolean("Spend", role != null && role.canSpendBank());
            tag.putInt("AllySlots", allySlots(me));
            tag.putLong("AllyCooldown", Math.max(0L, d.allyCooldown.getOrDefault(me.id(), 0L) - now));
            tag.putInt("BountyMax", Math.max(0, me.level()) * BOUNTY_PER_LEVEL);
        }

        ListTag factions = new ListTag();
        for (Faction f : data.leaderboard()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", f.id());
            t.putString("Name", f.name());
            t.putInt("Level", f.level());
            t.putInt("Members", f.size());
            String status = "none";
            if (me != null) {
                if (f.id().equals(me.id())) {
                    status = "self";
                } else if (allied(server, me.id(), f.id())) {
                    status = "ally";
                } else if (warBetween(server, me.id(), f.id()) != null) {
                    status = "war";
                } else if (hasRequest(me.id(), f.id())) {
                    status = "asked";
                } else if (hasRequest(f.id(), me.id())) {
                    status = "asking";
                }
                Long cd = d.warCooldown.get(DiplomacyData.pair(me.id(), f.id()));
                t.putLong("WarCooldown", cd == null ? 0L : Math.max(0L, cd - now));
            }
            t.putString("Status", status);
            factions.add(t);
        }
        tag.put("Factions", factions);

        ListTag wars = new ListTag();
        if (me != null) {
            for (DiplomacyData.War w : d.wars) {
                if (!w.involves(me.id()) || now >= w.end) {
                    continue;
                }
                boolean att = w.attacker.equals(me.id());
                Faction enemy = data.byId(att ? w.defender : w.attacker);
                CompoundTag t = new CompoundTag();
                t.putString("Enemy", enemy == null ? "?" : enemy.name());
                t.putBoolean("Declared", att);
                t.putLong("Left", w.end - now);
                t.putLong("Total", w.end - w.start);
                t.putInt("MyKills", att ? w.attackerKills : w.defenderKills);
                t.putInt("TheirKills", att ? w.defenderKills : w.attackerKills);
                t.putInt("Won", att ? w.lostDefender : w.lostAttacker);
                t.putInt("Lost", att ? w.lostAttacker : w.lostDefender);
                wars.add(t);
            }
        }
        tag.put("Wars", wars);

        ListTag bounties = new ListTag();
        for (DiplomacyData.Bounty b : d.bounties) {
            CompoundTag t = new CompoundTag();
            t.putString("Target", b.targetName());
            t.putString("By", b.factionName());
            t.putInt("Amount", b.amount());
            t.putLong("Left", Math.max(0L, b.placed() + BOUNTY_DAYS * 86_400_000L - now));
            t.putBoolean("Mine", me != null && b.faction().equals(me.id()));
            t.putBoolean("OnMe", b.target().equals(player.getUUID()));
            bounties.add(t);
        }
        tag.put("Bounties", bounties);

        ListTag players = new ListTag();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Faction theirs = data.of(p.getUUID());
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", p.getUUID());
            t.putString("Name", p.getGameProfile().getName());
            t.putString("Faction", theirs == null ? "" : theirs.name());
            boolean friend = me != null && theirs != null
                    && (theirs.id().equals(me.id()) || allied(server, me.id(), theirs.id()));
            t.putBoolean("Friend", friend);
            players.add(t);
        }
        tag.put("Players", players);
        return tag;
    }

    private static boolean hasRequest(UUID from, UUID to) {
        long now = System.currentTimeMillis();
        for (Request r : REQUESTS) {
            if (r.from().equals(from) && r.to().equals(to) && now - r.at() < REQUEST_MS) {
                return true;
            }
        }
        return false;
    }

    /* ================================================================== actions */

    public static void handle(ServerPlayer player, String action, CompoundTag tag) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        if (action.equals("open")) {
            open(player);
            return;
        }
        FactionData data = FactionData.get(server);
        Faction me = data.of(player.getUUID());
        if (me == null) {
            Feedback.bad(player, "لازم تكون في منظمة");
            return;
        }
        FactionRole role = me.roleOf(player.getUUID());
        Faction other = tag.hasUUID("Id") ? data.byId(tag.getUUID("Id")) : null;
        switch (action) {
            case "ally.request" -> {
                if (role != FactionRole.LEADER) {
                    Feedback.bad(player, "التحالفات للقائد بس");
                } else if (other != null) {
                    requestAlliance(server, player, me, other);
                }
            }
            case "ally.accept" -> {
                if (role != FactionRole.LEADER) {
                    Feedback.bad(player, "التحالفات للقائد بس");
                } else if (other != null) {
                    acceptAlliance(server, player, me, other);
                }
            }
            case "ally.reject" -> {
                if (role == FactionRole.LEADER && other != null) {
                    REQUESTS.removeIf(r -> r.from().equals(other.id()) && r.to().equals(me.id()));
                    tellFaction(server, other, "{b}" + me.name() + "{/b} رفضت طلب التحالف", true);
                    refresh(server, me.id(), other.id());
                }
            }
            case "ally.break" -> {
                if (role != FactionRole.LEADER) {
                    Feedback.bad(player, "التحالفات للقائد بس");
                } else if (other != null) {
                    breakAlliance(server, me, other);
                }
            }
            case "war.declare" -> {
                if (role != FactionRole.LEADER) {
                    Feedback.bad(player, "إعلان الحرب للقائد بس");
                } else if (other != null) {
                    declareWar(server, player, me, other);
                }
            }
            case "bounty.place" -> {
                if (role == null || !role.canSpendBank()) {
                    Feedback.bad(player, "المكافآت من فلوس المنظمة للقائد والنائب بس");
                } else {
                    placeBounty(server, player, me, tag.hasUUID("Target") ? tag.getUUID("Target") : null, tag.getInt("Amount"));
                }
            }
            default -> {
            }
        }
    }

    /* ---- alliances ---- */

    private static String allyBlock(MinecraftServer server, Faction f) {
        DiplomacyData d = DiplomacyData.get(server);
        long now = System.currentTimeMillis();
        if (allySlots(f) == 0) {
            return f.name() + " تحتاج مستوى " + ALLY_LEVEL + " عشان تتحالف";
        }
        if (alliesOf(server, f.id()).size() >= allySlots(f)) {
            return f.name() + " عندها تحالفات بعدد ما يسمح مستواها";
        }
        if (d.allyCooldown.getOrDefault(f.id(), 0L) > now) {
            return f.name() + " فكت تحالف قريب، تنتظر يوم قبل تحالف جديد";
        }
        return null;
    }

    private static void requestAlliance(MinecraftServer server, ServerPlayer player, Faction me, Faction other) {
        if (other.id().equals(me.id()) || allied(server, me.id(), other.id())) {
            return;
        }
        if (warBetween(server, me.id(), other.id()) != null) {
            Feedback.bad(player, "ما تقدرون تتحالفون وأنتم في حرب");
            return;
        }
        String block = allyBlock(server, me);
        if (block == null) {
            block = allyBlock(server, other);
        }
        if (block != null) {
            Feedback.bad(player, block);
            return;
        }
        if (hasRequest(other.id(), me.id())) {
            acceptAlliance(server, player, me, other);
            return;
        }
        REQUESTS.removeIf(r -> r.from().equals(me.id()) && r.to().equals(other.id()));
        REQUESTS.add(new Request(me.id(), other.id(), System.currentTimeMillis()));
        Feedback.ok(player, "انرسل طلب التحالف لـ {b}" + other.name() + "{/b} · ينتهي بعد 10 دقايق");
        tellFaction(server, other, "{b}" + me.name() + "{/b} تبي تتحالف معكم · قائدكم يقبل من شاشة الدبلوماسية", false);
        refresh(server, me.id(), other.id());
    }

    private static void acceptAlliance(MinecraftServer server, ServerPlayer player, Faction me, Faction other) {
        if (!hasRequest(other.id(), me.id())) {
            Feedback.bad(player, "الطلب انتهى");
            return;
        }
        String block = allyBlock(server, me);
        if (block == null) {
            block = allyBlock(server, other);
        }
        if (block != null) {
            Feedback.bad(player, block);
            return;
        }
        REQUESTS.removeIf(r -> (r.from().equals(other.id()) && r.to().equals(me.id()))
                || (r.from().equals(me.id()) && r.to().equals(other.id())));
        DiplomacyData.get(server).addAlliance(me.id(), other.id(), System.currentTimeMillis());
        broadcast(server, "تحالف جديد: " + me.name() + " و " + other.name(), ChatFormatting.AQUA);
        refresh(server, me.id(), other.id());
    }

    private static void breakAlliance(MinecraftServer server, Faction me, Faction other) {
        DiplomacyData d = DiplomacyData.get(server);
        boolean removed = d.alliances.removeIf(al -> al.has(me.id()) && al.has(other.id()));
        if (!removed) {
            return;
        }
        long until = System.currentTimeMillis() + ALLY_COOLDOWN_MS;
        d.allyCooldown.put(me.id(), until);
        d.allyCooldown.put(other.id(), until);
        d.setDirty();
        broadcast(server, "انفك التحالف بين " + me.name() + " و " + other.name(), ChatFormatting.GRAY);
        refresh(server, me.id(), other.id());
    }

    /* ---- war ---- */

    private static void declareWar(MinecraftServer server, ServerPlayer player, Faction me, Faction other) {
        DiplomacyData d = DiplomacyData.get(server);
        long now = System.currentTimeMillis();
        if (other.id().equals(me.id())) {
            return;
        }
        if (me.level() < WAR_LEVEL) {
            Feedback.bad(player, "إعلان الحرب يحتاج مستوى " + WAR_LEVEL);
            return;
        }
        if (allied(server, me.id(), other.id())) {
            Feedback.bad(player, "ما تقدر تعلن حرب على حليفك · فك التحالف أول");
            return;
        }
        if (warBetween(server, me.id(), other.id()) != null) {
            Feedback.bad(player, "أنتم في حرب معهم أصلاً");
            return;
        }
        for (DiplomacyData.War w : d.wars) {
            if (w.attacker.equals(me.id()) && now < w.end) {
                Feedback.bad(player, "عندكم حرب معلنة · وحدة بس في نفس الوقت");
                return;
            }
        }
        Long cd = d.warCooldown.get(DiplomacyData.pair(me.id(), other.id()));
        if (cd != null && cd > now) {
            Feedback.bad(player, "ما تقدرون تتحاربون مرة ثانية قبل " + hours(cd - now));
            return;
        }
        d.addWar(me.id(), other.id(), now, now + WAR_MS, me.bank() / 10, other.bank() / 10);
        d.warCooldown.put(DiplomacyData.pair(me.id(), other.id()), now + WAR_MS + WAR_COOLDOWN_MS);
        d.setDirty();
        broadcast(server, "⚔ " + me.name() + " أعلنت الحرب على " + other.name() + " لمدة 48 ساعة", ChatFormatting.RED);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.MASTER, 0.6F, 1.0F);
        }
        refresh(server, me.id(), other.id());
    }

    /* ---- bounties ---- */

    private static void placeBounty(MinecraftServer server, ServerPlayer player, Faction me, @Nullable UUID target, int amount) {
        DiplomacyData d = DiplomacyData.get(server);
        if (target == null) {
            Feedback.bad(player, "اختر اللاعب");
            return;
        }
        for (DiplomacyData.Bounty b : d.bounties) {
            if (b.faction().equals(me.id())) {
                Feedback.bad(player, "عندكم مكافأة شغالة · وحدة بس في نفس الوقت");
                return;
            }
        }
        int max = Math.max(0, me.level()) * BOUNTY_PER_LEVEL;
        if (max < BOUNTY_MIN) {
            Feedback.bad(player, "المكافآت تبدأ من مستوى 1");
            return;
        }
        if (amount < BOUNTY_MIN || amount > max) {
            Feedback.bad(player, "المبلغ بين " + BOUNTY_MIN + " و " + max);
            return;
        }
        ServerPlayer victim = server.getPlayerList().getPlayer(target);
        if (victim == null) {
            Feedback.bad(player, "اللاعب لازم يكون متصل");
            return;
        }
        Faction theirs = FactionData.get(server).of(target);
        if (theirs != null && (theirs.id().equals(me.id()) || allied(server, me.id(), theirs.id()))) {
            Feedback.bad(player, "ما تحط مكافأة على واحد منكم أو من حلفائكم");
            return;
        }
        if (!me.withdraw(amount)) {
            Feedback.bad(player, "فلوس المنظمة ما تكفي");
            return;
        }
        FactionData.get(server).setDirty();
        d.addBounty(new DiplomacyData.Bounty(me.id(), me.name(), target, victim.getGameProfile().getName(), amount,
                System.currentTimeMillis()));
        broadcast(server, "مطلوب: " + victim.getGameProfile().getName() + " · مكافأة $" + String.format(java.util.Locale.ROOT, "%,d", amount)
                + " من " + me.name(), ChatFormatting.GOLD);
        Feedback.bad(victim, "{b}" + me.name() + "{/b} حطت مكافأة على راسك · انتبه");
        refresh(server, me.id());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p != player) {
                send(p, "update", payload(p));
            }
        }
    }

    /* ================================================================== events */

    /** Allies cannot hurt each other. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer attacker) || attacker == victim) {
            return;
        }
        FactionData data = FactionData.get(victim.server);
        Faction a = data.of(attacker.getUUID());
        Faction b = data.of(victim.getUUID());
        if (a != null && b != null && allied(victim.server, a.id(), b.id())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != victim) {
            LAST_HIT_BY.put(victim.getUUID(), attacker.getUUID());
            LAST_HIT_AT.put(victim.getUUID(), System.currentTimeMillis());
        }
    }

    /** A real death (not a downing, which is cancelled before this): war money and bounties. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) {
            return;
        }
        MinecraftServer server = victim.server;
        ServerPlayer killer = event.getSource().getEntity() instanceof ServerPlayer p && p != victim ? p : null;
        if (killer == null) {
            UUID by = LAST_HIT_BY.get(victim.getUUID());
            Long at = LAST_HIT_AT.get(victim.getUUID());
            if (by != null && at != null && System.currentTimeMillis() - at < CREDIT_MS) {
                killer = server.getPlayerList().getPlayer(by);
            }
        }
        LAST_HIT_BY.remove(victim.getUUID());
        LAST_HIT_AT.remove(victim.getUUID());
        if (killer == null || killer == victim) {
            return;
        }
        warKill(server, killer, victim);
        claimBounties(server, killer, victim);
    }

    private static void warKill(MinecraftServer server, ServerPlayer killer, ServerPlayer victim) {
        FactionData data = FactionData.get(server);
        Faction kf = data.of(killer.getUUID());
        Faction vf = data.of(victim.getUUID());
        if (kf == null || vf == null) {
            return;
        }
        DiplomacyData.War w = warBetween(server, kf.id(), vf.id());
        if (w == null) {
            return;
        }
        boolean victimIsAttacker = w.attacker.equals(vf.id());
        int cap = victimIsAttacker ? w.capAttacker : w.capDefender;
        int lost = victimIsAttacker ? w.lostAttacker : w.lostDefender;
        int amount = Math.min(vf.bank() / 100, Math.max(0, cap - lost));
        if (victimIsAttacker) {
            w.defenderKills++;
            w.lostAttacker += Math.max(0, amount);
        } else {
            w.attackerKills++;
            w.lostDefender += Math.max(0, amount);
        }
        if (amount > 0 && vf.withdraw(amount)) {
            kf.addBank(amount);
            data.setDirty();
        }
        DiplomacyData.get(server).setDirty();
        String money = amount > 0 ? " · $" + String.format(java.util.Locale.ROOT, "%,d", amount) : "";
        tellFaction(server, kf, "{b}" + killer.getGameProfile().getName() + "{/b} قتل {b}" + victim.getGameProfile().getName()
                + "{/b} من " + vf.name() + money + " لبنككم", false);
        tellFaction(server, vf, "{b}" + victim.getGameProfile().getName() + "{/b} انقتل على يد " + kf.name() + money
                + " راحت من بنككم", true);
        refresh(server, kf.id(), vf.id());
    }

    private static void claimBounties(MinecraftServer server, ServerPlayer killer, ServerPlayer victim) {
        DiplomacyData d = DiplomacyData.get(server);
        FactionData data = FactionData.get(server);
        Faction vf = data.of(victim.getUUID());
        Faction kf = data.of(killer.getUUID());
        if (vf != null && kf != null && (vf.id().equals(kf.id()) || allied(server, vf.id(), kf.id()))) {
            return;   // a friend cannot cash in on a friend
        }
        int total = 0;
        Iterator<DiplomacyData.Bounty> it = d.bounties.iterator();
        while (it.hasNext()) {
            DiplomacyData.Bounty b = it.next();
            if (b.target().equals(victim.getUUID())) {
                total += b.amount();
                it.remove();
            }
        }
        if (total <= 0) {
            return;
        }
        d.setDirty();
        WalletService.give(killer, total);
        broadcast(server, killer.getGameProfile().getName() + " قبض مكافأة $" + String.format(java.util.Locale.ROOT, "%,d", total)
                + " على راس " + victim.getGameProfile().getName(), ChatFormatting.GOLD);
        killer.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 0.8F, 1.2F);
    }

    /* ================================================================== housekeeping */

    private static int tick;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++tick % 400 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        DiplomacyData d = DiplomacyData.get(server);
        FactionData data = FactionData.get(server);
        long now = System.currentTimeMillis();
        REQUESTS.removeIf(r -> now - r.at() >= REQUEST_MS);
        boolean dirty = d.alliances.removeIf(al -> data.byId(al.a()) == null || data.byId(al.b()) == null);

        Iterator<DiplomacyData.War> wars = d.wars.iterator();
        while (wars.hasNext()) {
            DiplomacyData.War w = wars.next();
            Faction a = data.byId(w.attacker);
            Faction b = data.byId(w.defender);
            if (a == null || b == null) {
                wars.remove();
                dirty = true;
            } else if (now >= w.end) {
                wars.remove();
                dirty = true;
                broadcast(server, "انتهت الحرب بين " + a.name() + " (" + w.attackerKills + " قتلة) و " + b.name()
                        + " (" + w.defenderKills + " قتلة)", ChatFormatting.YELLOW);
            }
        }

        Iterator<DiplomacyData.Bounty> bounties = d.bounties.iterator();
        while (bounties.hasNext()) {
            DiplomacyData.Bounty b = bounties.next();
            if (now - b.placed() >= BOUNTY_DAYS * 86_400_000L) {
                bounties.remove();
                dirty = true;
                Faction f = data.byId(b.faction());
                if (f != null) {
                    f.addBank(b.amount());
                    data.setDirty();
                    tellFaction(server, f, "انتهت المكافأة على " + b.targetName() + " · رجعت فلوسها لبنككم", false);
                }
            }
        }
        LAST_HIT_AT.entrySet().removeIf(e -> now - e.getValue() > CREDIT_MS);
        LAST_HIT_BY.keySet().retainAll(LAST_HIT_AT.keySet());
        if (dirty) {
            d.setDirty();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HIT_BY.remove(event.getEntity().getUUID());
        LAST_HIT_AT.remove(event.getEntity().getUUID());
    }

    /* ================================================================== helpers */

    private static void broadcast(MinecraftServer server, String text, ChatFormatting color) {
        server.getPlayerList().broadcastSystemMessage(
                Component.literal(ArabicText.autoDisplay(text)).withStyle(color), false);
    }

    private static void tellFaction(MinecraftServer server, Faction f, String markup, boolean bad) {
        for (UUID id : f.members().keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                Feedback.send(p, markup, bad);
            }
        }
    }

    private static String hours(long ms) {
        long h = Math.max(1, ms / 3_600_000L);
        return h >= 48 ? (h / 24) + " يوم" : h + " ساعة";
    }
}
