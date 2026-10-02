package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.ClientQuestBoard;
import com.barbwra.mlum.client.ui.Px;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.layout.Span;
import com.barbwra.mlum.client.ui.text.Raster;
import com.barbwra.mlum.client.ui.view.Chrome;
import com.barbwra.mlum.client.ui.view.Css;
import com.barbwra.mlum.client.ui.view.Overlays;
import com.barbwra.mlum.quest.QuestEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Everything that has to outlive one screen: the static between menus (which starts on one screen
 * and finishes on the next), the wallet's count-up, the toast, the zombie alert, the key flash.
 *
 * <p>Switching from the bag to the quests is a different {@code Screen} object - the bag is a
 * container the server owns and the quests are not - so none of this can live on a screen. Keeping
 * it here is what makes the switch look like one continuous thing.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class UiState {

    private UiState() {
    }

    public static long now() {
        return System.nanoTime() / 1_000_000L;
    }

    public static boolean lite() {
        return MlumConfig.lightMode();
    }

    private static long lastFrame;

    /** Called once per rendered frame by whichever screen is up. */
    public static void frame(long t) {
        if (t - lastFrame > 2000L) {
            // the menus were closed a while: whatever happened meanwhile is not news
            walletKnown = false;
            dangerDir = -1;
        }
        lastFrame = t;
        tickWallet(t);
        tickDanger(t);
    }

    /* ================================================================== the store */

    /**
     * The rank/money store, which hangs off the top bar and therefore belongs to no tab.
     *
     * <p>Kept here rather than on a screen for exactly the reason everything else in this class is:
     * the top bar is drawn on all six tabs, so a store opened from the bag and a store opened from
     * the vehicles tab have to be the same store - and switching tabs while it is open must not
     * close it.</p>
     */
    private static boolean storeOpen;
    private static int storeTab = com.barbwra.mlum.client.ui.view.StoreView.RANKS;
    private static int storeDetail = -1;

    public static boolean storeOpen() {
        return storeOpen;
    }

    public static void closeStore() {
        storeOpen = false;
        storeDetail = -1;
    }

    /** The dialog for this frame, or null. Both screen classes hand their {@code modal} to this. */
    @javax.annotation.Nullable
    public static Node storeModal(String hover) {
        if (!storeOpen) {
            return null;
        }
        com.barbwra.mlum.client.ui.view.StoreView.Model m = new com.barbwra.mlum.client.ui.view.StoreView.Model();
        m.tab = storeTab;
        m.detail = storeDetail;
        m.held = com.barbwra.mlum.client.ClientRanks.heldIndex();
        m.ranks = com.barbwra.mlum.client.ClientRanks.ranks();
        m.packs = com.barbwra.mlum.client.ClientRanks.packs();
        m.note = UiText.logical(com.barbwra.mlum.client.ClientRanks.note());
        m.hover = hover;
        return com.barbwra.mlum.client.ui.view.StoreView.build(m);
    }

    /**
     * Handles a click that belongs to the store. True when it was one.
     *
     * <p>Called before every screen's own routing, so the dialog swallows what is under it - the
     * frame already stops the page being clicked <i>through</i>, but the bar above it is not covered
     * and would otherwise still switch tabs from behind the dialog.</p>
     */
    public static boolean storeClick(com.barbwra.mlum.client.ui.Hits.Hit hit) {
        String id = hit == null ? null : hit.id;
        if (id == null) {
            return false;
        }
        if (!storeOpen) {
            if (id.equals("rank")) {
                storeOpen = true;
                storeTab = com.barbwra.mlum.client.ui.view.StoreView.RANKS;
                storeDetail = -1;
                return true;
            }
            if (id.equals("wallet")) {
                storeOpen = true;
                // straight to the money list when there is one, otherwise the ranks
                storeTab = com.barbwra.mlum.client.ClientRanks.packs().isEmpty()
                        ? com.barbwra.mlum.client.ui.view.StoreView.RANKS
                        : com.barbwra.mlum.client.ui.view.StoreView.MONEY;
                storeDetail = -1;
                return true;
            }
            return false;
        }
        if (id.equals("close") || id.equals("modal-bg")) {
            closeStore();
            return true;
        }
        if (id.equals("store-back")) {
            storeDetail = -1;
            return true;
        }
        if (id.startsWith("store-tab:") && hit.data instanceof Integer tab) {
            storeTab = tab;
            storeDetail = -1;
            return true;
        }
        if (id.startsWith("rank-row:") && hit.data instanceof Integer i) {
            storeDetail = i;
            return true;
        }
        // anything else while the dialog is up is swallowed rather than acted on
        return true;
    }

    /** Esc closes the store before it closes the screen. True when it consumed the key. */
    public static boolean storeEscape() {
        if (!storeOpen) {
            return false;
        }
        if (storeDetail >= 0) {
            storeDetail = -1;
        } else {
            closeStore();
        }
        return true;
    }

    /* ================================================================== the menu guard */

    private static long menuPingAt = -1L;

    /**
     * Tells the server a menu is open, about twice a second while one is.
     *
     * <p>Far more often than the server's staleness window needs, and deliberately so: the claim has
     * to survive a dropped packet, and re-sending a boolean costs nothing next to being un-guarded
     * for five seconds because one went missing.</p>
     */
    public static void pingMenuOpen(long t) {
        if (menuPingAt >= 0L && t - menuPingAt < 500L) {
            return;
        }
        menuPingAt = t;
        com.barbwra.mlum.network.ModNetwork.CHANNEL.sendToServer(
                new com.barbwra.mlum.network.C2SMenuOpen(true));
    }

    /** The last menu closed: the guard is dropped now rather than left to time out. */
    public static void menuClosed() {
        menuPingAt = -1L;
        if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
            com.barbwra.mlum.network.ModNetwork.CHANNEL.sendToServer(
                    new com.barbwra.mlum.network.C2SMenuOpen(false));
        }
    }

    /* ================================================================== the static */

    private static final int SW = 320;
    private static final int SH = 155;
    private static final int[] NOISE_PX = new int[SW * SH];
    private static final Raster NOISE = new Raster(SW, SH, NOISE_PX, false, 0, 0);
    private static long fxStart = -1L;
    private static boolean fxSoft;
    private static boolean fxHold;
    private static long fxHoldSince;
    private static long jitterSeed = 0x9E3779B97F4A7C15L;

    /**
     * Starts the old-TV static: {@code soft} for a sub-page or a chest page, {@code hold} while the
     * next screen is still on its way from the server (the static stays up until it arrives).
     */
    public static void startFx(boolean soft, boolean hold) {
        if (lite()) {
            fxStart = -1L;
            return;
        }
        UiSounds.staticBurst(soft);
        fxStart = now();
        fxSoft = soft;
        fxHold = hold;
        fxHoldSince = fxStart;
    }

    /** The screen the static was waiting for has arrived: let it fade. */
    public static void releaseFx() {
        if (fxHold) {
            fxHold = false;
            long t = now();
            fxStart = t - attack();
        }
    }

    private static long duration() {
        return fxSoft ? 170L : 240L;
    }

    private static long attack() {
        return fxSoft ? 40L : 60L;
    }

    private static long elapsed(long t) {
        if (fxStart < 0L) {
            return Long.MAX_VALUE;
        }
        if (fxHold) {
            if (t - fxHoldSince > 700L) {
                fxHold = false;
                fxStart = t - attack();
            } else {
                return Math.min(t - fxStart, attack() - 1);
            }
        }
        return t - fxStart;
    }

    public static boolean fxActive(long t) {
        long e = elapsed(t);
        if (e == Long.MAX_VALUE) {
            return false;
        }
        if (e >= duration()) {
            fxStart = -1L;
            return false;
        }
        return true;
    }

    public static float fxOpacity(long t) {
        long e = elapsed(t);
        if (e == Long.MAX_VALUE) {
            return 0.0F;
        }
        float peak = fxSoft ? 0.6F : 0.94F;
        long at = attack();
        if (e < at) {
            return peak;
        }
        float k = Math.max(0.0F, 1.0F - (e - at) / (float) (duration() - at));
        return peak * (float) Math.pow(k, 1.5);
    }

    /** The shake on the menu body this frame, in framebuffer pixels: {dx, dy}. */
    public static int[] jitter(long t) {
        long e = elapsed(t);
        if (e == Long.MAX_VALUE || e >= duration()) {
            return null;
        }
        float amp = 1.0F - Math.min(1.0F, e / (float) duration());
        float dx = (rand() * 2.0F - 1.0F) * (fxSoft ? 4.0F : 8.0F) * amp;
        float dy = e < attack() ? (rand() * 2.0F - 1.0F) * 3.0F : 0.0F;
        return new int[]{Math.round(Math.round(dx) * Px.s), Math.round(Math.round(dy) * Px.s)};
    }

    /** This frame's noise, redrawn in place. */
    public static Raster noise(long t) {
        long e = elapsed(t);
        float p = Math.min(1.0F, Math.max(0L, e) / (float) duration());
        Overlays.noise(NOISE_PX, SW, SH, p);
        NOISE.version++;
        return NOISE;
    }

    private static float rand() {
        jitterSeed ^= jitterSeed << 13;
        jitterSeed ^= jitterSeed >>> 7;
        jitterSeed ^= jitterSeed << 17;
        return ((jitterSeed >>> 40) & 0xFFFFFF) / (float) 0x1000000;
    }

    /* ================================================================== the wallet */

    private static boolean walletKnown;
    private static long money;
    private static double shownFrom;
    private static double shownTo;
    private static long animAt = -1L;
    private static long delta;
    private static long deltaAt = -1L;

    private static void tickWallet(long t) {
        // the balance is the server's account now, not a count of banknotes the client can see
        long real = Math.max(0L, com.barbwra.mlum.client.ClientWallet.balance());
        if (!walletKnown) {
            walletKnown = true;
            money = real;
            shownFrom = real;
            shownTo = real;
            animAt = -1L;
            deltaAt = -1L;
            return;
        }
        if (real != money) {
            long d = real - money;
            shownFrom = shown(t);
            shownTo = real;
            animAt = t;
            money = real;
            delta = d;
            deltaAt = t;
            if (d > 0) {
                UiSounds.coin();
            }
        }
    }

    /** What the wallet reads this frame: counting toward the real value for 560ms after a change. */
    public static long shown(long t) {
        if (animAt < 0L || lite()) {
            return money;
        }
        float k = Math.min(1.0F, (t - animAt) / 560.0F);
        double e = 1.0 - Math.pow(1.0 - k, 3);
        if (k >= 1.0F) {
            animAt = -1L;
            return money;
        }
        return Math.round(shownFrom + (shownTo - shownFrom) * e);
    }

    public static long money() {
        return money;
    }

    /* ================================================================== toast */

    private static List<Span> toastRuns;
    private static boolean toastBad;
    private static long toastAt = -1L;

    /** A message over the footer for 2.6s. {@code {b}..{/b}} is bold, {@code {n}..{/n}} a number. */
    public static void toast(String markup, boolean bad) {
        toastRuns = Overlays.runs(markup == null ? "" : markup);
        toastBad = bad;
        toastAt = now();
    }

    public static Node toastNode(long t) {
        if (toastRuns == null || toastAt < 0L || t - toastAt > 2600L) {
            return null;
        }
        return Overlays.toast(toastRuns, toastBad);
    }

    /* ================================================================== zombie alert */

    public static int dangerDir = -1;
    public static int dangerBlocks;
    private static long dangerAt;

    private static void tickDanger(long t) {
        if (t - dangerAt < 250L) {
            return;
        }
        dangerAt = t;
        dangerDir = -1;
        int radius = MlumConfig.zombieAlertRadius();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (radius <= 0 || p == null || mc.level == null) {
            return;
        }
        AABB box = p.getBoundingBox().inflate(radius);
        Mob nearest = null;
        double best = (double) radius * radius;
        for (Mob m : mc.level.getEntitiesOfClass(Mob.class, box, e -> e instanceof Enemy && e.isAlive())) {
            double d = m.distanceToSqr(p);
            if (d < best) {
                best = d;
                nearest = m;
            }
        }
        if (nearest == null) {
            return;
        }
        double dx = nearest.getX() - p.getX();
        double dz = nearest.getZ() - p.getZ();
        double yaw = Math.toRadians(p.getYRot());
        double forward = dx * -Math.sin(yaw) + dz * Math.cos(yaw);
        double right = dx * -Math.cos(yaw) + dz * -Math.sin(yaw);
        double angle = Math.toDegrees(Math.atan2(right, forward));
        if (Math.abs(angle) <= 60.0D) {
            return;   // in front: the clear middle of the screen already shows it
        }
        if (angle > 60.0D && angle <= 135.0D) {
            dangerDir = Overlays.DIR_RIGHT;
        } else if (angle < -60.0D && angle >= -135.0D) {
            dangerDir = Overlays.DIR_LEFT;
        } else {
            dangerDir = Overlays.DIR_BEHIND;
        }
        dangerBlocks = Math.max(1, (int) Math.round(Math.sqrt(best)));
    }

    /* ================================================================== the top bar */

    private static char keyHit;
    private static long keyHitAt;
    public static boolean seenSkills;

    /** Flashes the A or D key cap for 160ms, as pressing it does in the design. */
    public static void keyHit(char key) {
        keyHit = key;
        keyHitAt = now();
    }

    public static Chrome.Bar bar(int tab, String hover, long t) {
        Chrome.Bar b = new Chrome.Bar();
        b.selected = tab;
        b.hover = hover;
        int open = 0;
        for (QuestEntry q : ClientQuestBoard.all()) {
            if (!(q.isComplete() && ClientQuestBoard.isClaimed(q.line()))) {
                open++;
            }
        }
        b.questBadge = open;
        b.skillsNew = !seenSkills;
        b.shown = shown(t);
        b.nowMs = t;
        b.deltaAtMs = deltaAt;
        b.delta = delta;
        b.lite = lite();
        b.keyHit = t - keyHitAt < 160L ? keyHit : 0;
        com.barbwra.mlum.rank.Rank rank = com.barbwra.mlum.client.ClientRanks.heldRank();
        if (rank != null) {
            b.rank = rank.name();
            b.rankColor = 0xFF000000 | rank.color();
        }
        return b;
    }
}
