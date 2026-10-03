package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.ClientFactionData;
import com.barbwra.mlum.client.admin.AdminScreen;
import com.barbwra.mlum.client.admin.ClientAdmin;
import com.barbwra.mlum.client.admin.TicketScreen;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.mc.UiState;
import com.barbwra.mlum.client.ui.mc.UiText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The ESC menu in the bag's look.
 *
 * <pre>
 *                         M L I F E
 *    ┌── السيرفر ──────┐     [ رجوع للعبة   ]
 *    │ متصلين   12     │     [ الإنجازات    ]
 *    │ وقتك اليوم 1:20 │     [ تذكرة دعم    ]
 *    │ فلوسك  24,350   │     [ لوحة الإدارة ]   staff only
 *    │ منظمتك  الصقور  │     [ الإعدادات    ]
 *    └─────────────────┘     [ المودات      ]
 *                            [ اطلع          ]
 * </pre>
 *
 * <p>The world stays visible behind a shade. Leaving does exactly what the vanilla button does:
 * a local world is saved first, and a server drops you back at the server list.</p>
 */
@OnlyIn(Dist.CLIENT)
public class MlifePauseScreen extends Screen {

    private final ScreenKit kit = new ScreenKit();
    private static long sessionStart = System.currentTimeMillis();

    public MlifePauseScreen() {
        super(Component.translatable("menu.game"));
    }

    /** Called when a world or server is joined, for "your time today". */
    public static void sessionStarted() {
        sessionStart = System.currentTimeMillis();
    }

    private List<String[]> buttons() {
        List<String[]> b = new ArrayList<>();
        b.add(new String[]{"back", "رجوع للعبة"});
        b.add(new String[]{"advancements", "الإنجازات"});
        if (minecraft != null && minecraft.getConnection() != null && !minecraft.isLocalServer()) {
            b.add(new String[]{"ticket", "تذكرة دعم"});
        }
        if (ClientAdmin.staff() && ClientAdmin.has("panel")) {
            b.add(new String[]{"admin", "لوحة الإدارة"});
        }
        if (minecraft != null && minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished()) {
            b.add(new String[]{"lan", "افتح للشبكة"});
        }
        b.add(new String[]{"options", "الإعدادات"});
        b.add(new String[]{"mods", "المودات"});
        b.add(new String[]{"quit", minecraft != null && minecraft.isLocalServer() ? "احفظ واطلع" : "اطلع من السيرفر"});
        return b;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        kit.begin(g);
        try {
            kit.shade(width, height, 1.0F);
            float cx = width / 2.0F;
            kit.wordmark(MlumConfig.serverName(), cx, height * 0.2F, 26.0F);
            kit.pen.text(kit.pen.kufi("اللعبة واقفة", 5.5F, 600), cx, height * 0.2F + 11.0F, HudPen.CENTER, ScreenKit.MUTED);

            List<String[]> list = buttons();
            float bw = 140.0F;
            float bh = 18.0F;
            float gap = 5.0F;
            float total = list.size() * bh + (list.size() - 1) * gap;
            float top = Math.max(height * 0.28F, height / 2.0F - total / 2.0F + 8.0F);
            float bx = cx + 10.0F;
            for (int i = 0; i < list.size(); i++) {
                String id = list.get(i)[0];
                int style = id.equals("back") ? ScreenKit.FILLED : id.equals("quit") ? ScreenKit.DANGER : ScreenKit.GHOST;
                kit.button(id, list.get(i)[1], bx, top + i * (bh + gap), bw, bh, style, mouseX, mouseY);
            }

            // what is going on, beside the buttons
            float pw = 130.0F;
            float px = cx - 10.0F - pw;
            float ph = 86.0F;
            kit.panel(px, top, pw, ph);
            kit.heading(minecraft != null && minecraft.isLocalServer() ? "عالمك" : "السيرفر", px + pw - 6.0F, top + 11.0F);
            int online = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getOnlinePlayers().size() : 1;
            long minutes = (System.currentTimeMillis() - sessionStart) / 60_000L;
            String faction = ClientFactionData.inFaction() ? UiText.logical(ClientFactionData.factionName()) : "بدون";
            String[][] rows = {
                    {"المتصلين", String.valueOf(online)},
                    {"وقتك اليوم", (minutes / 60) + ":" + String.format("%02d", minutes % 60)},
                    {"فلوسك", String.format(java.util.Locale.US, "%,d", UiState.money())},
                    {"منظمتك", faction}};
            for (int i = 0; i < rows.length; i++) {
                float y = top + 26.0F + i * 14.0F;
                kit.pen.text(kit.pen.kufi(rows[i][0], 5.5F, 600), px + pw - 8.0F, y, HudPen.RIGHT, ScreenKit.MUTED);
                boolean latin = rows[i][1].chars().allMatch(c -> c < 128);
                kit.pen.text(latin ? kit.pen.pixel(rows[i][1], 8.0F, 700) : kit.pen.kufi(rows[i][1], 5.5F, 700),
                        px + 8.0F, y + 0.5F, HudPen.LEFT, i == 2 ? ScreenKit.SAGE : ScreenKit.BONE);
                if (i < rows.length - 1) {
                    kit.pen.rect(px + 6.0F, y + 5.0F, pw - 12.0F, 0.5F, ScreenKit.LINE_SOFT);
                }
            }
            kit.pen.text(kit.pen.kufi("Esc يرجعك للعبة", 4.5F, 600), cx, height - 12.0F, HudPen.CENTER, ScreenKit.FAINT);
        } finally {
            kit.end();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        String id = button == 0 ? kit.hit(mx, my) : null;
        if (id == null || minecraft == null) {
            return true;
        }
        ScreenKit.click();
        switch (id) {
            case "back" -> onClose();
            case "advancements" -> {
                if (minecraft.player != null) {
                    minecraft.setScreen(new AdvancementsScreen(minecraft.player.connection.getAdvancements()));
                }
            }
            case "ticket" -> minecraft.setScreen(new TicketScreen());
            case "admin" -> minecraft.setScreen(new AdminScreen());
            case "lan" -> minecraft.setScreen(new ShareToLanScreen(this));
            case "options" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case "mods" -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this));
            case "quit" -> disconnect();
            default -> {
            }
        }
        return true;
    }

    /** Vanilla's own leaving, step for step. */
    private void disconnect() {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        boolean local = minecraft.isLocalServer();
        minecraft.level.disconnect();
        if (local) {
            minecraft.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
        } else {
            minecraft.clearLevel();
        }
        TitleScreen title = new TitleScreen();
        minecraft.setScreen(local ? title : new JoinMultiplayerScreen(title));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
