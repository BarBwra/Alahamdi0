package com.barbwra.mlum.loot;

import com.barbwra.mlum.MlumConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Which blocks count as something you search, asked the same way on both sides.
 *
 * <p>The server uses it to decide which right-clicks become a timed search; the client uses it to
 * decide which blocks get a marker. One rule in one place means a marker never promises a search
 * that does not happen, and a search never starts on a block that had no marker.</p>
 *
 * <p><b>Storage, not machines.</b> Anything that holds items - a vanilla container, or a modded
 * fridge or wardrobe that only exposes Forge's item handler - counts, except the vanilla blocks that
 * process items rather than keep them: furnaces, brewing stands, hoppers, dispensers and droppers,
 * jukeboxes, lecterns, bookshelves and beacons. Anything else a pack wants left alone goes in
 * {@code loot_search.ignoreBlocks}.</p>
 */
public final class LootRules {

    private LootRules() {
    }

    public static boolean isLootable(@Nullable BlockEntity be) {
        if (be == null || be.isRemoved()) {
            return false;
        }
        if (be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity
                || be instanceof HopperBlockEntity || be instanceof DispenserBlockEntity
                || be instanceof JukeboxBlockEntity || be instanceof LecternBlockEntity
                || be instanceof ChiseledBookShelfBlockEntity || be instanceof BeaconBlockEntity) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(be.getBlockState().getBlock());
        if (id != null && MlumConfig.lootIgnoredBlocks().contains(id.toString())) {
            return false;
        }
        if (be instanceof Container) {
            return true;
        }
        try {
            return be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent();
        } catch (Throwable broken) {
            // another mod's capability code is not allowed to break the scan
            return false;
        }
    }

    /**
     * The other half of a two-block container, or null.
     *
     * <p>A double chest is two chests; a tall fridge or wardrobe is usually a lower and an upper
     * block. Both halves share one marker and one search, so clicking either half searches the
     * same thing.</p>
     */
    @Nullable
    public static BlockPos partner(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            Direction toward = ChestBlock.getConnectedDirection(state);
            BlockPos other = pos.relative(toward);
            return level.getBlockState(other).is(state.getBlock()) ? other : null;
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            BlockPos other = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below();
            return level.getBlockState(other).is(state.getBlock()) ? other : null;
        }
        return null;
    }

    /** One stable key for a container and its other half, whichever half was clicked. */
    public static long key(BlockPos pos, @Nullable BlockPos partner) {
        if (partner == null) {
            return pos.asLong();
        }
        return pos.asLong() < partner.asLong() ? pos.asLong() : partner.asLong();
    }
}
