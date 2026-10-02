package com.barbwra.mlum.client;

import com.barbwra.mlum.client.ui.mc.UiScreens;
import com.barbwra.mlum.client.ui.mc.UiState;
import com.barbwra.mlum.util.ArabicChat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Where a server message lands: the menu's toast if a menu is up, the chat if not. */
@OnlyIn(Dist.CLIENT)
public final class ClientToast {

    private ClientToast() {
    }

    public static void accept(String markup, boolean bad) {
        Minecraft mc = Minecraft.getInstance();
        if (UiScreens.isOpen(mc.screen)) {
            UiState.toast(markup, bad);
            return;
        }
        if (mc.player != null) {
            String plain = markup.replace("{b}", "").replace("{/b}", "").replace("{n}", "").replace("{/n}", "");
            mc.player.displayClientMessage(ArabicChat.of(plain).copy()
                    .withStyle(bad ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        }
    }
}
