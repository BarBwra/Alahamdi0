package com.barbwra.mlum.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One quest line, exactly as the {@code /PlayerQuest} command delivers it.
 *
 * <p>{@code title} and {@code description} are stored in <b>logical</b> Arabic order, unshaped.
 * Shaping happens at draw time so wrapping can run on logical text - see
 * {@link com.barbwra.mlum.util.ArabicText}.</p>
 */
public record QuestEntry(int line, QuestType type, String title, int progress, int max,
                         String description, List<ItemStack> rewards) {

    public static final int MAX_TITLE = 128;
    public static final int MAX_DESC = 1024;
    public static final int MAX_REWARDS = 12;

    public QuestEntry {
        title = clamp(title, MAX_TITLE);
        description = clamp(description, MAX_DESC);
        max = Math.max(0, max);
        progress = Math.max(0, progress);
        rewards = rewards == null ? List.of()
                : List.copyOf(rewards.size() > MAX_REWARDS ? rewards.subList(0, MAX_REWARDS) : rewards);
    }

    private static String clamp(String s, int limit) {
        if (s == null) {
            return "";
        }
        return s.length() > limit ? s.substring(0, limit) : s;
    }

    /** A quest is complete when it reaches its target. There is no separate flag to drift. */
    public boolean isComplete() {
        return max > 0 && progress >= max;
    }

    public float fraction() {
        return max <= 0 ? 0.0F : Math.min(1.0F, (float) progress / max);
    }

    /* ------------------------------------------------------------------- codec */

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(line);
        buf.writeByte(type.ordinal());
        buf.writeUtf(title, MAX_TITLE);
        buf.writeVarInt(progress);
        buf.writeVarInt(max);
        buf.writeUtf(description, MAX_DESC);
        buf.writeByte(rewards.size());
        for (ItemStack stack : rewards) {
            buf.writeItem(stack);
        }
    }

    public static QuestEntry read(FriendlyByteBuf buf) {
        int line = buf.readVarInt();
        QuestType type = QuestType.values()[Math.floorMod(buf.readByte(), QuestType.values().length)];
        String title = buf.readUtf(MAX_TITLE);
        int progress = buf.readVarInt();
        int max = buf.readVarInt();
        String description = buf.readUtf(MAX_DESC);
        int count = Math.min(buf.readByte(), MAX_REWARDS);
        List<ItemStack> rewards = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            rewards.add(buf.readItem());
        }
        return new QuestEntry(line, type, title, progress, max, description, rewards);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Line", line);
        tag.putString("Type", type.id());
        tag.putString("Title", title);
        tag.putInt("Progress", progress);
        tag.putInt("Max", max);
        tag.putString("Desc", description);
        ListTag list = new ListTag();
        for (ItemStack stack : rewards) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put("Rewards", list);
        return tag;
    }

    public static QuestEntry load(CompoundTag tag) {
        ListTag list = tag.getList("Rewards", Tag.TAG_COMPOUND);
        List<ItemStack> rewards = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = ItemStack.of(list.getCompound(i));
            if (!stack.isEmpty()) {
                rewards.add(stack);
            }
        }
        return new QuestEntry(
                tag.getInt("Line"),
                QuestType.byId(tag.getString("Type")),
                tag.getString("Title"),
                tag.getInt("Progress"),
                tag.getInt("Max"),
                tag.getString("Desc"),
                rewards);
    }
}
