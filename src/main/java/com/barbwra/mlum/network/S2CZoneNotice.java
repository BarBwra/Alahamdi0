package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientZoneNotice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Told to a player the moment they cross a safe area boundary.
 *
 * <p>Sent on the transition only, never per tick. The server already tracks which zone each player
 * was in last check, so this fires twice per visit - once entering, once leaving - and the client
 * owns the entire lifetime of the popup from there.</p>
 */
public record S2CZoneNotice(boolean entered, String zoneName) {

    public static void encode(S2CZoneNotice packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.entered);
        buf.writeUtf(packet.zoneName, 64);
    }

    public static S2CZoneNotice decode(FriendlyByteBuf buf) {
        return new S2CZoneNotice(buf.readBoolean(), buf.readUtf(64));
    }

    public static void handle(S2CZoneNotice packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientZoneNotice.show(packet.entered(), packet.zoneName())));
        context.get().setPacketHandled(true);
    }
}
