package com.barbwra.mlum.network;

import com.barbwra.mlum.bag.BagConfig;
import com.barbwra.mlum.bag.ItemSize;
import com.barbwra.mlum.bag.ItemTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The server's {@code mlum_inventory.toml}, sent on login and after {@code /mlum_inventory reload}.
 *
 * <p>Item sizes and rarities used to come from whatever copy of the file the client happened to
 * have - usually the default one - so an item the server made 2x2 was drawn 1x1, and every rarity an
 * admin set showed as plain grey. The client now draws with exactly what the server decides.</p>
 */
public record S2CBagConfig(BagConfig.Snapshot snapshot) {

    private static final int MAX = 4096;

    public static void encode(S2CBagConfig msg, FriendlyByteBuf buf) {
        BagConfig.Snapshot s = msg.snapshot;
        buf.writeVarInt(s.backpacks().size());
        for (Map.Entry<ResourceLocation, Integer> e : s.backpacks().entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue());
        }
        buf.writeVarInt(s.vipOnly().size());
        for (ResourceLocation id : s.vipOnly()) {
            buf.writeResourceLocation(id);
        }
        buf.writeVarInt(s.itemSizes().size());
        for (Map.Entry<ResourceLocation, ItemSize> e : s.itemSizes().entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue().width());
            buf.writeVarInt(e.getValue().height());
        }
        buf.writeVarInt(s.gunSizes().size());
        for (Map.Entry<String, ItemSize> e : s.gunSizes().entrySet()) {
            buf.writeUtf(e.getKey(), 256);
            buf.writeVarInt(e.getValue().width());
            buf.writeVarInt(e.getValue().height());
        }
        buf.writeVarInt(s.rarity().size());
        for (Map.Entry<ResourceLocation, ItemTier> e : s.rarity().entrySet()) {
            buf.writeResourceLocation(e.getKey());
            buf.writeVarInt(e.getValue().ordinal());
        }
        buf.writeBoolean(s.disableBackpackRightClick());
    }

    public static S2CBagConfig decode(FriendlyByteBuf buf) {
        Map<ResourceLocation, Integer> packs = new LinkedHashMap<>();
        int n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            packs.put(buf.readResourceLocation(), buf.readVarInt());
        }
        Set<ResourceLocation> vips = new LinkedHashSet<>();
        n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            vips.add(buf.readResourceLocation());
        }
        Map<ResourceLocation, ItemSize> sizes = new LinkedHashMap<>();
        n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            sizes.put(id, new ItemSize(buf.readVarInt(), buf.readVarInt()));
        }
        Map<String, ItemSize> guns = new LinkedHashMap<>();
        n = Math.min(buf.readVarInt(), MAX);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf(256);
            guns.put(id, new ItemSize(buf.readVarInt(), buf.readVarInt()));
        }
        Map<ResourceLocation, ItemTier> tiers = new LinkedHashMap<>();
        n = Math.min(buf.readVarInt(), MAX);
        ItemTier[] all = ItemTier.values();
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            tiers.put(id, all[Math.floorMod(buf.readVarInt(), all.length)]);
        }
        boolean noRightClick = buf.readBoolean();
        return new S2CBagConfig(new BagConfig.Snapshot(packs, vips, sizes, guns, tiers, noRightClick));
    }

    public static void handle(S2CBagConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                BagConfig.applyRemote(msg.snapshot());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
