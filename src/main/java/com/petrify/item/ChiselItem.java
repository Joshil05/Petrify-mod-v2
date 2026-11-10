package com.petrify.item;

import com.petrify.block.StatueBlock;
import com.petrify.block.StatueBlockEntity;
import com.petrify.util.PetrifyHelper;
import com.petrify.util.StatueManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Sculptor's chisel: breaks statues fast, and right click collects a statue instantly. */
public class ChiselItem extends Item {
    public ChiselItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof StatueBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (context.getPlayer() instanceof ServerPlayer player
                && level.getBlockEntity(context.getClickedPos()) instanceof StatueBlockEntity be) {
            StatueManager.collect((ServerLevel) level, be, player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!PetrifyHelper.isPetrified(target)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer collector) {
            StatueManager.collect((ServerLevel) player.level(), target, collector);
        }
        return InteractionResult.CONSUME;
    }
}
