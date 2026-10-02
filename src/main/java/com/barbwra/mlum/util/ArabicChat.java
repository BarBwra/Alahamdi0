package com.barbwra.mlum.util;

import net.minecraft.network.chat.Component;

/**
 * Builds Arabic chat messages that come out the right way round.
 *
 * <p><b>Chat is not the GUI, and the difference has bitten this project three times.</b> Anything
 * drawn with {@code Font.drawString(String, ...)} gets no bidi pass, so the caller has to shape
 * <i>and</i> reorder it - that is what {@link ArabicText#autoDisplay} does, and it is why the mod's
 * lang files are stored pre-baked in visual order. Chat and tooltips go the other way: they render
 * a {@code Component}, and that path runs {@code Language.getVisualOrder}, which reorders the line
 * itself. Handing it visual-order text reverses it a second time.</p>
 *
 * <p>So a message sent as {@code Component.translatable("some.baked.key")} arrives backwards, which
 * is exactly what happened to the vehicle "lost" notice. The fix is to shape only and let Minecraft
 * do the reordering.</p>
 *
 * <p>Use {@link #of} for every player-facing chat message containing Arabic. Raw logical Arabic
 * would order correctly but render unjoined, and pre-baked text orders backwards - shaping without
 * reordering is the only combination that is right on this path.</p>
 */
public final class ArabicChat {

    private ArabicChat() {
    }

    /**
     * A chat component from logical Arabic: letters joined, order left to Minecraft.
     *
     * <p>Non-Arabic text and colour codes pass through untouched.</p>
     */
    public static Component of(String logical) {
        if (logical == null || logical.isEmpty()) {
            return Component.empty();
        }
        return Component.literal(ArabicText.isLogical(logical) ? ArabicText.shape(logical) : logical);
    }

    /**
     * Un-bakes a lang value that was stored in visual order, then re-shapes it for chat.
     *
     * <p>The lang files hold presentation forms already reversed for {@code drawString}. Reversing
     * them back yields shaped logical order, which is precisely what the Component path wants - so
     * one call to {@link ArabicText#toVisual} undoes the bake, because the reordering is its own
     * inverse for a single run of right-to-left text.</p>
     */
    public static Component fromBaked(String baked) {
        if (baked == null || baked.isEmpty()) {
            return Component.empty();
        }
        return Component.literal(ArabicText.toVisual(baked));
    }
}
