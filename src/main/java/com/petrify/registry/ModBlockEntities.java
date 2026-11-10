package com.petrify.registry;

import com.petrify.PetrifyMod;
import com.petrify.block.StatueBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, PetrifyMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<StatueBlockEntity>> STATUE = TYPES.register("statue",
            () -> BlockEntityType.Builder.of(StatueBlockEntity::new, ModBlocks.STATUE.get()).build(null));
}
