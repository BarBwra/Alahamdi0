package com.barbwra.mlum.downed;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * A downed body to practise on: {@code /mlum downed dummy} drops one in front of you.
 *
 * <p>It behaves like a downed player in every way the system cares about - it bleeds out on the
 * same clock, can be looted through the same screen and revived through the same prompt, with the
 * same items - so the whole flow can be tried alone. It carries a handful of ordinary items so the
 * loot screen has something in it. Revived, it gets up and is gone; bled out, it just is gone.</p>
 */
public class DownedDummy extends Mob {

    private static final EntityDataAccessor<Integer> REMAINING =
            SynchedEntityData.defineId(DownedDummy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TOTAL =
            SynchedEntityData.defineId(DownedDummy.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> REVIVE =
            SynchedEntityData.defineId(DownedDummy.class, EntityDataSerializers.FLOAT);

    /** The same 42 places a player's loot view has: 36 inventory, 4 armour, offhand, backpack. */
    final NonNullList<ItemStack> loot = NonNullList.withSize(42, ItemStack.EMPTY);

    public DownedDummy(EntityType<? extends DownedDummy> type, Level level) {
        super(type, level);
        setNoAi(true);
        setPersistenceRequired();
        setPose(Pose.SLEEPING);
    }

    public static AttributeSupplier.Builder attributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(REMAINING, 3600);
        this.entityData.define(TOTAL, 3600);
        this.entityData.define(REVIVE, 0.0F);
    }

    /** Fresh from the command: a full timer and something worth looting. */
    public void prime() {
        int total = MlumConfig.downedSeconds() * 20;
        this.entityData.set(TOTAL, total);
        this.entityData.set(REMAINING, total);
        loot.set(0, new ItemStack(Items.BREAD, 6));
        loot.set(1, new ItemStack(Items.COOKED_BEEF, 3));
        loot.set(2, new ItemStack(Items.ARROW, 24));
        loot.set(9, new ItemStack(Items.IRON_INGOT, 5));
        loot.set(27, new ItemStack(Items.IRON_SWORD));
        loot.set(38, new ItemStack(Items.LEATHER_CHESTPLATE));
    }

    public int remaining() {
        return this.entityData.get(REMAINING);
    }

    public int total() {
        return Math.max(1, this.entityData.get(TOTAL));
    }

    public float reviveProgress() {
        return this.entityData.get(REVIVE);
    }

    void setReviveProgress(float progress) {
        this.entityData.set(REVIVE, progress);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            return;
        }
        int left = remaining() - 1;
        this.entityData.set(REMAINING, left);
        if (left <= 0) {
            vanish();
        }
    }

    /** Revived or bled out: a puff, and gone. */
    void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3D, getZ(), 12, 0.4D, 0.2D, 0.4D, 0.02D);
        }
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // only /kill removes it; nothing in the world should finish the practice body by accident
        return source.is(DamageTypes.GENERIC_KILL) && super.hurt(source, amount);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.8F, 0.5F);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ContainerHelper.saveAllItems(tag, loot);
        tag.putInt("MlumRemaining", remaining());
        tag.putInt("MlumTotal", total());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ContainerHelper.loadAllItems(tag, loot);
        if (tag.contains("MlumTotal")) {
            this.entityData.set(TOTAL, tag.getInt("MlumTotal"));
            this.entityData.set(REMAINING, tag.getInt("MlumRemaining"));
        }
    }
}
