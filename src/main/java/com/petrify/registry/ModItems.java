package com.petrify.registry;

import com.petrify.PetrifyMod;
import com.petrify.item.ChiselItem;
import com.petrify.item.PetrifyScrollItem;
import com.petrify.item.StatueItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, PetrifyMod.MOD_ID);

    /** Creative scroll: unlimited uses, any duration. Not craftable. */
    public static final RegistryObject<Item> PETRIFICATION_SCROLL = ITEMS.register("petrification_scroll",
            () -> new PetrifyScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), false, 0));

    /** Survival scrolls petrification_scroll_t1 .. t<MAX_TIER>, in tier order. */
    public static final List<RegistryObject<Item>> TIER_SCROLLS = new ArrayList<>();

    static {
        for (int tier = 1; tier <= PetrifyScrollItem.MAX_TIER; tier++) {
            final int t = tier;
            TIER_SCROLLS.add(ITEMS.register("petrification_scroll_t" + tier,
                    () -> new PetrifyScrollItem(
                            new Item.Properties()
                                    .durability(PetrifyScrollItem.usesForTier(t))
                                    .rarity(t < 3 ? Rarity.UNCOMMON : Rarity.RARE),
                            false, t)));
        }
    }

    public static final RegistryObject<Item> RESTORATION_SCROLL = ITEMS.register("restoration_scroll",
            () -> new PetrifyScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), true, 0));

    /** Holds a collected petrified mob or player. Not craftable: it only comes from breaking a statue. */
    public static final RegistryObject<Item> STATUE = ITEMS.register("statue",
            () -> new StatueItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** Sculptor's chisel: collects statues faster than any other tool. */
    public static final RegistryObject<Item> CHISEL = ITEMS.register("chisel",
            () -> new ChiselItem(new Item.Properties().stacksTo(1).durability(250)));
}
