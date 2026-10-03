package com.barbwra.mlum.client.screens;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.hud.field.HudPen;
import com.barbwra.mlum.client.ui.mc.UiText;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * The title screen: the vanilla panorama still turning behind, the server's name over it, one big
 * button straight into the server, and the news beside it.
 *
 * <p>The big button joins {@code serverAddress} from the client config directly; with no address
 * it opens the server list. The news is read once from {@code newsUrl} - any plain text file on
 * the web, one line each - so the server owner can change it without touching anyone's game; until
 * it arrives, or if it cannot be reached, the lines in the config are shown.</p>
 */
@OnlyIn(Dist.CLIENT)
public class MlifeTitleScreen extends Screen {

    private final ScreenKit kit = new ScreenKit();
    private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
    private static volatile List<String> fetched;
    private static boolean asked;

    public MlifeTitleScreen() {
        super(Component.translatable("narrator.screen.title"));
    }

    @Override
    protected void init() {
        if (!asked) {
            asked = true;
            fetchNews();
        }
    }

    private static void fetchNews() {
        String url = MlumConfig.newsUrl().trim();
        if (url.isEmpty()) {
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
            client.sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenAccept(resp -> {
                if (resp.statusCode() / 100 != 2) {
                    return;
                }
                List<String> lines = new ArrayList<>();
                for (String line : resp.body().split("\\R")) {
                    if (!line.isBlank() && lines.size() < 8) {
                        lines.add(line.trim());
                    }
                }
                fetched = lines;
            });
        } catch (Exception bad) {
            // no news is fine
        }
    }

    private List<String> news() {
        List<String> f = fetched;
        if (f != null && !f.isEmpty()) {
            return f;
        }
        return new ArrayList<>(MlumConfig.news());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        panorama.render(partialTick, 1.0F);
        kit.begin(g);
        try {
            kit.shade(width, height, 0.85F);
            float cx = width * 0.42F;
            float titleY = height * 0.3F;
            kit.wordmark(MlumConfig.serverName(), cx, titleY, 34.0F);
            kit.pen.text(kit.pen.kufi("سيرفر بقاء عربي", 6.0F, 600), cx, titleY + 13.0F, HudPen.CENTER, ScreenKit.MUTED);

            float bw = 150.0F;
            float x = cx - bw / 2.0F;
            float y = titleY + 28.0F;
            boolean direct = !MlumConfig.serverAddress().isBlank();
            kit.button("play", direct ? "ادخل السيرفر" : "السيرفرات", x, y, bw, 24.0F, ScreenKit.FILLED, mouseX, mouseY);
            y += 30.0F;
            float half = (bw - 5.0F) / 2.0F;
            kit.button("servers", "السيرفرات", x + half + 5.0F, y, half, 17.0F, ScreenKit.GHOST, mouseX, mouseY);
            kit.button("single", "عالم فردي", x, y, half, 17.0F, ScreenKit.GHOST, mouseX, mouseY);
            y += 22.0F;
            kit.button("options", "الإعدادات", x + half + 5.0F, y, half, 17.0F, ScreenKit.GHOST, mouseX, mouseY);
            kit.button("mods", "المودات", x, y, half, 17.0F, ScreenKit.GHOST, mouseX, mouseY);
            y += 22.0F;
            kit.button("quit", "اطلع من اللعبة", x, y, bw, 17.0F, ScreenKit.GHOST, mouseX, mouseY);

            // the news
            float nw = Math.min(170.0F, width * 0.3F);
            float nx = width - 14.0F - nw;
            float ny = height * 0.24F;
            List<String> lines = news();
            float nh = 22.0F + lines.size() * 13.0F;
            kit.panel(nx, ny, nw, nh);
            kit.heading("الأخبار", nx + nw - 6.0F, ny + 11.0F);
            for (int i = 0; i < lines.size(); i++) {
                float ly = ny + 25.0F + i * 13.0F;
                kit.pen.rect(nx + nw - 8.0F, ly - 3.5F, 1.5F, 1.5F, ScreenKit.AMBER);
                kit.pen.text(kit.pen.kufi(UiText.logical(lines.get(i)), 5.5F, 600), nx + nw - 12.0F, ly, HudPen.RIGHT, ScreenKit.SOFT);
            }
            kit.pen.text(kit.pen.pixel("MINECRAFT " + SharedConstants.getCurrentVersion().getName() + " · FORGE", 5.0F, 600),
                    6.0F, height - 6.0F, HudPen.LEFT, ScreenKit.FAINT);
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
            case "play" -> {
                String address = MlumConfig.serverAddress().trim();
                if (address.isEmpty()) {
                    minecraft.setScreen(new JoinMultiplayerScreen(this));
                } else {
                    ServerData data = new ServerData(MlumConfig.serverName(), address, false);
                    ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(address), data, false);
                }
            }
            case "servers" -> minecraft.setScreen(new JoinMultiplayerScreen(this));
            case "single" -> minecraft.setScreen(new SelectWorldScreen(this));
            case "options" -> minecraft.setScreen(new OptionsScreen(this, minecraft.options));
            case "mods" -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this));
            case "quit" -> minecraft.stop();
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
