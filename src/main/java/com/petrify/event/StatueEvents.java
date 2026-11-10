package com.petrify.event;

import com.petrify.PetrifyMod;
import com.petrify.util.StatueManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps players who were collected into a statue item spectating whoever carries it. */
@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID)
public class StatueEvents {
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            StatueManager.tick(player);
        }
    }
}
