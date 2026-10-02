package com.barbwra.mlum.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The lock state of the vehicle this player is sitting in.
 *
 * <h2>Why this packet has to exist</h2>
 * <p>Both the lock mode and the owner stamp live in the vehicle's {@code getPersistentData()}, and
 * <b>Forge never synchronises that tag to clients</b> - it is written to disk on the server and the
 * client's copy is simply empty. The HUD read it directly, found no owner, and returned before
 * drawing anything, every single frame. No amount of moving the coordinates was going to help.</p>
 *
 * <p>Only the rider is told, and only about the vehicle they are in. The HUD has no use for the
 * state of a vehicle parked across the map, and broadcasting it would be telling every client who
 * owns what.</p>
 */
public record S2CVehicleLockState(boolean riding, int mode, boolean owner) {

    public static final S2CVehicleLockState NONE = new S2CVehicleLockState(false, 0, false);

    public static void encode(S2CVehicleLockState msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.riding);
        buf.writeByte(msg.mode);
        buf.writeBoolean(msg.owner);
    }

    public static S2CVehicleLockState decode(FriendlyByteBuf buf) {
        boolean riding = buf.readBoolean();
        int mode = buf.readByte();
        return new S2CVehicleLockState(riding, mode, buf.readBoolean());
    }

    public static void handle(S2CVehicleLockState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.ClientVehicleLock.accept(msg)));
        ctx.get().setPacketHandled(true);
    }
}
