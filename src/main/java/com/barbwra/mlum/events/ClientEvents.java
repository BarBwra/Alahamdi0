package com.barbwra.mlum.events;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.MlumInventory;
import com.barbwra.mlum.client.ClientQuestBoard;
import com.barbwra.mlum.client.ClientVehicleData;
import com.barbwra.mlum.client.hud.WeaponCard;
import com.barbwra.mlum.compat.TaczCompat;
import com.barbwra.mlum.network.C2SOpenMlumInventory;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import com.barbwra.mlum.client.ClientLevelData;
import com.barbwra.mlum.client.ClientZoneNotice;
import com.barbwra.mlum.client.hud.LevelHud;
import com.barbwra.mlum.client.hud.ZoneToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Turns the inventory key into a request for the server side tactical menu.
 *
 * <p>Vanilla opens {@code InventoryScreen} purely on the client, which cannot carry the ground item
 * slots or the backpack rows. So the key press is intercepted and replaced with a packet; the
 * server answers with the real menu, which arrives as a normal {@code ClientboundOpenScreenPacket}.</p>
 *
 * <p><b>Two independent interception points, on purpose.</b> {@link #onClientTick} consumes the
 * keybind in the tick phase that runs <i>before</i> {@code Minecraft.handleKeybinds}, so vanilla
 * never even tries to open its screen - this is the reliable path. {@link #onScreenOpening} stays
 * as a backstop for any other code that opens an {@code InventoryScreen} directly. Relying on the
 * screen event alone left the key doing nothing whenever the event ordering did not cooperate.</p>
 *
 * <p>Hold SHIFT while pressing the key to get the vanilla screen (and its 2x2 crafting grid).</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {
    }

    /**
     * TACZ's ammo readout, as it registers it in its own client setup. Matched by name rather than
     * by class so this mod still never has to compile against TACZ.
     */
    private static final String TACZ_GUN_HUD = "tac_gun_hud_overlay";

    /** Lets exactly one vanilla inventory open slip past the backstop. */
    private static boolean bypassOnce = false;
    /** Debounce so a laggy server cannot be spammed with open requests. */
    private static int cooldown = 0;

    public static void bypassNextOpen() {
        bypassOnce = true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // START runs before Minecraft#handleKeybinds, which is what lets us take the key first
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        com.barbwra.mlum.client.ui.mc.UiHost.idleTick();
        Minecraft minecraft = Minecraft.getInstance();
        if (cooldown > 0) {
            cooldown--;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.options == null) {
            return;
        }
        if (!shouldTakeOver(minecraft, player)) {
            return;   // leave the keybind alone so vanilla opens its own screen
        }
        if (com.barbwra.mlum.client.downed.ClientDowned.selfDowned()) {
            return;   // on the ground E is the call for help - DownedClientEvents reads it
        }

        enforceHitboxLock(minecraft, player);

        boolean pressed = false;
        while (minecraft.options.keyInventory.consumeClick()) {
            pressed = true;
        }
        if (!pressed || cooldown > 0) {
            return;
        }
        cooldown = 5;
        ModNetwork.CHANNEL.sendToServer(C2SOpenMlumInventory.INSTANCE);
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        /*
         * TACZ's own refit screen, the one Z opens, is cancelled outright.
         *
         * Attachments are fitted from the inventory now, and two ways to do the same thing that
         * disagree about what is installed is worse than one. Matched by class name so the mod
         * still does not have to reference TACZ's client classes - and cancelling the screen rather
         * than eating the keybind means it stays dead however it was opened.
         */
        if (MlumConfig.attachmentSlots()
                && event.getScreen().getClass().getName().startsWith("com.tacz.guns.client.gui.GunRefitScreen")) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getScreen() instanceof InventoryScreen)) {
            return;
        }
        if (bypassOnce) {
            bypassOnce = false;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !shouldTakeOver(minecraft, player)) {
            return;
        }

        event.setCanceled(true);

        if (cooldown > 0) {
            return;
        }
        cooldown = 5;
        ModNetwork.CHANNEL.sendToServer(C2SOpenMlumInventory.INSTANCE);
    }

    /**
     * Takes F3+B away from anyone who is not an operator.
     *
     * <p>Hitbox rendering outlines every entity through walls, which on a survival server is a
     * wallhack the game ships with. There is no event to veto the keybind, so instead the flag is
     * checked every tick and switched straight back off - a non-op can press it, and it is off
     * again before the next frame draws.</p>
     *
     * <p>Operators keep it, because it is genuinely how you debug a broken spawn or an entity that
     * will not render. Permission level 2 is the same bar the mod's own commands use, and the
     * client already knows it: the server sends the level on join, which is how vanilla decides
     * whether to offer the F3 debug options at all.</p>
     */
    private static void enforceHitboxLock(Minecraft minecraft, LocalPlayer player) {
        if (!MlumConfig.opOnlyHitboxes() || minecraft.getEntityRenderDispatcher() == null) {
            return;
        }
        if (minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes() && !player.hasPermissions(2)) {
            minecraft.getEntityRenderDispatcher().setRenderHitBoxes(false);
        }
    }

    /**
     * Creative keeps the vanilla item picker unless the pack owner opts in, because the tactical
     * screen has no creative tabs. This is the usual reason the key "does nothing" in testing.
     */
    private static boolean shouldTakeOver(Minecraft minecraft, LocalPlayer player) {
        if (!MlumConfig.replaceInventory() || Screen.hasShiftDown()) {
            return false;
        }
        if (player.isSpectator()) {
            return false;
        }
        boolean creative = minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems();
        return !creative || MlumConfig.replaceInCreative();
    }


    /**
     * Cancels exactly one overlay, and nothing else.
     *
     * <p>The whole vanilla HUD is left alone - all nine hotbar slots, hearts, armour, hunger and
     * the experience bar. Every attempt at replacing any of it was worse than what it replaced.</p>
     *
     * <p>Of TACZ's four overlays only {@code tac_gun_hud_overlay} goes: it is the ammo readout the
     * firearm card replaces, and both live in the same corner. Its heat bar, kill counter and
     * interact prompt stay, because nothing here substitutes for them. None of this edits
     * {@code tacz-client.toml} - turn {@code hud.firearmCard} off and TACZ's readout is back.</p>
     */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();

        // The menus fill the window with their own frame; the HUD showing through the shade under
        // the footer is clutter, and the design has none. Everything comes back when they close.
        if (com.barbwra.mlum.client.ui.mc.UiScreens.isOpen(Minecraft.getInstance().screen)) {
            event.setCanceled(true);
            return;
        }

        if (MlumConfig.firearmCard()
                && TaczCompat.MODID.equals(id.getNamespace())
                && TACZ_GUN_HUD.equals(id.getPath())) {
            event.setCanceled(true);
        }

        /*
         * Vanilla's experience bar is suppressed because LevelHud replaces it in the same place.
         * The mod's progression is written into experienceLevel so Skript can read it, which means
         * vanilla would draw its own bar over ours with identical data - two bars, neither legible.
         * Health, hunger and armour are deliberately left alone: they sit above this row and are
         * exactly where players already look for them.
         */
        if (event.getOverlay() == VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
            event.setCanceled(true);
        }

        /*
         * The field HUD redraws all of these itself: hearts, armour and hunger live on the wrist
         * device, the hotbar is the belt, and the held item's name is drawn over the belt.
         */
        if (MlumConfig.fieldHud()) {
            var overlay = event.getOverlay();
            if (overlay == VanillaGuiOverlay.PLAYER_HEALTH.type()
                    || overlay == VanillaGuiOverlay.ARMOR_LEVEL.type()
                    || overlay == VanillaGuiOverlay.FOOD_LEVEL.type()
                    || overlay == VanillaGuiOverlay.HOTBAR.type()
                    || overlay == VanillaGuiOverlay.ITEM_NAME.type()) {
                event.setCanceled(true);
            }
        }

    }

    /** Set while a vitals row is drawn lifted; cleared by the pop that undoes the lift. */
    private static boolean vitalsLifted;

    /**
     * Lifts the vitals rows clear of the level badge. Vanilla draws hearts and hunger at
     * screenHeight-39 and the badge lands at screenHeight-38, so without this they share a line.
     * Translating in Pre and undoing it in Post moves vanilla's own drawing without reimplementing
     * any of it.
     *
     * <p><b>Last, and never for a cancelled row.</b> Forge fires no Post for an overlay whose Pre was
     * cancelled, so a push made before some other mod cancels the row would never be popped and
     * would drag every later HUD element up the screen. Running at the lowest priority, and not at
     * all once cancelled, means the push only happens for a row that is really drawn.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderOverlayLift(RenderGuiOverlayEvent.Pre event) {
        if (isVitalsRow(event.getOverlay())) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0.0F, -LevelHud.VITALS_LIFT, 0.0F);
            vitalsLifted = true;
        }
    }

    /**
     * The safety net for the lift: if a row's Post never came (a listener after ours cancelled it),
     * the next overlay starts by undoing the leftover push, so at most one row is ever drawn wrong.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderOverlayFirst(RenderGuiOverlayEvent.Pre event) {
        if (vitalsLifted) {
            vitalsLifted = false;
            com.mojang.blaze3d.vertex.PoseStack pose = event.getGuiGraphics().pose();
            if (!pose.clear()) {   // clear() only asks whether this is the bottom entry
                pose.popPose();
            }
        }
    }

    /** A new frame draws on a new pose stack, so a lift left over from the last one is not popped. */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderGuiStart(RenderGuiEvent.Pre event) {
        vitalsLifted = false;
    }

    /** Restores the pose pushed in {@link #onRenderOverlayLift}. */
    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (vitalsLifted && isVitalsRow(event.getOverlay())) {
            vitalsLifted = false;
            event.getGuiGraphics().pose().popPose();
        }
    }

    /** The four rows that sit directly above the experience bar. */
    private static boolean isVitalsRow(net.minecraftforge.client.gui.overlay.NamedGuiOverlay overlay) {
        return overlay == VanillaGuiOverlay.PLAYER_HEALTH.type()
                || overlay == VanillaGuiOverlay.FOOD_LEVEL.type()
                || overlay == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || overlay == VanillaGuiOverlay.AIR_LEVEL.type();
    }

    /**
     * Draws the safe-area popup after the hotbar, so it layers over the world but under chat.
     *
     * <p>{@code Post} rather than {@code Pre} because this adds to the HUD instead of suppressing
     * part of it, and the hotbar is a reliable anchor that every GUI scale still renders.</p>
     */
    @SubscribeEvent
    public static void onRenderZoneToast(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        ZoneToast.render(event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
        LevelHud.render(event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
        com.barbwra.mlum.client.hud.LevelUpToast.render(event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WeaponCard.reset();
        com.barbwra.mlum.client.hud.field.FieldHud.reset();
        ClientZoneNotice.clear();
        com.barbwra.mlum.client.ClientLevelUp.clear();
        ClientLevelData.clear();
        ClientQuestBoard.clear();
        ClientVehicleData.clear();
        com.barbwra.mlum.client.ClientSkills.clear();
        com.barbwra.mlum.client.loot.LootMarkers.clear();
        com.barbwra.mlum.client.loot.ClientLootSearch.clear();
        com.barbwra.mlum.client.ClientRanks.clear();
        com.barbwra.mlum.client.ClientVehicleLock.clear();
        com.barbwra.mlum.client.ui.mc.UiState.closeStore();
        // the preview entities were built against the level that is going away
        com.barbwra.mlum.client.ui.mc.EntityPreview.clear();
        com.barbwra.mlum.client.ClientBagState.clear();
        com.barbwra.mlum.client.ClientWallet.clear();
        com.barbwra.mlum.client.ClientFactionData.reset();
        com.barbwra.mlum.client.ui.mc.McImage.clearCache();
        com.barbwra.mlum.client.ui.mc.McItems.clear();
        // the server's bag tables go with the server; this side's own file comes back
        com.barbwra.mlum.bag.BagConfig.load();
        cooldown = 0;
        bypassOnce = false;
    }
}
