package com.barbwra.mlum.network;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.bag.BagService;
import com.barbwra.mlum.menu.MlumMenuProvider;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

import java.util.function.Supplier;

/**
 * Sent when the client cancels the vanilla inventory screen. The screen itself has to be opened by
 * the server, because the tactical menu owns server-side slots (the ground item window) that a
 * purely client-side {@code InventoryMenu} could never carry.
 */
public class C2SOpenMlumInventory {

    public static final C2SOpenMlumInventory INSTANCE = new C2SOpenMlumInventory();

    public void encode(FriendlyByteBuf buf) {
        // no payload
    }

    public static C2SOpenMlumInventory decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    /** {@code consumerMainThread} already hops to the server thread and marks the packet handled. */
    public static void handle(C2SOpenMlumInventory msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null || !MlumConfig.replaceInventory()) {
            return;
        }
        if (player.isSpectator()) {
            return;
        }
        if (player.isCreative() && !MlumConfig.replaceInCreative()) {
            return;   // creative keeps the vanilla item picker unless the pack opts in
        }
        // already inside some container - do not stack menus
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }

        NetworkHooks.openScreen(player,
                new MlumMenuProvider(Component.translatable("container.mlum.inventory"), null, 0),
                buf -> buf.writeByte(0));

        /*
         * The bag rides its own packet rather than vanilla's container sync, because its cells are
         * not slots - each item carries a (row, col) and a footprint that no Slot can express. Sent
         * after the screen is opened so the client has somewhere to put it, and sent on every open
         * so a client that missed an update while the screen was closed is corrected on sight.
         */
        BagService.sync(player);
    }
}
