package com.barbwra.mlum.client.admin;

import com.barbwra.mlum.client.ui.Hits;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.mc.UiBoot;
import com.barbwra.mlum.client.ui.mc.UiHost;
import com.barbwra.mlum.client.ui.mc.UiSounds;
import com.barbwra.mlum.client.ui.mc.UiPage;
import com.barbwra.mlum.client.ui.mc.UiScreens;
import com.barbwra.mlum.client.ui.mc.UiState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The admin panel. Opened with O by anyone the server says is staff; what each page shows comes
 * from {@link ClientAdmin}, and every button only asks the server, which checks again.
 */
@OnlyIn(Dist.CLIENT)
public class AdminScreen extends Screen implements UiPage {

    private final AdminView.Model m = new AdminView.Model();
    private final Map<String, String> fields = new HashMap<>();
    private long confirmAt;
    private int ticks;

    public AdminScreen() {
        super(Component.literal(""));
        m.fields = fields;
        m.sub = 0;
    }

    @Override
    protected void init() {
        UiBoot.ensure();
        UiState.releaseFx();
        refresh();
    }

    private void refresh() {
        ClientAdmin.ask(AdminView.SUBS[m.sub]);
        if (m.sub == 0) {
            ClientAdmin.ask("tickets");
        }
    }

    @Override
    public void tick() {
        // the players page moves on its own (positions, health); ask again every two seconds
        if (++ticks % 40 == 0 && (m.sub == 0 || m.sub == 4 || m.sub == 5)) {
            ClientAdmin.ask(AdminView.SUBS[m.sub]);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiHost.render(graphics, this);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
    }

    @Override
    public int tab() {
        return -1;
    }

    @Override
    public boolean bagScrim() {
        return false;
    }

    @Override
    public Node main(String hover) {
        m.hover = hover;
        m.caret = (UiState.now() / 530L) % 2L == 0L;
        if (m.confirm != null && UiState.now() - confirmAt > 3500L) {
            m.confirm = null;
        }
        return AdminView.build(m);
    }

    /* ================================================================== clicks */

    private static int index(String id) {
        int c = id.lastIndexOf(':');
        try {
            return c < 0 ? -1 : Integer.parseInt(id.substring(c + 1));
        } catch (NumberFormatException bad) {
            return -1;
        }
    }

    private String field(String id) {
        return fields.getOrDefault(id, "").trim();
    }

    private static int number(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    /** Asks twice for anything that cannot be undone. True on the second click. */
    private boolean confirmed(String id) {
        if (id.equals(m.confirm)) {
            m.confirm = null;
            return true;
        }
        m.confirm = id;
        confirmAt = UiState.now();
        return false;
    }

    private CompoundTag target() {
        CompoundTag tag = new CompoundTag();
        if (m.selected != null) {
            tag.putUUID("Target", m.selected);
        }
        return tag;
    }

    @Override
    public boolean mouseClicked(double guiX, double guiY, int button) {
        Hits.Hit hit = UiHost.hitUnderMouse();
        if (button == 0 && UiState.storeClick(hit)) {
            return true;
        }
        if (hit == null || button != 0) {
            m.focus = null;
            return true;
        }
        UiSounds.click();
        String id = hit.id;
        if (id.startsWith("tab:") && hit.data instanceof Integer t) {
            UiScreens.go(-1, t);
            return true;
        }
        if (id.startsWith("input:")) {
            m.focus = id.substring(6);
            return true;
        }
        m.focus = null;
        if (id.startsWith("asub:")) {
            int s = index(id);
            if (s >= 0 && s != m.sub && AdminView.canSee(s)) {
                m.sub = s;
                m.scroll = 0;
                m.confirm = null;
                UiState.startFx(true, false);
                refresh();
            }
            return true;
        }
        click(id);
        return true;
    }

    private void click(String id) {
        switch (id) {
            case "vanish" -> ClientAdmin.send("vanish", new CompoundTag());
            case "tp", "bring", "inv", "spectate" -> {
                if (m.selected != null) {
                    ClientAdmin.send(id, target());
                    if (!id.equals("tp") && !id.equals("bring")) {
                        onClose();
                    }
                }
            }
            case "unspectate" -> ClientAdmin.send("unspectate", new CompoundTag());
            case "deaths" -> {
                if (m.selected != null) {
                    ClientAdmin.ask("deaths", target());
                }
            }
            case "punish", "punish-name" -> {
                CompoundTag tag = id.equals("punish") ? target() : new CompoundTag();
                if (id.equals("punish-name")) {
                    tag.putString("Name", field("pname"));
                }
                tag.putString("Type", m.ptype);
                tag.putString("Reason", field("reason"));
                tag.putInt("Minutes", number(field("minutes"), 0));
                if ((m.ptype.equals("ban") || m.ptype.equals("kick")) && !confirmed(id)) {
                    return;
                }
                ClientAdmin.send("punish", tag);
                fields.remove("reason");
                fields.remove("minutes");
            }
            case "jail-set" -> ClientAdmin.send("jail.set", new CompoundTag());
            case "rank-create" -> {
                CompoundTag tag = new CompoundTag();
                tag.putString("Id", field("rid"));
                tag.putString("Name", field("rname"));
                ClientAdmin.send("rank.create", tag);
                m.rank = field("rid").toLowerCase(java.util.Locale.ROOT);
                fields.remove("rid");
                fields.remove("rname");
            }
            case "rank-delete" -> {
                if (m.rank != null && confirmed(id)) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("Id", m.rank);
                    ClientAdmin.send("rank.delete", tag);
                    m.rank = null;
                }
            }
            case "perm-add" -> {
                if (m.rank != null && !field("custom").isEmpty()) {
                    perm(field("custom"), true);
                    fields.remove("custom");
                }
            }
            case "assign" -> {
                if (m.rank != null && !field("mname").isEmpty()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("Id", m.rank);
                    tag.putString("Name", field("mname"));
                    ClientAdmin.send("rank.assign", tag);
                    fields.remove("mname");
                }
            }
            case "alerts-clear" -> ClientAdmin.send("alerts.clear", new CompoundTag());
            case "restart-in" -> {
                int minutes = number(field("rmin"), -1);
                if (minutes > 0) {
                    CompoundTag tag = new CompoundTag();
                    tag.putInt("Minutes", minutes);
                    ClientAdmin.send("restart.in", tag);
                    fields.remove("rmin");
                }
            }
            case "restart-cancel" -> ClientAdmin.send("restart.cancel", new CompoundTag());
            case "job-add" -> {
                if (!field("jcmd").isEmpty()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("Name", field("jname").isEmpty() ? field("jcmd") : field("jname"));
                    tag.putString("Command", field("jcmd"));
                    tag.putInt("Hour", number(field("jhour"), 0));
                    tag.putInt("Minute", number(field("jmin"), 0));
                    tag.putInt("Days", m.days);
                    ClientAdmin.send("sched.add", tag);
                    fields.remove("jname");
                    fields.remove("jcmd");
                }
            }
            default -> clickIndexed(id);
        }
    }

    private void clickIndexed(String id) {
        int i = index(id);
        if (id.startsWith("pl:")) {
            List<CompoundTag> players = AdminView.list("players", "Players");
            if (i >= 0 && i < players.size()) {
                m.selected = players.get(i).getUUID("Id");
            }
        } else if (id.startsWith("ptype:")) {
            m.ptype = id.substring(6);
        } else if (id.startsWith("restore:")) {
            CompoundTag tag = target();
            tag.putInt("Index", i);
            if (confirmed(id)) {
                ClientAdmin.send("restore", tag);
            }
        } else if (id.startsWith("rk:")) {
            List<CompoundTag> ranks = AdminView.list("ranks", "Ranks");
            if (i >= 0 && i < ranks.size()) {
                m.rank = ranks.get(i).getString("Id");
            }
        } else if (id.startsWith("perm:")) {
            String node = id.substring(5);
            perm(node, !hasPerm(node));
        } else if (id.startsWith("cmd:")) {
            String node = "cmd." + id.substring(4);
            perm(node, !hasPerm(node));
        } else if (id.startsWith("unassign:")) {
            List<CompoundTag> members = AdminView.list("ranks", "Members");
            if (i >= 0 && i < members.size()) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Target", members.get(i).getUUID("Id"));
                ClientAdmin.send("rank.unassign", tag);
            }
        } else if (id.startsWith("lift:")) {
            List<CompoundTag> records = AdminView.list("punish", "Records");
            if (i >= 0 && i < records.size()) {
                CompoundTag tag = new CompoundTag();
                tag.putUUID("Target", records.get(i).getUUID("Id"));
                tag.putString("Type", records.get(i).getString("Type"));
                ClientAdmin.send("lift", tag);
            }
        } else if (id.startsWith("tk-tp:") || id.startsWith("tk-close:")) {
            List<CompoundTag> tickets = AdminView.list("tickets", "Tickets");
            if (i >= 0 && i < tickets.size()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Id", tickets.get(i).getInt("Id"));
                ClientAdmin.send(id.startsWith("tk-tp:") ? "ticket.tp" : "ticket.close", tag);
            }
        } else if (id.startsWith("job-run:") || id.startsWith("job-del:")) {
            List<CompoundTag> jobs = AdminView.list("schedule", "Jobs");
            if (i >= 0 && i < jobs.size()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Id", jobs.get(i).getInt("Id"));
                if (id.startsWith("job-del:") && !confirmed(id)) {
                    return;
                }
                ClientAdmin.send(id.startsWith("job-run:") ? "sched.run" : "sched.remove", tag);
            }
        } else if (id.startsWith("day:")) {
            m.days ^= 1 << i;
        }
    }

