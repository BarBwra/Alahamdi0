package com.barbwra.mlum.client.admin;

import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.text.Shaped;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/**
 * The small window a player writes a support ticket in: one text box, Enter sends it, Esc closes.
 * Opened with {@code /mlum ticket}; the text goes to the admin panel's ticket list.
 */
@OnlyIn(Dist.CLIENT)
public class TicketScreen extends Screen {

    private static final HudPen PEN = new HudPen();
    private String text = "";

    public TicketScreen() {
        super(Component.literal(""));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x99050705);
        HudPen pen = PEN;
        pen.begin(g);
        try {
            float w = 220.0F;
            float h = 74.0F;
            float x = width / 2.0F - w / 2.0F;
            float y = height / 2.0F - h / 2.0F;
            pen.rect(x, y, w, h, 0xF2111410);
            pen.rect(x, y, w, 0.5F, 0xFF2B3026);
            pen.rect(x, y + h - 0.5F, w, 0.5F, 0xFF2B3026);
            pen.rect(x + w - 3.0F, y + 5.0F, 1.5F, 7.0F, 0xFFF0A93B);
            pen.text(pen.kufi("تذكرة دعم", 7.0F, 700), x + w - 7.0F, y + 11.5F, HudPen.RIGHT, 0xFFECE6D4);
            pen.text(pen.kufi("اكتب مشكلتك، توصل للإدارة مع مكانك", 5.0F, 600), x + w - 7.0F, y + 20.0F, HudPen.RIGHT, 0xFFA19E8B);
            // the box
            float bx = x + 7.0F;
            float by = y + 26.0F;
            float bw = w - 14.0F;
            pen.rect(bx, by, bw, 26.0F, 0xFF0D100B);
            pen.rect(bx, by, bw, 0.5F, 0xFFF0A93B);
            pen.rect(bx, by + 25.5F, bw, 0.5F, 0xFFF0A93B);
            String shown = text.isEmpty() ? "مثال: أحد سرق شنطتي عند المستودع" : text;
            Shaped s = pen.kufi(tail(shown, 60), 5.5F, 600);
            pen.text(s, bx + bw - 4.0F, by + 15.0F, HudPen.RIGHT, text.isEmpty() ? 0xFF6E6D5F : 0xFFECE6D4);
            if ((System.currentTimeMillis() / 530L) % 2L == 0L) {
                float cx = bx + bw - 4.0F - (text.isEmpty() ? 0.0F : pen.width(s)) - 1.0F;
                pen.rect(cx, by + 7.0F, 0.5F, 11.0F, 0xFFECE6D4);
            }
            pen.text(pen.kufi("Enter للإرسال · Esc للإلغاء · " + text.length() + "/300", 4.5F, 600),
                    x + w - 7.0F, y + h - 7.0F, HudPen.RIGHT, 0xFF6E6D5F);
        } finally {
            pen.end();
        }
    }

    private static String tail(String s, int max) {
        return s.length() <= max ? s : "…" + s.substring(s.length() - max);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!text.isEmpty()) {
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (text.trim().length() >= 3) {
                CompoundTag tag = new CompoundTag();
                tag.putString("Text", text.trim());
                ClientAdmin.send("ticket.create", tag);
                onClose();
            }
            return true;
        }
        if (Screen.isPaste(keyCode) && minecraft != null) {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null) {
                for (int i = 0; i < clip.length(); i++) {
                    charTyped(clip.charAt(i), 0);
                }
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c >= 32 && c != 127 && c != '§' && text.length() < 300) {
            text += c;
        }
        return true;
    }
}
