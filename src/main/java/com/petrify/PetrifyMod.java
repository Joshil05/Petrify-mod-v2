package com.petrify;

import com.petrify.net.PetrifyNetwork;
import com.petrify.registry.ModBlockEntities;
import com.petrify.registry.ModBlocks;
import com.petrify.registry.ModCreativeTabs;
import com.petrify.registry.ModEffects;
import com.petrify.registry.ModItems;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PetrifyMod.MOD_ID)
public class PetrifyMod {
    public static final String MOD_ID = "petrify";

    /** Effect duration used for "permanent" petrification. Client animation reads elapsed = BASE_DURATION - remaining. */
    public static final int BASE_DURATION = 1_000_000_000;

    /** Length of the feet-to-head stone animation, in ticks. */
    public static final int ANIMATION_TICKS = 60;

    public PetrifyMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEffects.EFFECTS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.TYPES.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        PetrifyNetwork.register();
    }
}
