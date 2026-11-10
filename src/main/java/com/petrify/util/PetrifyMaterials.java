package com.petrify.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Decides what a statue is made of in "block underfoot" mode: the block it stands on, or the
 * "hardened" version of it (sand becomes sandstone, clay becomes terracotta...).
 * Edit {@link #hardenedBlock(Block)} to change or add pairs.
 */
public final class PetrifyMaterials {
    private PetrifyMaterials() {}

    /** @return the material for a statue standing on {@code pos}, or null for plain stone. */
    @Nullable
    public static BlockState resolve(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return null;
        }

        Block hardened = hardenedBlock(state.getBlock());
        if (hardened != null) {
            return hardened.defaultBlockState();
        }
        // Only full, opaque blocks make a good statue material (not carpets, glass, plants...).
        return state.isSolidRender(level, pos) ? state : null;
    }

    /** @return the hardened version of a block, or null if the block is used as it is. */
    @Nullable
    private static Block hardenedBlock(Block block) {
        if (block == Blocks.SAND) return Blocks.SANDSTONE;
        if (block == Blocks.RED_SAND) return Blocks.RED_SANDSTONE;
        if (block == Blocks.CLAY) return Blocks.TERRACOTTA;
        if (block == Blocks.SNOW_BLOCK) return Blocks.PACKED_ICE;

        if (block == Blocks.DIRT || block == Blocks.GRASS_BLOCK || block == Blocks.COARSE_DIRT
                || block == Blocks.PODZOL || block == Blocks.MYCELIUM || block == Blocks.ROOTED_DIRT
                || block == Blocks.DIRT_PATH || block == Blocks.FARMLAND || block == Blocks.MUD) {
            return Blocks.PACKED_MUD;
        }

        // Concrete powder of every color turns into the concrete of the same color.
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id != null && id.getPath().endsWith("_concrete_powder")) {
            Block concrete = ForgeRegistries.BLOCKS.getValue(
                    new ResourceLocation(id.getNamespace(), id.getPath().replace("_powder", "")));
            if (concrete != null && concrete != Blocks.AIR) {
                return concrete;
            }
        }
        return null;
    }
}
