package com.petrify.client;

import com.petrify.PetrifyMod;
import com.petrify.block.StatueBlock;
import com.petrify.util.PetrifyHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A statue is drawn as the model of the entity, but it is meant to be handled like a block. When the
 * crosshair is on the model, aim at the invisible block under it instead, so it can be mined, chiseled
 * and so on from any height (head included).
 */
@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID, value = Dist.CLIENT)
public class StatueClientEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !(mc.hitResult instanceof EntityHitResult hit)) {
            return;
        }
        if (!(hit.getEntity() instanceof LivingEntity living) || !PetrifyHelper.isPetrified(living)) {
            return;
        }
        BlockPos pos = living.blockPosition();
        if (mc.level.getBlockState(pos).getBlock() instanceof StatueBlock) {
            mc.hitResult = new BlockHitResult(hit.getLocation(), Direction.UP, pos, false);
        }
    }
}
