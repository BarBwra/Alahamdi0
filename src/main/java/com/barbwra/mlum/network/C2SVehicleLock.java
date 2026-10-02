package com.barbwra.mlum.network;

import com.barbwra.mlum.vehicle.VehicleAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Turn the key on my vehicle." No payload - the server finds the vehicle and checks who is asking.
 *
 * <p>Deliberately carries neither which vehicle nor which state to move to. The client knowing the
 * lock state is a display concern; deciding it is not. A packet naming a target would let a modified
 * client unlock somebody else's tank.</p>
 */
public final class C2SVehicleLock {

    public static final C2SVehicleLock INSTANCE = new C2SVehicleLock();

    public static void encode(C2SVehicleLock msg, FriendlyByteBuf buf) {
    }

    public static C2SVehicleLock decode(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(C2SVehicleLock msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                VehicleAccess.toggle(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
