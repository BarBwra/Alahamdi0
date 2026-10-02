package com.barbwra.mlum.client.ui.mc;

import com.barbwra.mlum.MlumConfig;
import com.barbwra.mlum.client.ClientFactionData;
import com.barbwra.mlum.client.ClientLevelData;
import com.barbwra.mlum.client.ClientQuestBoard;
import com.barbwra.mlum.client.ClientSkills;
import com.barbwra.mlum.client.ClientVehicleData;
import com.barbwra.mlum.client.ui.Hits;
import com.barbwra.mlum.client.ui.layout.Node;
import com.barbwra.mlum.client.ui.view.FactionView;
import com.barbwra.mlum.client.ui.view.Item;
import com.barbwra.mlum.client.ui.view.LevelView;
import com.barbwra.mlum.client.ui.view.Overlays;
import com.barbwra.mlum.client.ui.view.QuestsView;
import com.barbwra.mlum.client.ui.view.SkillsView;
import com.barbwra.mlum.client.ui.view.VehiclesView;
import com.barbwra.mlum.faction.FactionLevel;
import com.barbwra.mlum.faction.FactionRole;
import com.barbwra.mlum.network.C2SClaimReward;
import com.barbwra.mlum.network.C2SFactionAction;
import com.barbwra.mlum.network.C2SSkillBuy;
import com.barbwra.mlum.network.C2SVehicleAction;
import com.barbwra.mlum.network.ModNetwork;
import com.barbwra.mlum.network.S2CFactionRoster;
import com.barbwra.mlum.network.S2CFactionState;
import com.barbwra.mlum.network.S2CLevelState;
import com.barbwra.mlum.network.S2CSkills;
import com.barbwra.mlum.quest.QuestEntry;
import com.barbwra.mlum.quest.QuestType;
import com.barbwra.mlum.vehicle.VehicleEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Every menu that is not the bag: quests, level, skills, vehicles, faction.
 *
 * <p>One screen for all five, so moving between them is a change of what is drawn - with the static
 * over the swap - rather than a change of screen. Only reaching the bag replaces this screen,
 * because the bag holds slots the server has to open.</p>
 *
 * <p>Nothing here decides anything. Buttons send the same requests the old screens did and the
 * server answers with fresh state; what is drawn is always rebuilt from those caches.</p>
 */
@OnlyIn(Dist.CLIENT)
public class TabsScreen extends Screen implements UiPage {

    public static final int QUESTS = 1;
    public static final int LEVEL = 2;
    public static final int SKILLS = 3;
    public static final int VEHICLES = 4;
    public static final int FACTION = 5;

    private int tab;

    /* ---- quests ---- */
    private int questSel = -1;
    private int claimingLine = -1;
    private List<QuestEntry> claimBoard;
    private long claimAt;

    /* ---- level ---- */
    private int roadStart = -1;

    /* ---- skills ---- */
    private int confirming = -1;
    private long confirmAt;
    /** The skill whose "drop it" button is asking for a second click, and when it started asking. */
    private int dropping = -1;
    private long dropAt;
    private String buyingId;
    private int buyingVersion;

    /* ---- vehicles ---- */
    private int vehicleSel;
    private int vehicleBusyVersion = -1;
    private long vehicleBusyAt;

    /* ---- faction ---- */
    private int fsub;
    @Nullable
    private String focus;
    private String createName = "";
    private String renameText;
    private String disbandText = "";
    private String donateAmount = "1000";
    @Nullable
    private String modal;
    private int profileIndex = -1;
    @Nullable
    private String confirm;
    private long factionConfirmAt;
    private int boardScroll;
    private int membersScroll;
    private final java.util.Set<UUID> invitedHere = new java.util.HashSet<>();
    private long leaveArmedAt;

    public TabsScreen(int tab) {
        super(Component.literal(""));
        this.tab = Math.max(QUESTS, Math.min(FACTION, tab));
    }

    /** Switch to another tab of this screen. The caller has already started the static. */
    void show(int newTab) {
        tab = Math.max(QUESTS, Math.min(FACTION, newTab));
        modal = null;
        focus = null;
        confirm = null;
        confirming = -1;
        onShow();
    }

