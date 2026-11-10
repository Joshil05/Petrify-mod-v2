package com.petrify.registry;

import com.petrify.PetrifyMod;
import com.petrify.effect.PetrifiedEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, PetrifyMod.MOD_ID);

    public static final RegistryObject<MobEffect> PETRIFIED =
            EFFECTS.register("petrified", PetrifiedEffect::new);
}
