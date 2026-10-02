package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientLevelUp;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "You reached level N", as a popup rather than a chat line.
 *
 * <p>A level up used to be announced with {@code sendSystemMessage}, which put it in the chat log
 * where it scrolled away behind whatever else was being said - on a busy server, the one moment the
 * progression system has to feel like an event was the easiest thing to miss. It is now shown the
 * same way entering a safe area is: a card on the HUD, on screen for a few seconds, in the middle
 * of the player's view rather than the corner of it.</p>
 *
 * <p>Only the number travels. Everything about how it looks is the client's business.</p>
 */
public record S2CLevelUp(int level) {

    public static void encode(S2CLevelUp packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.level);
    }

    public static S2CLevelUp decode(FriendlyByteBuf buf) {
        return new S2CLevelUp(buf.readVarInt());
    }

    public static void handle(S2CLevelUp packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientLevelUp.show(packet.level())));
        context.get().setPacketHandled(true);
    }
}