    /** Asks the server for whatever this tab shows that it does not push on its own. */
    private void onShow() {
        if (tab == VEHICLES) {
            ModNetwork.CHANNEL.sendToServer(C2SVehicleAction.refresh());
        } else if (tab == FACTION) {
            ModNetwork.sendFactionAction(C2SFactionAction.Action.REFRESH, "");
        } else if (tab == SKILLS) {
            UiState.seenSkills = true;
        }
    }

    /** init() runs again on every window resize; the server is asked for fresh data only once. */
    private boolean opened;

    @Override
    protected void init() {
        UiBoot.ensure();
        UiState.releaseFx();
        if (!opened) {
            opened = true;
            onShow();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (tab == VEHICLES) {
            ClientVehicleData.tick();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiHost.render(graphics, this);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // the scrim is part of the frame
    }

    /* ================================================================== UiPage */

    @Override
    public int tab() {
        return tab;
    }

    @Override
    public boolean bagScrim() {
        return false;
    }

    @Override
    public Node main(String hover) {
        return switch (tab) {
            case QUESTS -> QuestsView.build(quests(hover));
            case LEVEL -> LevelView.build(level(hover));
            case SKILLS -> SkillsView.build(skills(hover));
            case VEHICLES -> VehiclesView.build(vehicles(hover));
            default -> FactionView.build(faction(hover));
        };
    }

    @Override
    public Node modal(String hover) {
        // the store belongs to the top bar, so it outranks whatever this tab wanted to show
        Node store = UiState.storeModal(hover);
        if (store != null) {
            return store;
        }
        if (tab != FACTION || modal == null || !ClientFactionData.inFaction()) {
            if (tab == FACTION && modal != null && !ClientFactionData.inFaction()) {
                modal = null;
            }
            return null;
        }
        List<FactionView.Member> members = members();
        switch (modal) {
            case "donate" -> {
                return Overlays.modal("تبرع للخزينة", Overlays.donateBody(donateAmount, "donate".equals(focus),
                        caretOn(), UiState.money(), null, hover), hover);
            }
            case "invite" -> {
                List<Overlays.Player> players = new ArrayList<>();
                for (S2CFactionRoster.Entry e : ClientFactionData.roster()) {
                    if (e.id().equals(selfId())) {
                        continue;
                    }
                    Overlays.Player p = new Overlays.Player();
                    p.id = e.id().toString();
                    p.name = e.name();
                    p.sent = e.invited() || invitedHere.contains(e.id());
                    p.faction = e.factionName().isEmpty() ? null : UiText.logical(e.factionName());
                    players.add(p);
                }
                return Overlays.modal("دعوة لاعب", Overlays.inviteBody(players, !ClientFactionData.rosterLoaded(), hover), hover);
            }
            case "profile" -> {
                if (profileIndex < 0 || profileIndex >= members.size()) {
                    modal = null;
                    return null;
                }
                FactionView.Member mb = members.get(profileIndex);
                int mine = myRank();
                boolean canAct = !mb.me && mine <= FactionRole.OFFICER.ordinal() && mine < mb.rank;
                boolean canPromote = canAct && mb.rank - 1 > mine && mb.rank - 1 > FactionRole.LEADER.ordinal();
                boolean canDemote = canAct && mb.rank < FactionRole.GUEST.ordinal();
                return Overlays.modal(mb.name, Overlays.profileBody(mb, canAct, canPromote, canDemote, liveConfirm(), hover), hover);
            }
            case "transfer" -> {
                return Overlays.modal("نقل القيادة", Overlays.transferBody(members, 0, liveConfirm(), hover), hover);
            }
            default -> {
                return null;
            }
        }
    }

    private boolean caretOn() {
        return (UiState.now() / 530L) % 2L == 0L;
    }

    /* ================================================================== quests */

    private QuestsView.Model quests(String hover) {
        QuestsView.Model m = new QuestsView.Model();
        List<QuestEntry> board = ClientQuestBoard.all();
        int firstActive = -1;
        for (QuestEntry e : board) {
            QuestsView.Quest q = new QuestsView.Quest();
            q.line = e.line();
            q.type = e.type() == QuestType.DAILY ? QuestsView.DAILY : e.type() == QuestType.SIDE ? QuestsView.SIDE : QuestsView.MAIN;
            q.title = UiText.logical(e.title());
            q.description = UiText.logical(e.description());
            q.progress = e.progress();
            q.max = Math.max(1, e.max());
            q.done = e.isComplete();
            q.claimed = ClientQuestBoard.isClaimed(e.line());
            for (ItemStack stack : e.rewards()) {
                Item it = McItems.withCount(stack, stack.getCount());
                if (it != null) {
                    QuestsView.Reward r = new QuestsView.Reward();
                    r.item = it;
                    r.count = stack.getCount();
                    q.rewards.add(r);
                }
            }
            if (firstActive < 0 && !q.done) {
                firstActive = m.quests.size();
            }
            m.quests.add(q);
        }
        if (questSel < 0 || questSel >= m.quests.size()) {
            questSel = Math.max(0, firstActive);
        }
        m.selected = questSel;
        m.hover = hover;
        m.claiming = claimingLine >= 0 && board == claimBoard && UiState.now() - claimAt < 3000L;
        if (!m.claiming) {
            claimingLine = -1;
        }
        return m;
    }

    /* ================================================================== level */

    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    private static ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id == null ? "" : id, key -> {
            ResourceLocation rl = ResourceLocation.tryParse(key);
            net.minecraft.world.item.Item item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            return new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item);
        });
    }

    private LevelView.Model level(String hover) {
        LevelView.Model m = new LevelView.Model();
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        m.level = ClientLevelData.level();
        m.points = p == null ? ClientLevelData.points() : p.totalExperience;
        m.cur = ClientLevelData.inLevel();
        m.need = Math.max(1, ClientLevelData.costOfNext());
        for (S2CLevelState.Rule r : ClientLevelData.rules()) {
            m.rules.add(new LevelView.Rule(r.value(), r.kind(), UiText.logical(r.label()), r.off()));
        }
        for (S2CLevelState.Tier t : ClientLevelData.track()) {
            LevelView.Milestone ms = new LevelView.Milestone();
            ms.level = t.level();
            ms.item = McItems.withCount(icon(t.icon()), 1);
            ms.name = UiText.logical(t.description());
            m.road.add(ms);
        }
        int total = m.road.size();
        if (roadStart < 0) {
            int next = total;
            for (int i = 0; i < total; i++) {
                if (m.road.get(i).level > m.level) {
                    next = i;
                    break;
                }
            }
            roadStart = Math.max(0, Math.min(Math.max(0, total - 8), next - 3));
        }
        roadStart = Math.max(0, Math.min(Math.max(0, total - 8), roadStart));
        m.roadStart = roadStart;
        m.hover = hover;
        return m;
    }

    /* ================================================================== skills */

    private SkillsView.Model skills(String hover) {
        SkillsView.Model m = new SkillsView.Model();
        List<S2CSkills.Entry> list = ClientSkills.skills();
        for (int i = 0; i < list.size(); i++) {
            S2CSkills.Entry e = list.get(i);
            SkillsView.Perk p = new SkillsView.Perk();
            p.id = e.id();
            p.name = UiText.logical(e.name());
            p.description = UiText.logical(e.description());
            p.icon = e.icon().isEmpty() ? "bolt" : e.icon();
            for (int k = 0; k < 3; k++) {
                p.effects[k] = k < e.effects().size() ? UiText.logical(e.effects().get(k)) : "";
                p.prices[k] = k < e.prices().length ? e.prices()[k] : 0L;
            }
            p.level = e.level();
            p.soon = e.soon();
            m.perks.add(p);
            if (buyingId != null && buyingId.equals(e.id()) && buyingVersion == ClientSkills.version()) {
                m.buying = i;
            }
        }
        if (buyingId != null && buyingVersion != ClientSkills.version()) {
            buyingId = null;
        }
        if (confirming >= 0 && UiState.now() - confirmAt > 3500L) {
            confirming = -1;
        }
        if (dropping >= 0 && UiState.now() - dropAt > 3500L) {
            dropping = -1;
        }
        m.maxedCap = ClientSkills.maxedCap();
        m.money = UiState.money();
        m.dropCost = ClientSkills.dropCost();
        m.confirming = confirming;
        m.dropping = dropping;
        m.hover = hover;
        return m;
    }

    /* ================================================================== vehicles */

    private VehiclesView.Model vehicles(String hover) {
        VehiclesView.Model m = new VehiclesView.Model();
        List<VehicleEntry> owned = ClientVehicleData.owned();
        String active = ClientVehicleData.hasActive() ? ClientVehicleData.activeEntity() : "";
        long cooldown = ClientVehicleData.cooldownTicks();
        for (VehicleEntry e : owned) {
            VehiclesView.Vehicle v = new VehiclesView.Vehicle();
            v.id = e.entityId();
            v.entityId = e.entityId();
            v.name = UiText.logical(e.label().getString());
            MlumConfig.VehicleDisplay display = MlumConfig.vehicleDisplays().get(e.entityId());
            v.subtitle = display == null ? "" : UiText.logical(display.subtitle());
            v.image = McImage.vehicle(e.entityId(), v.name);
            v.limited = e.isConsumable();
            v.left = e.count();
            v.available = e.isAvailable();
            boolean armed = v.subtitle.contains("دبابة") || v.subtitle.contains("مسلح") || v.subtitle.contains("قتال");
            if (display != null) {
                for (String[] stat : display.stats()) {
                    String label = UiText.logical(stat[0]);
                    String value = UiText.logical(stat[1]);
                    if (label.contains("سلاح") || label.contains("مسلح") || label.equalsIgnoreCase("armed")) {
                        armed = !(value.equals("لا") || value.equals("0") || value.equalsIgnoreCase("no"));
                        continue;
                    }
                    VehiclesView.Stat s = new VehiclesView.Stat();
                    s.label = label;
                    s.value = value;
                    int n = parseInt(value);
                    if (label.contains("سرعة") && n >= 0) {
                        s.bar = Math.min(1.0F, n / 120.0F);
                    } else if (label.contains("مقاعد") && n >= 0) {
                        s.seats = Math.min(6, n);
                    }
                    v.stats.add(s);
                }
            }
            v.armed = armed;
            if (!active.isEmpty() && active.equals(e.entityId())) {
                v.state = VehiclesView.OUT;
            } else if (cooldown > 0L) {
                v.state = VehiclesView.WAIT;
                v.cooldown = (int) ((cooldown + 19L) / 20L);
            } else {
                v.state = VehiclesView.STORED;
            }
            m.vehicles.add(v);
        }
        vehicleSel = Math.max(0, Math.min(Math.max(0, m.vehicles.size() - 1), vehicleSel));
        m.selected = vehicleSel;
        m.hover = hover;
        m.busy = vehicleBusyVersion == ClientVehicleData.version() && UiState.now() - vehicleBusyAt < 3000L;
        if (!m.vehicles.isEmpty()) {
            VehiclesView.Vehicle v = m.vehicles.get(m.selected);
            m.anotherOut = !active.isEmpty() && v.state != VehiclesView.OUT;
            if (m.anotherOut) {
                m.blocked = "عندك مركبة برا · خزّنها أول";
            } else if (!v.available) {
                m.blocked = "مود المركبة مو مثبت";
            } else if (v.limited && v.left <= 0) {
                m.blocked = "خلص العدد";
            } else if (ClientVehicleData.combatLocked()) {
                m.blocked = "ممنوع وأنت في قتال";
            } else if (!ClientVehicleData.blockKey().isEmpty()) {
                m.blocked = blockText(ClientVehicleData.blockKey());
            }
        }
        return m;
    }

    private static String blockText(String key) {
        if (key.endsWith("wrong_dimension")) {
            return "ما تقدر تستدعي في هذا العالم";
        }
        if (key.endsWith("blocked_zone")) {
            return "منطقة ممنوع فيها الاستدعاء";
        }
        return UiText.logical(Component.translatable(key).getString());
    }

    private static int parseInt(String s) {
        StringBuilder digits = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (c >= '٠' && c <= '٩') {
                digits.append((char) ('0' + (c - '٠')));
            } else if (digits.length() > 0) {
                break;
            }
        }
        try {
            return digits.length() == 0 ? -1 : Integer.parseInt(digits.toString());
        } catch (NumberFormatException tooBig) {
            return -1;
        }
    }

    /* ================================================================== faction */

    private UUID selfId() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0L, 0L);
    }

    private int myRank() {
        return FactionRole.byName(ClientFactionData.myRole()).ordinal();
    }

    private List<FactionView.Member> members() {
        List<FactionView.Member> out = new ArrayList<>();
        UUID self = selfId();
        for (S2CFactionState.Member s : ClientFactionData.members()) {
            FactionView.Member mb = new FactionView.Member();
            FactionRole role = FactionRole.byName(s.role());
            mb.id = s.id().toString();
            mb.name = s.name();
            mb.role = role.arabic();
            mb.rank = role.ordinal();
            mb.level = s.playerLevel();
            mb.points = s.contributed();
            mb.donated = s.donated();
            mb.online = s.online();
            mb.me = s.id().equals(self);
            out.add(mb);
        }
        return out;
    }

    private FactionView.Model faction(String hover) {
        FactionView.Model m = new FactionView.Model();
        m.inFaction = ClientFactionData.inFaction();
        m.sub = fsub;
        m.name = UiText.logical(ClientFactionData.factionName());
        m.members.addAll(members());
        for (FactionView.Member mb : m.members) {
            if (mb.rank == FactionRole.LEADER.ordinal()) {
                m.leader = mb.name;
            }
        }
        m.founded = founded(ClientFactionData.createdAt());
        int level = ClientFactionData.level();
        m.level = level;
        m.points = ClientFactionData.points();
        m.floor = ClientFactionData.pointsFloor();
        m.next = ClientFactionData.pointsForNext();
        m.maxLevel = level >= FactionLevel.MAX_LEVEL;
        m.bank = ClientFactionData.bank();
        m.rows = FactionLevel.totalRows(level);
        m.pages = FactionLevel.pageCount(level);
        m.rank = ClientFactionData.serverRank();
        m.factionCount = ClientFactionData.factionCount();
        for (S2CFactionState.Standing s : ClientFactionData.top()) {
            FactionView.Standing st = new FactionView.Standing();
            st.name = UiText.logical(s.name());
            st.level = s.level();
            st.points = s.points();
            st.bank = s.bank();
            st.members = s.members();
            st.mine = s.mine();
            m.board.add(st);
        }
        int first = Math.max(1, Math.min(level - 2, FactionLevel.MAX_LEVEL - 5));
        for (int l = first; l < first + 6 && l <= FactionLevel.MAX_LEVEL; l++) {
            FactionView.LevelCard lc = new FactionView.LevelCard();
            lc.level = l;
            lc.rows = FactionLevel.totalRows(l);
            lc.pages = FactionLevel.pageCount(l);
            lc.cost = FactionLevel.requirementFor(l);
            m.levels.add(lc);
        }
        if (ClientFactionData.invited()) {
            FactionView.Invite in = new FactionView.Invite();
            in.faction = UiText.logical(ClientFactionData.inviteFrom());
            in.from = ClientFactionData.inviteBy();
            in.when = ago(ClientFactionData.inviteAt());
            m.invites.add(in);
        }
        m.createCost = ClientFactionData.createCost();
        m.money = UiState.money();
        int mine = myRank();
        m.canInvite = m.inFaction && mine <= FactionRole.OFFICER.ordinal();
        m.leaderMe = m.inFaction && mine == FactionRole.LEADER.ordinal();
        if (renameText == null && m.inFaction) {
            renameText = m.name;
        }
        m.createName = createName;
        m.renameText = renameText == null ? "" : renameText;
        m.disbandText = disbandText;
        m.focus = focus;
        m.caretOn = caretOn();
        m.hover = hover;
        m.boardScroll = boardScroll;
        m.membersScroll = membersScroll;
        return m;
    }

    private static String founded(long createdAt) {
        if (createdAt <= 0L) {
            return "";
        }
        long days = (System.currentTimeMillis() - createdAt) / 86_400_000L;
        if (days <= 0L) {
            return "تأسست اليوم";
        }
        if (days == 1L) {
            return "تأسست أمس";
        }
        return "تأسست قبل " + days + " يوم";
    }

    private static String ago(long at) {
        if (at <= 0L) {
            return "";
        }
        long minutes = Math.max(0L, (System.currentTimeMillis() - at) / 60_000L);
        if (minutes < 1L) {
            return "الحين";
        }
        if (minutes < 60L) {
            return "قبل " + minutes + " دقايق";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return "قبل " + hours + " ساعة";
        }
        return "قبل " + (hours / 24L) + " يوم";
    }

    @Nullable
    private String liveConfirm() {
        if (confirm != null && UiState.now() - factionConfirmAt > 4000L) {
            confirm = null;
        }
        return confirm;
    }

    /* ================================================================== clicks */

    @Override
    public boolean mouseClicked(double guiX, double guiY, int button) {
        Hits.Hit hit = UiHost.hitUnderMouse();
        if (button == 0 && UiState.storeClick(hit)) {
            return true;
        }
        if (hit == null || button != 0) {
            if (hit == null && focus != null) {
                focus = null;
            }
            return true;
        }
        String id = hit.id;
        if (id.startsWith("tab:") && hit.data instanceof Integer t) {
            UiScreens.go(tab, t);
            return true;
        }
        if (id.equals("nav:a") || id.equals("nav:d")) {
            UiState.keyHit(id.charAt(4));
            UiScreens.go(tab, UiScreens.step(tab, id.equals("nav:a") ? 1 : -1));
            return true;
        }
        if (!id.startsWith("input:")) {
            focus = null;
        }
        switch (tab) {
            case QUESTS -> clickQuests(hit);
            case LEVEL -> {
            }
            case SKILLS -> clickSkills(hit);
            case VEHICLES -> clickVehicles(hit);
            default -> clickFaction(hit);
        }
        return true;
    }

    private void clickQuests(Hits.Hit hit) {
        if (hit.id.startsWith("q:") && hit.data instanceof Integer i) {
            questSel = i;
        } else if (hit.id.equals("claim") && hit.data instanceof Integer line && claimingLine < 0) {
            ModNetwork.CHANNEL.sendToServer(new C2SClaimReward(line));
            claimingLine = line;
            claimBoard = ClientQuestBoard.all();
            claimAt = UiState.now();
        }
    }

    private void clickSkills(Hits.Hit hit) {
        if (!(hit.data instanceof Integer i)) {
            return;
        }
        List<S2CSkills.Entry> list = ClientSkills.skills();
        if (i < 0 || i >= list.size() || buyingId != null || list.get(i).soon()) {
            return;
        }
        if (hit.id.startsWith("drop:")) {
            if (dropping != i) {
                dropping = i;
                dropAt = UiState.now();
                return;
            }
            dropping = -1;
            buyingId = list.get(i).id();
            buyingVersion = ClientSkills.version();
            ModNetwork.CHANNEL.sendToServer(new C2SSkillBuy(buyingId, true));
            return;
        }
        if (!hit.id.startsWith("buy:")) {
            return;
        }
        if (confirming != i) {
            confirming = i;
            confirmAt = UiState.now();
            return;
        }
        confirming = -1;
        buyingId = list.get(i).id();
        buyingVersion = ClientSkills.version();
        ModNetwork.CHANNEL.sendToServer(new C2SSkillBuy(buyingId));
    }

    private void clickVehicles(Hits.Hit hit) {
        if (hit.id.startsWith("v:") && hit.data instanceof Integer i) {
            vehicleSel = i;
            return;
        }
        if (!hit.id.equals("veh")) {
            return;
        }
        List<VehicleEntry> owned = ClientVehicleData.owned();
        if (vehicleSel < 0 || vehicleSel >= owned.size()) {
            return;
        }
        VehicleEntry e = owned.get(vehicleSel);
        String active = ClientVehicleData.hasActive() ? ClientVehicleData.activeEntity() : "";
        if (!active.isEmpty() && active.equals(e.entityId())) {
            ModNetwork.CHANNEL.sendToServer(C2SVehicleAction.store());
        } else {
            ModNetwork.CHANNEL.sendToServer(C2SVehicleAction.summon(e.entityId()));
        }
        vehicleBusyVersion = ClientVehicleData.version();
        vehicleBusyAt = UiState.now();
    }

    private void clickFaction(Hits.Hit hit) {
        String id = hit.id;
        if (modal != null) {
            clickModal(hit);
            return;
        }
        if (id.startsWith("fsub:") && hit.data instanceof Integer i) {
            if (i != fsub) {
                fsub = i;
                UiState.startFx(true, false);
            }
            return;
        }
        if (id.startsWith("input:")) {
            focus = id.substring(6);
            return;
        }
        switch (id) {
            case "donate" -> {
                modal = "donate";
                focus = "donate";
            }
            case "invite" -> {
                modal = "invite";
                ClientFactionData.clearRoster();
                ModNetwork.sendFactionAction(C2SFactionAction.Action.ROSTER, "");
            }
            case "open-vault" -> {
                UiState.startFx(true, true);
                ModNetwork.sendFactionAction(C2SFactionAction.Action.OPEN_VAULT, "");
            }
            case "save-name" -> {
                if (renameText != null && renameText.trim().length() >= 3) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.RENAME, renameText.trim());
                }
            }
            case "transfer" -> {
                modal = "transfer";
                confirm = null;
            }
            case "leave" -> {
                if (UiState.now() - leaveArmedAt < 3000L) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.LEAVE, "");
                    leaveArmedAt = 0L;
                } else {
                    leaveArmedAt = UiState.now();
                    UiState.toast("اضغط {b}مغادرة{/b} مرة ثانية للتأكيد", true);
                }
            }
            case "dissolve" -> ModNetwork.sendFactionAction(C2SFactionAction.Action.DISBAND, disbandText.trim());
            case "create" -> {
                if (createName.trim().length() >= 3) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.CREATE, createName.trim());
                    UiState.startFx(true, false);
                }
            }
            case "join" -> {
                ModNetwork.sendFactionAction(C2SFactionAction.Action.JOIN, "");
                UiState.startFx(true, false);
            }
            case "reject" -> ModNetwork.sendFactionAction(C2SFactionAction.Action.REJECT, "");
            default -> {
                if (id.startsWith("member:") && hit.data instanceof Integer i) {
                    profileIndex = i;
                    modal = "profile";
                    confirm = null;
                }
            }
        }
    }

    private void clickModal(Hits.Hit hit) {
        String id = hit.id;
        if (id.equals("close") || id.equals("modal-bg")) {
            modal = null;
            focus = null;
            confirm = null;
            return;
        }
        if (id.startsWith("input:")) {
            focus = id.substring(6);
            return;
        }
        List<FactionView.Member> members = members();
        switch (modal == null ? "" : modal) {
            case "donate" -> {
                if (id.startsWith("amt:")) {
                    String v = id.substring(4);
                    donateAmount = v.equals("all") ? String.valueOf(UiState.money()) : v;
                    focus = "donate";
                } else if (id.equals("donate-ok")) {
                    long amount = parseLong(donateAmount);
                    if (amount > 0L) {
                        ModNetwork.sendFactionAction(C2SFactionAction.Action.DONATE, String.valueOf(amount));
                        modal = null;
                        focus = null;
                    }
                }
            }
            case "invite" -> {
                if (id.equals("invite-one") && hit.data instanceof Integer i) {
                    List<S2CFactionRoster.Entry> roster = new ArrayList<>();
                    for (S2CFactionRoster.Entry e : ClientFactionData.roster()) {
                        if (!e.id().equals(selfId())) {
                            roster.add(e);
                        }
                    }
                    if (i >= 0 && i < roster.size()) {
                        UUID target = roster.get(i).id();
                        invitedHere.add(target);
                        ModNetwork.sendFactionAction(C2SFactionAction.Action.INVITE, target.toString());
                    }
                }
            }
            case "profile" -> {
                if (profileIndex < 0 || profileIndex >= members.size()) {
                    return;
                }
                FactionView.Member mb = members.get(profileIndex);
                if (id.equals("promote") || id.equals("demote") || id.equals("kick")) {
                    if (!id.equals(liveConfirm())) {
                        confirm = id;
                        factionConfirmAt = UiState.now();
                        return;
                    }
                    C2SFactionAction.Action action = id.equals("promote") ? C2SFactionAction.Action.PROMOTE
                            : id.equals("demote") ? C2SFactionAction.Action.DEMOTE : C2SFactionAction.Action.KICK;
                    ModNetwork.sendFactionAction(action, mb.id);
                    confirm = null;
                    if (action == C2SFactionAction.Action.KICK) {
                        modal = null;
                    }
                }
            }
            case "transfer" -> {
                if (id.equals("transfer-to") && hit.data instanceof Integer i && i >= 0 && i < members.size()) {
                    String key = "transfer-to:" + i;
                    if (!key.equals(liveConfirm())) {
                        confirm = key;
                        factionConfirmAt = UiState.now();
                        return;
                    }
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.TRANSFER, members.get(i).id);
                    confirm = null;
                    modal = null;
                }
            }
            default -> {
            }
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException bad) {
            return -1L;
        }
    }

    @Override
    public boolean mouseScrolled(double guiX, double guiY, double delta) {
        int dir = delta > 0 ? -1 : 1;
        if (tab == LEVEL) {
            roadStart = Math.max(0, roadStart + dir);
        } else if (tab == FACTION && modal == null) {
            if (fsub == 1) {
                membersScroll = Math.max(0, Math.min(Math.max(0, ClientFactionData.members().size() - 9), membersScroll + dir));
            } else {
                boardScroll = Math.max(0, Math.min(Math.max(0, ClientFactionData.top().size() - 9), boardScroll + dir));
            }
        } else if (tab == QUESTS) {
            int n = ClientQuestBoard.all().size();
            if (n > 0) {
                questSel = Math.max(0, Math.min(n - 1, questSel + dir));
            }
        } else if (tab == VEHICLES) {
            int n = ClientVehicleData.owned().size();
            if (n > 0) {
                vehicleSel = Math.max(0, Math.min(n - 1, vehicleSel + dir));
            }
        }
        return true;
    }

    /* ================================================================== keys and typing */

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && focus == null && UiState.storeEscape()) {
            return true;
        }
        if (focus != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focus = null;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                edit(s -> s.isEmpty() ? s : s.substring(0, s.offsetByCodePoints(s.length(), -1)));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitFocused();
                return true;
            }
            if (Screen.isPaste(keyCode) && minecraft != null) {
                String clip = minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    for (int i = 0; i < clip.length(); i++) {
                        typeChar(clip.charAt(i));
                    }
                }
                return true;
            }
            return true;   // every other key belongs to the text box while it has focus
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (modal != null) {
                modal = null;
                confirm = null;
                return true;
            }
            onClose();
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode) && modal == null) {
            onClose();
            return true;
        }
        int dir = UiScreens.navDirection(keyCode, scanCode);
        if (dir != 0 && modal == null) {
            UiState.keyHit(dir > 0 ? 'a' : 'd');
            UiScreens.go(tab, UiScreens.step(tab, dir));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (focus == null) {
            return false;
        }
        typeChar(c);
        return true;
    }

    private void typeChar(char c) {
        if (c < 32 || c == 127 || c == '§') {
            return;
        }
        if ("donate".equals(focus)) {
            if (c >= '0' && c <= '9') {
                edit(s -> s.length() >= 9 ? s : s + c);
            } else if (c >= '٠' && c <= '٩') {
                edit(s -> s.length() >= 9 ? s : s + (char) ('0' + (c - '٠')));
            }
            return;
        }
        edit(s -> s.length() >= 24 ? s : s + c);
    }

    private void edit(java.util.function.UnaryOperator<String> change) {
        if (focus == null) {
            return;
        }
        switch (focus) {
            case "create" -> createName = change.apply(createName);
            case "rename" -> renameText = change.apply(renameText == null ? "" : renameText);
            case "disband" -> disbandText = change.apply(disbandText);
            case "donate" -> donateAmount = change.apply(donateAmount);
            default -> {
            }
        }
    }

    private void submitFocused() {
        String f = focus;
        if (f == null) {
            return;
        }
        switch (f) {
            case "create" -> {
                if (createName.trim().length() >= 3) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.CREATE, createName.trim());
                }
            }
            case "rename" -> {
                if (renameText != null && renameText.trim().length() >= 3) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.RENAME, renameText.trim());
                }
            }
            case "disband" -> {
                if (disbandText.trim().equals(UiText.logical(ClientFactionData.factionName()))) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.DISBAND, disbandText.trim());
                }
            }
            case "donate" -> {
                long amount = parseLong(donateAmount);
                if (amount > 0L) {
                    ModNetwork.sendFactionAction(C2SFactionAction.Action.DONATE, String.valueOf(amount));
                    modal = null;
                }
            }
            default -> {
            }
        }
        focus = null;
    }
}
