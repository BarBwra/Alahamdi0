package com.barbwra.mlum.warehouse.net;

import com.barbwra.mlum.warehouse.client.ClientMissionData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Mission state for the heads-up display, sent once a second to the runner.
 *
 * <p><b>Deadlines travel as absolute epoch milliseconds.</b> The client subtracts its own clock and
 * redraws every frame, so one packet per second still produces a bar that slides smoothly at 60 fps
 * and a countdown that never stutters, jumps or drifts when a packet is late. Sending "seconds
 * remaining" instead would make the HUD only as smooth as the network.</p>
 *
 * <p>The drop coordinates ride along so the compass can be drawn entirely client-side, at frame
 * rate, from the player's own yaw - which is what replaces the {@code jwbwp_server create_pos}
 * waypoint call.</p>
 */
public record S2CMissionHud(boolean active, int state, long leaksAt, long expiresAt,
                            int payout, int crates, double targetX, double targetZ) {

    public static final S2CMissionHud INACTIVE =
            new S2CMissionHud(false, 0, 0L, 0L, 0, 0, 0.0D, 0.0D);

    public static void encode(S2CMissionHud packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.active);
        buf.writeVarInt(packet.state);
        buf.writeLong(packet.leaksAt);
        buf.writeLong(packet.expiresAt);
        buf.writeVarInt(packet.payout);
        buf.writeVarInt(packet.crates);
        buf.writeDouble(packet.targetX);
        buf.writeDouble(packet.targetZ);
    }

    public static S2CMissionHud decode(FriendlyByteBuf buf) {
        return new S2CMissionHud(buf.readBoolean(), buf.readVarInt(), buf.readLong(), buf.readLong(),
                buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(S2CMissionHud packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMissionData.accept(packet)));
        context.get().setPacketHandled(true);
    }
}
