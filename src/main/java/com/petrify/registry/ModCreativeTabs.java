package com.petrify.registry;

import com.petrify.PetrifyMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, PetrifyMod.MOD_ID);

    public static final RegistryObject<CreativeModeTab> PETRIFY_TAB = TABS.register("petrify",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.petrify"))
                    .icon(() -> new ItemStack(ModItems.PETRIFICATION_SCROLL.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.PETRIFICATION_SCROLL.get());
                        for (RegistryObject<Item> scroll : ModItems.TIER_SCROLLS) {
                            output.accept(scroll.get());
                        }
                        output.accept(ModItems.RESTORATION_SCROLL.get());
                        output.accept(ModItems.CHISEL.get());
                    })
                    .build());
}
