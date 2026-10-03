package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.downed.DownedDummy;
import com.barbwra.mlum.downed.DownedState;
import com.barbwra.mlum.network.S2CDowned;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** What the server last said about each downed player this client can see, itself included. */
@OnlyIn(Dist.CLIENT)
public final class ClientDowned {

    private ClientDowned() {
    }

    public record Info(int remaining, int total, float revive, String reviver, long at) {
    }

    private static final Map<Integer, Info> INFO = new HashMap<>();

    public static void update(S2CDowned msg) {
        DownedState.setClient(msg.entityId(), msg.downed());
        if (msg.downed()) {
            INFO.put(msg.entityId(), new Info(msg.remaining(), msg.total(), msg.revive(), msg.reviver(),
                    Anim.now()));
        } else {
            INFO.remove(msg.entityId());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(msg.entityId());
            if (entity != null) {
                entity.refreshDimensions();
            }
        }
    }

    public static Info info(int entityId) {
        return INFO.get(entityId);
    }

    public static boolean selfDowned() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && DownedState.isDowned(mc.player);
    }

    /** How much of the bleed-out clock is left, 0..1, counted down smoothly between packets. */
    public static float left(Entity body) {
        if (body instanceof DownedDummy dummy) {
            return dummy.remaining() / (float) dummy.total();
        }
        Info info = INFO.get(body.getId());
        if (info == null) {
            return 1.0F;
        }
        float ticks = info.remaining() - (Anim.now() - info.at()) / 50.0F;
        return Math.max(0.0F, Math.min(1.0F, ticks / info.total()));
    }

    /** How far a revive on this body has got, 0..1. */
    public static float revive(Entity body) {
        if (body instanceof DownedDummy dummy) {
            return dummy.reviveProgress();
        }
        Info info = INFO.get(body.getId());
        return info == null ? 0.0F : info.revive();
    }

    public static void clear() {
        INFO.clear();
        DownedState.clearClient();
    }
}
