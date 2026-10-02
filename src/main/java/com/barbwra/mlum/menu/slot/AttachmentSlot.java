package com.barbwra.mlum.menu.slot;

import com.barbwra.mlum.compat.TaczAttachments;
import com.barbwra.mlum.menu.AttachmentContainer;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;

/**
 * One mounting point on one firearm.
 *
 * <p>The slot enforces three things, in the order a player runs into them: there has to be a gun in
 * the firearm cell beside it, the gun has to have that kind of mounting point, and the attachment
 * has to be one this particular gun accepts. All three are TACZ's answers, not ours - which is why
 * a scope the game would refuse can never be dropped in and then silently vanish.</p>
 */
public class AttachmentSlot extends Slot {

    private final AttachmentContainer attachments;
    private final int index;

    public AttachmentSlot(AttachmentContainer container, int index, int x, int y) {
        super(container, index, x, y);
        this.attachments = container;
        this.index = index;
    }

    public AttachmentType type() {
        return AttachmentContainer.typeOf(index);
    }

    /** Which firearm this belongs to, 0 or 1. */
    public int gunIndex() {
        return AttachmentContainer.gunOf(index);
    }

    public ItemStack gun() {
        return attachments.gunStack(index);
    }

    /** True when there is a gun here and it has this kind of mounting point. */
    public boolean isUsable() {
        ItemStack gun = gun();
        return !gun.isEmpty() && TaczAttachments.acceptsType(gun, type());
    }

    /**
     * An empty firearm cell means these six slots are not a thing yet.
     *
     * <p>Vanilla has no notion of a slot that is present but inert, so "does not exist" is
     * expressed as refusing everything and being drawn differently. It cannot be hidden outright:
     * the slot count is baked into the menu on both sides at construction, and a menu whose slot
     * list changed when a gun moved would desync instantly.</p>
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!isUsable()) {
            return false;
        }
        AttachmentType incoming = TaczAttachments.typeOf(stack);
        return incoming == type() && TaczAttachments.accepts(gun(), stack);
    }

    @Override
    public boolean mayPickup(Player player) {
        return !getItem().isEmpty();
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    /** Hidden from vanilla's 16px render pass while the bag screen draws it - see {@link SlotVisibility}. */
    @Override
    public boolean isActive() {
        return !SlotVisibility.hidden;
    }
}
