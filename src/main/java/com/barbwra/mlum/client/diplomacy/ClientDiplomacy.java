package com.barbwra.mlum.client.diplomacy;

import com.barbwra.mlum.network.C2SDiplomacy;
import com.barbwra.mlum.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** The last diplomacy data the server sent, and the way back to it. */
@OnlyIn(Dist.CLIENT)
public final class ClientDiplomacy {

    private ClientDiplomacy() {
    }

    public static CompoundTag data = new CompoundTag();
    public static int version;

    public static void receive(String kind, CompoundTag tag) {
        data = tag;
        version++;
        if (kind.equals("open")) {
            Minecraft.getInstance().setScreen(new DiplomacyScreen());
        }
    }

    public static void ask() {
        send("open", new CompoundTag());
    }

    public static void send(String action, CompoundTag tag) {
        ModNetwork.CHANNEL.sendToServer(new C2SDiplomacy(action, tag));
    }
}
