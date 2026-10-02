package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The skills tab: every skill for sale, the player's level in each, and how many may be maxed. */
public record S2CSkills(List<Entry> skills, int maxedCap, long dropCost) {

    public record Entry(String id, String name, String description, String icon,
                        List<String> effects, long[] prices, int level, boolean soon) {
    }

    private static final int MAX = 64;

    public static void encode(S2CSkills msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.skills.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = msg.skills.get(i);
            buf.writeUtf(NetText.cut(e.id(), 64), 64);
            buf.writeUtf(NetText.cut(e.name(), 128), 128);
            buf.writeUtf(NetText.cut(e.description(), 512), 512);
            buf.writeUtf(NetText.cut(e.icon(), 32), 32);
            for (int k = 0; k < 3; k++) {
                buf.writeUtf(NetText.cut(k < e.effects().size() ? e.effects().get(k) : "", 128), 128);
            }
            for (int k = 0; k < 3; k++) {
                buf.writeVarLong(k < e.prices().length ? Math.max(0L, e.prices()[k]) : 0L);
            }
            buf.writeVarInt(e.level());
            buf.writeBoolean(e.soon());
        }
        buf.writeVarInt(msg.maxedCap);
        buf.writeVarLong(Math.max(0L, msg.dropCost));
    }

    public static S2CSkills decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Entry> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(64);
            String name = buf.readUtf(128);
            String desc = buf.readUtf(512);
            String icon = buf.readUtf(32);
            List<String> effects = List.of(buf.readUtf(128), buf.readUtf(128), buf.readUtf(128));
            long[] prices = {buf.readVarLong(), buf.readVarLong(), buf.readVarLong()};
            int level = buf.readVarInt();
            out.add(new Entry(id, name, desc, icon, effects, prices, level, buf.readBoolean()));
        }
        int cap = buf.readVarInt();
        return new S2CSkills(out, cap, buf.readVarLong());
    }

    public static void handle(S2CSkills msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSkills.set(msg)));
        ctx.get().setPacketHandled(true);
    }
}
