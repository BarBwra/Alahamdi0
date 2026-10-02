package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.client.gui.Anim;
import com.barbwra.mlum.network.S2CDistress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Faction members calling for help: a name and a place, shown for a little while and then gone.
 *
 * <p>One chime when the call arrives and a marker on screen for its lifetime - nothing repeats, so a
 * call is noticed without becoming a nuisance.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientDistress {

    private ClientDistress() {
    }

    public record Call(String name, Vec3 pos, long until) {
    }

    private static final List<Call> CALLS = new ArrayList<>();

    public static void add(S2CDistress msg) {
        CALLS.removeIf(c -> c.name().equals(msg.name()));
        CALLS.add(new Call(msg.name(), new Vec3(msg.x(), msg.y(), msg.z()), Anim.now() + msg.seconds() * 1000L));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.0F));
    }

    public static List<Call> live() {
        long now = Anim.now();
        CALLS.removeIf(c -> c.until() < now);
        return CALLS;
    }

    public static void clear() {
        CALLS.clear();
    }
}
