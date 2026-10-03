package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.downed.DownedDummy;
import com.barbwra.mlum.downed.DownedState;
import com.barbwra.mlum.network.C2SDownedAction;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.client.player.Input;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * The downed system on this client: lying down, looking up, and F.
 *
 * <h2>When you are the one down</h2>
 * <p>The camera lies on the ground looking at the sky and cannot turn - only a slow sway, like
 * breathing. Movement, the hands, the hotbar and every use or attack are held back. A heartbeat
 * plays. E calls your faction; holding F gives up.</p>
 *
 * <h2>When someone else is</h2>
 * <p>Walk up to them and two options appear over the body: loot and revive. The mouse wheel
 * moves between them and F does the one picked - once for loot, held for a revive, with the hands
 * up and working (see {@code RummageHands}). The defibrillator in hand turns revive into an instant
 * shock, the oxygen kit into a shorter hold.</p>
 *
 * <h2>How the body lies</h2>
 * <p>Drawn in the sleeping pose for the length of the render call and put back straight after, so
 * every client sees a downed player flat on the ground without anything about their real pose
 * changing.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class DownedClientEvents {

    private DownedClientEvents() {
    }

    public static final int LOOT = 0;
    public static final int REVIVE = 1;
    private static final int OPTIONS = 2;

    private static float lockedYaw = Float.NaN;
    private static int holdTicks;
    private static boolean gaveUp;
    private static int heartbeat;

    /** The body the prompt is showing for, or null. */
    private static Entity target;
    private static int selected = REVIVE;
    private static boolean reviving;
    private static int beat;
    private static long revivingSince;

    private static final Map<Integer, Pose> POSES = new HashMap<>();

    /** Set while a body is drawn standing in the loot screen, so it is not laid down for that draw. */
    public static boolean portrait;

    public static Entity target() {
        return target;
    }

    public static int selected() {
        return selected;
    }

    public static int holdTicks() {
        return holdTicks;
    }

    /** This client is holding F on a body to bring it round. */
    public static boolean reviving() {
        return reviving && target != null;
    }

    public static long revivingSince() {
        return revivingSince;
    }

    /* ================================================================== lying down */

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        if (!portrait && DownedState.isDowned(player) && !player.hasPose(Pose.SLEEPING)) {
            POSES.put(player.getId(), player.getPose());
            player.setPose(Pose.SLEEPING);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        Pose before = POSES.remove(event.getEntity().getId());
        if (before != null) {
            event.getEntity().setPose(before);
        }
    }

    /* ================================================================== looking up */

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!ClientDowned.selfDowned()) {
            lockedYaw = Float.NaN;
            return;
        }
        if (Float.isNaN(lockedYaw)) {
            lockedYaw = event.getYaw();
        }
        float t = Anim.now() / 1000.0F;
        float sway = Anim.enabled() ? Mth.sin(t * 0.9F) * 1.6F : 0.0F;
        event.setYaw(lockedYaw + sway * 0.6F);
        event.setPitch(-86.0F + sway);
        event.setRoll(Mth.sin(t * 0.55F) * 2.0F);
    }

    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (!ClientDowned.selfDowned()) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClientDowned.selfDowned()) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ClientDowned.selfDowned()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type() && ClientDowned.selfDowned()) {
            event.setCanceled(true);
        }
    }

    /** The wheel: frozen while down, and the option picker while a prompt is up. */
    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            return;
        }
        if (ClientDowned.selfDowned()) {
            event.setCanceled(true);
            return;
        }
        if (target != null) {
            event.setCanceled(true);
            int step = event.getScrollDelta() > 0 ? -1 : 1;
            selected = (selected + step + OPTIONS) % OPTIONS;
            stopReviving();
        }
    }

    /* ================================================================== F, every tick */

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // START runs before vanilla reads its keys, which is what lets the swap-hands press be held back
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            target = null;
            return;
        }
        if (ClientDowned.selfDowned()) {
            target = null;
            stopReviving();
            whileDown(mc);
            return;
        }
        holdTicks = 0;
        gaveUp = false;
        target = mc.screen == null ? findTarget(mc, player) : null;
        if (target == null) {
            stopReviving();
            while (DownedKeys.INTERACT.consumeClick()) {
                // nothing to do with it here; do not let presses pile up for later
            }
            return;
        }
        holdBackSwap(mc);
        int option = selected;
        boolean defib = holds(player.getMainHandItem(), MlumConfig.defibItem());
        if (option == REVIVE && !defib) {
            while (DownedKeys.INTERACT.consumeClick()) {
                // a held revive is driven by isDown below
            }
            if (DownedKeys.INTERACT.isDown()) {
                if (!reviving || ++beat % 4 == 0) {
                    send(C2SDownedAction.REVIVE, target);
                }
                if (!reviving) {
                    revivingSince = Anim.now();
                }
                reviving = true;
            } else {
                stopReviving();
            }
            return;
        }
        stopReviving();
        boolean pressed = false;
        while (DownedKeys.INTERACT.consumeClick()) {
            pressed = true;
        }
        if (!pressed) {
            return;
        }
        if (option == LOOT) {
            send(C2SDownedAction.LOOT, target);
        } else {
            send(C2SDownedAction.DEFIB, target);
        }
    }

    private static void whileDown(Minecraft mc) {
        // no crawling off: whatever else pushes the player, they stay where they fell
        LocalPlayer self = mc.player;
        if (self != null) {
            self.setSprinting(false);
            self.setDeltaMovement(0.0D, Math.min(0.0D, self.getDeltaMovement().y), 0.0D);
        }
        // no bag while down: the inventory key (E) calls the faction for help instead
        boolean call = false;
        while (mc.options.keyInventory.consumeClick()) {
            call = true;
        }
        while (mc.options.keyDrop.consumeClick()) {
            // nor dropping things
        }
        for (var hotbar : mc.options.keyHotbarSlots) {
            while (hotbar.consumeClick()) {
                // the belt stays where it was
            }
        }
        holdBackSwap(mc);
        if (call) {
            ModNetwork.CHANNEL.sendToServer(new C2SDownedAction(C2SDownedAction.DISTRESS, 0));
        }
        while (DownedKeys.INTERACT.consumeClick()) {
            // giving up is read from isDown below
        }
        if (DownedKeys.INTERACT.isDown()) {
            holdTicks++;
            if (!gaveUp && holdTicks >= MlumConfig.giveUpSeconds() * 20) {
                gaveUp = true;
                ModNetwork.CHANNEL.sendToServer(new C2SDownedAction(C2SDownedAction.GIVE_UP, 0));
            }
        } else {
            holdTicks = 0;
        }
        if (Anim.enabled() && ++heartbeat % 22 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.45F));
        }
    }

    private static void holdBackSwap(Minecraft mc) {
        if (DownedKeys.INTERACT.same(mc.options.keySwapOffhand)) {
            while (mc.options.keySwapOffhand.consumeClick()) {
                // F belongs to the downed system right now
            }
        }
    }

    private static void stopReviving() {
        if (reviving && target != null) {
            send(C2SDownedAction.STOP, target);
        }
        reviving = false;
        beat = 0;
    }

    private static void send(int action, Entity body) {
        ModNetwork.CHANNEL.sendToServer(new C2SDownedAction(action, body.getId()));
    }

    /** The downed body nearest the centre of the view, within reach. */
    private static Entity findTarget(Minecraft mc, LocalPlayer player) {
        double reach = MlumConfig.reviveRange() + 0.5D;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Entity best = null;
        double bestDot = Math.cos(Math.toRadians(55.0D));
        for (AbstractClientPlayer other : mc.level.players()) {
            if (other != player && DownedState.isDowned(other)) {
                double dot = score(eye, look, other, reach);
                if (dot > bestDot) {
                    bestDot = dot;
                    best = other;
                }
            }
        }
        for (DownedDummy dummy : mc.level.getEntitiesOfClass(DownedDummy.class, player.getBoundingBox().inflate(reach + 1.0D))) {
            double dot = score(eye, look, dummy, reach);
            if (dot > bestDot) {
                bestDot = dot;
                best = dummy;
            }
        }
        return best;
    }

    private static double score(Vec3 eye, Vec3 look, Entity body, double reach) {
        Vec3 to = body.getBoundingBox().getCenter().subtract(eye);
        double dist = to.length();
        if (dist > reach + 1.0D || dist < 1.0E-3D) {
            return -1.0D;
        }
        return look.dot(to.scale(1.0D / dist));
    }

    static boolean holds(ItemStack stack, String itemId) {
        if (stack.isEmpty() || itemId == null || itemId.isBlank()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.toString().equals(itemId.trim());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDowned.clear();
        ClientDistress.clear();
        target = null;
        reviving = false;
        lockedYaw = Float.NaN;
        POSES.clear();
    }
}
