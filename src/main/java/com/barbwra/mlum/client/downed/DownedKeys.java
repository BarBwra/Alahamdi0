package com.barbwra.mlum.client.downed;

import com.barbwra.mlum.MlumInventory;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * F: act on a downed body - or, when you are the one down, hold it to give up.
 *
 * <p>F is also vanilla's swap-hands key. While there is a body to act on, or while you are down,
 * the swap is held back so pressing F never also moves your offhand item.</p>
 *
 * <p>Calling for help while down is the inventory key (E) - the bag cannot open on the ground
 * anyway, and borrowing that key means no second binding on E to show up red in the controls.</p>
 */
@Mod.EventBusSubscriber(modid = MlumInventory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DownedKeys {

    private DownedKeys() {
    }

    public static final KeyMapping INTERACT = new KeyMapping(
            "key.mlum.interact", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F, "key.categories.mlum");

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(INTERACT);
    }
}
