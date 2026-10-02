package com.barbwra.mlum.network;

import com.barbwra.mlum.rank.Rank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The rank ladder, the money packs, and which rank this player holds - in one message.
 *
 * <p>Together on purpose, the same reasoning as the faction state: the top bar draws the player's
 * rank <i>from</i> the ladder, so a frame in which the two disagree is a frame that draws a rank id
 * with no entry behind it. One packet cannot tear.</p>
 *
 * <p>The ladder is small and only sent on login and on change, so there is nothing to gain from
 * splitting the catalogue out of it.</p>
 */
public record S2CRanks(List<Rank> ranks, String held, List<? extends String> moneyPacks, String note) {

    private static final int MAX = 32;

    public static void encode(S2CRanks msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.ranks.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Rank r = msg.ranks.get(i);
            buf.writeUtf(NetText.cut(r.id(), 32), 32);
            buf.writeUtf(NetText.cut(r.name(), 32), 32);
            buf.writeVarInt(r.color());
            buf.writeUtf(NetText.cut(r.price(), 48), 48);
            buf.writeUtf(NetText.cut(r.blurb(), 256), 256);
            int perks = Math.min(r.perks().size(), 8);
            buf.writeVarInt(perks);
            for (int k = 0; k < perks; k++) {
                buf.writeUtf(NetText.cut(r.perks().get(k), 128), 128);
            }
        }
        buf.writeUtf(NetText.cut(msg.held, 32), 32);
        int packs = Math.min(msg.moneyPacks.size(), MAX);
        buf.writeVarInt(packs);
        for (int i = 0; i < packs; i++) {
            buf.writeUtf(NetText.cut(msg.moneyPacks.get(i), 128), 128);
        }
        buf.writeUtf(NetText.cut(msg.note, 256), 256);
    }

    public static S2CRanks decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Rank> ranks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(32);
            String name = buf.readUtf(32);
            int color = buf.readVarInt();
            String price = buf.readUtf(48);
            String blurb = buf.readUtf(256);
            int perkCount = Math.min(buf.readVarInt(), 8);
            List<String> perks = new ArrayList<>(perkCount);
            for (int k = 0; k < perkCount; k++) {
                perks.add(buf.readUtf(128));
            }
            ranks.add(new Rank(id, name, color, price, blurb, List.copyOf(perks)));
        }
        String held = buf.readUtf(32);
        int packCount = Math.min(buf.readVarInt(), MAX);
        List<String> packs = new ArrayList<>(packCount);
        for (int i = 0; i < packCount; i++) {
            packs.add(buf.readUtf(128));
        }
        return new S2CRanks(List.copyOf(ranks), held, List.copyOf(packs), buf.readUtf(256));
    }

    public static void handle(S2CRanks msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.barbwra.mlum.client.ClientRanks.accept(msg)));
        ctx.get().setPacketHandled(true);
    }
}
