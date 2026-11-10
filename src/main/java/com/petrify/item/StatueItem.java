package com.petrify.item;

import com.petrify.util.StatueManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** A collected statue: holds a petrified mob or player until it is placed somewhere. */
public class StatueItem extends Item {
    public StatueItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace() || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (StatueManager.place((ServerLevel) level, player, stack, place.getClickedPos())) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    /** A statue that holds a player must never get lost, so its item entity is indestructible. */
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        entity.setUnlimitedLifetime();
        entity.setInvulnerable(true);
        return false;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(StatueManager.TAG_NAME)) {
            return Component.translatable("item.petrify.statue.named", tag.getString(StatueManager.TAG_NAME));
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            int remaining = tag.getInt(StatueManager.TAG_REMAINING);
            tooltip.add(remaining > 0
                    ? Component.translatable("item.petrify.statue.time", PetrifyScrollItem.formatDuration(remaining))
                    : Component.translatable("item.petrify.statue.forever"));
            tooltip.add(Component.translatable("item.petrify.mode", Component.translatable(
                    tag.getBoolean(StatueManager.TAG_MATERIAL) ? "gui.petrify.mode.block" : "gui.petrify.mode.stone")));
        }
        tooltip.add(Component.translatable("item.petrify.statue.hint"));
    }
}
