package com.barbwra.mlum.network;

import com.barbwra.mlum.skill.SkillService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Buy the next level of this skill", or "drop it back to nothing".
 *
 * <p>Both are one packet because both are the same shape of request: a skill id and an intent. The
 * server checks the level, the maxed cap, the price and the balance itself - the client only ever
 * names which card was clicked.</p>
 */
public record C2SSkillBuy(String id, boolean drop) {

    public C2SSkillBuy(String id) {
        this(id, false);
    }

    public static void encode(C2SSkillBuy msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.id, 64);
        buf.writeBoolean(msg.drop);
    }

    public static C2SSkillBuy decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        return new C2SSkillBuy(id, buf.readBoolean());
    }

    public static void handle(C2SSkillBuy msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }
            if (msg.drop()) {
                SkillService.drop(player, msg.id());
            } else {
                SkillService.buy(player, msg.id());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