    private boolean hasPerm(String node) {
        for (CompoundTag r : AdminView.list("ranks", "Ranks")) {
            if (r.getString("Id").equals(m.rank)) {
                net.minecraft.nbt.ListTag l = r.getList("Perms", net.minecraft.nbt.Tag.TAG_STRING);
                for (int i = 0; i < l.size(); i++) {
                    if (l.getString(i).equals(node)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void perm(String node, boolean on) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", m.rank);
        tag.putString("Node", node);
        tag.putBoolean("On", on);
        ClientAdmin.send("rank.perm", tag);
    }

    @Override
    public boolean mouseScrolled(double guiX, double guiY, double delta) {
        m.scroll = Math.max(0, m.scroll + (delta > 0 ? -1 : 1));
        return true;
    }

    /* ================================================================== typing */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && m.focus == null && UiState.storeEscape()) {
            return true;
        }
        if (m.focus != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                m.focus = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                String s = fields.getOrDefault(m.focus, "");
                if (!s.isEmpty()) {
                    fields.put(m.focus, s.substring(0, s.offsetByCodePoints(s.length(), -1)));
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
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || (minecraft != null && ClientAdmin.OPEN.matches(keyCode, scanCode))) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (m.focus == null || c < 32 || c == 127 || c == '§') {
            return m.focus != null;
        }
        String s = fields.getOrDefault(m.focus, "");
        if (s.length() < 120) {
            fields.put(m.focus, s + c);
        }
        return true;
    }

    /** The selected player, for the view. */
    UUID selected() {
        return m.selected;
    }
}
