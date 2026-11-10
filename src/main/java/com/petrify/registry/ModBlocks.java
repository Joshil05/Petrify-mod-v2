package com.petrify.registry;

import com.petrify.PetrifyMod;
import com.petrify.block.StatueBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, PetrifyMod.MOD_ID);

    public static final RegistryObject<Block> STATUE = BLOCKS.register("statue", StatueBlock::new);
}
