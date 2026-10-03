package com.barbwra.mlum.faction;

import com.barbwra.mlum.menu.MlumMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The one way a faction vault opens.
 *
 * <p>Two callers: the افتح الخزنة button in the faction tab, which opens the player's own faction's
 * first page, and {@code /openfactionvault}, which an operator or the console uses to open any
 * faction's vault for anyone. Both end here, so there is one path to audit.</p>
 *
 * <p>The page opens in the bag's own menu, in the place a chest takes, so the whole bag is beside
 * it. Turning a page reopens the menu - pages can differ in height and a live menu's slot count is
 * fixed - and the reopen runs every check again. The same membership rule is asked again by the
 * page itself every tick ({@link FactionVaultContainer#stillValid}), so a member who is kicked or
 * leaves loses the screen at once.</p>
 *
 * <p>The purchase timer is not enforced: pages cannot be bought yet, so enforcing it would put every
 * page past the first permanently out of reach.</p>
 */
public final class FactionVaultAccess {

    private FactionVaultAccess() {
    }

    /** A member whose rank may look inside this page. */
    public static boolean mayView(Faction faction, int page, UUID player) {
        return faction.contains(player) && faction.vault().canView(page, faction.roleOf(player));
    }

    /**
     * Every check {@link #open} makes, without opening anything - so a page turn can be refused
     * while the current page is still open, instead of closing it first and finding out after.
     *
     * @param admin opened by an operator's command: membership and rank are not required
     */
    public static boolean canOpen(ServerPlayer viewer, UUID factionId, int page,
                                  @Nullable ServerPlayer notify, boolean admin) {
        return resolve(viewer, factionId, page, notify, admin) != null;
    }

    /**
     * Opens a page of a faction's vault for a player.
     *
     * @param admin opened by an operator's command: membership and rank are not required
     * @return true when the screen was opened; false after telling {@code notify} why not
     */
    public static boolean open(ServerPlayer viewer, UUID factionId, int page,
                               @Nullable ServerPlayer notify, boolean admin) {
        Faction faction = resolve(viewer, factionId, page, notify, admin);
        if (faction == null) {
            return false;
        }
        MinecraftServer server = viewer.getServer();
        FactionData data = FactionData.get(server);
        int level = faction.level();
        int pageCount = Math.max(1, FactionLevel.pageCount(level));
        int rows = FactionLevel.rowsOnPage(level, page);

        FactionVaultContainer container = new FactionVaultContainer(data, faction, page, rows, admin);
        String title = faction.name();
        long now = System.currentTimeMillis();
        long rentLeft = faction.vault().rentLeft(now);
        int bank = faction.bank();
        FactionRole role = faction.contains(viewer.getUUID()) ? faction.roleOf(viewer.getUUID()) : null;
        boolean canPay = role != null && role.canSpendBank();

        // The vault is the bag's own menu with the vault page where a chest would sit, so the
        // player works it with the whole bag beside it, exactly like a chest.
        NetworkHooks.openScreen(viewer, new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(title);
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new MlumMenu(id, inventory, container, rows,
                        new net.minecraft.world.inventory.SimpleContainerData(MlumMenu.DATA_COUNT))
                        .asVault(factionId, page, pageCount, admin);
            }
        }, buf -> {
            // Read back in exactly this order by MlumMenu's client constructor.
            buf.writeByte(rows);
            buf.writeByte(1);
            buf.writeByte(page);
            buf.writeByte(pageCount);
            // the rent, so the screen can show it and offer the plans
            buf.writeLong(rentLeft);
            buf.writeInt(bank);
            buf.writeBoolean(canPay);
            buf.writeVarInt(level);
        });
        return true;
    }

    /** The faction whose page may be opened, or null after telling {@code notify} why not. */
    @Nullable
    private static Faction resolve(ServerPlayer viewer, UUID factionId, int page,
                                   @Nullable ServerPlayer notify, boolean admin) {
        MinecraftServer server = viewer.getServer();
        if (server == null) {
            return null;
        }
        FactionData data = FactionData.get(server);
        Faction faction = data.byId(factionId);
        if (faction == null) {
            tell(notify, "لا توجد هذه المنظمة");
            return null;
        }
        if (!admin && !faction.contains(viewer.getUUID())) {
            tell(notify, "أنت لست في هذه المنظمة");
            return null;
        }

        int level = faction.level();
        int pageCount = Math.max(1, FactionLevel.pageCount(level));
        if (page < 1 || page > pageCount) {
            tell(notify, "هذه الصفحة غير موجودة - المتاح " + pageCount);
            return null;
        }
        if (!admin && !mayView(faction, page, viewer.getUUID())) {
            tell(notify, "رتبتك ما تسمح لك تفتح هذي الصفحة");
            return null;
        }

        int rows = FactionLevel.rowsOnPage(level, page);
        if (rows <= 0) {
            tell(notify, "هذه الصفحة مغلقة - مستوى المنظمة " + level);
            return null;
        }
        // every page past the first is rented, for everyone - operators included
        if (page > 1 && !faction.vault().rentActive(System.currentTimeMillis())) {
            tell(notify, "الصفحة مقفلة - إيجار الخزنة منتهي");
            return null;
        }
        return faction;
    }

    /**
     * Pays one of the rent plans from the faction bank, for the vault the player has open.
     *
     * @return the message to show; starts with "+" on success
     */
    public static String payRent(ServerPlayer viewer, UUID factionId, int plan) {
        MinecraftServer server = viewer.getServer();
        if (server == null || plan < 0 || plan >= FactionLevel.RENT_DAYS.length) {
            return "طلب غلط";
        }
        FactionData data = FactionData.get(server);
        Faction faction = data.byId(factionId);
        if (faction == null || !faction.contains(viewer.getUUID())) {
            return "أنت لست في هذه المنظمة";
        }
        if (!faction.roleOf(viewer.getUUID()).canSpendBank()) {
            return "الدفع للقائد والنائب بس";
        }
        long cost = FactionLevel.rentCost(faction.level(), plan);
        if (cost <= 0) {
            return "ما عندكم صفحات تحتاج إيجار";
        }
        if (cost > Integer.MAX_VALUE || !faction.withdraw((int) cost)) {
            return "فلوس المنظمة ما تكفي";
        }
        faction.vault().extendRent(FactionLevel.RENT_DAYS[plan], System.currentTimeMillis());
        data.setDirty();
        return "+تم دفع إيجار " + FactionLevel.RENT_DAYS[plan] + " يوم";
    }

    private static void tell(@Nullable ServerPlayer player, String message) {
        if (player != null) {
            com.barbwra.mlum.util.Feedback.bad(player, message);
        }
    }
}
