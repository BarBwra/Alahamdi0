package com.barbwra.mlum.network;

import com.barbwra.mlum.client.ClientVehicleData;
import com.barbwra.mlum.vehicle.VehicleEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A player's garage: what they own, whether one is out, and whether the summon button is currently
 * refusing to work and why.
 *
 * <p>The lock state travels with the list rather than being worked out on the client, because the
 * client cannot see combat tags, the allowed dimensions or the blacklisted zones, and must never be
 * the thing that decides whether a summon is allowed - it only decides how the button <i>looks</i>.
 * {@code blockKey} is the translation key for whichever placement rule currently forbids a summon,
 * or empty; without it the button read as enabled everywhere and every click was refused
 * server-side with no explanation on screen.</p>
 */
public record S2CVehicles(List<VehicleEntry> owned, boolean hasActive, boolean combatLocked,
                          long cooldownTicks, String blockKey, String activeEntity) {

    public static final int MAX = 64;

    public void encode(FriendlyByteBuf buf) {
        int count = Math.min(owned.size(), MAX);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            owned.get(i).write(buf);
        }
        buf.writeBoolean(hasActive);
        buf.writeBoolean(combatLocked);
        buf.writeVarLong(Math.max(0L, cooldownTicks));
        buf.writeUtf(blockKey == null ? "" : blockKey, 96);
        buf.writeUtf(activeEntity == null ? "" : activeEntity, VehicleEntry.MAX_ID);
    }

    public static S2CVehicles decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX);
        List<VehicleEntry> owned = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            owned.add(VehicleEntry.read(buf));
        }
        boolean active = buf.readBoolean();
        boolean locked = buf.readBoolean();
        long cooldown = buf.readVarLong();
        String block = buf.readUtf(96);
        return new S2CVehicles(owned, active, locked, cooldown, block, buf.readUtf(VehicleEntry.MAX_ID));
    }

    public static void handle(S2CVehicles msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVehicleData.set(
                msg.owned(), msg.hasActive(), msg.combatLocked(), msg.cooldownTicks(), msg.blockKey(),
                msg.activeEntity()));
    }
}
