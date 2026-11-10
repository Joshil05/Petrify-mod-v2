package com.petrify.client;

import com.petrify.PetrifyMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        // Players: "default" (Steve-style arms) and "slim" (Alex-style arms) renderers.
        for (String skin : event.getSkins()) {
            EntityRenderer<?> renderer = event.getSkin(skin);
            if (renderer instanceof LivingEntityRenderer living) {
                addLayer(living);
            }
        }
        // Every other living entity type that has a renderer (mobs, villagers, armor stands...).
        for (EntityType<?> type : ForgeRegistries.ENTITY_TYPES.getValues()) {
            EntityRenderer<?> renderer = event.getRenderer((EntityType) type);
            if (renderer instanceof LivingEntityRenderer living) {
                addLayer(living);
            }
        }
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> void addLayer(LivingEntityRenderer<T, M> renderer) {
        renderer.addLayer(new StoneLayer<>(renderer));
    }
}
