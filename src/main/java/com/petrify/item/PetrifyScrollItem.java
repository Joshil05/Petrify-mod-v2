package com.petrify.item;

import com.petrify.client.PetrifyClientHooks;
import com.petrify.util.PetrifyHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.petrify.block.StatueBlock;
import com.petrify.block.StatueBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Scrolls of Petrification and Restoration.
 *
 * tier 0 + petrification: the creative scroll. Unlimited uses, any duration (0 = until restored).
 * tier 1..MAX_TIER      : survival scrolls with a duration cap, a cooldown and limited durability.
 * restoration           : brings a statue back to life, consumed on use.
 */
public class PetrifyScrollItem extends Item {
    /** NBT key holding how long the Scroll of Petrification keeps its target stone, in seconds. */
    public static final String TAG_SECONDS = "PetrifySeconds";
    /** Longest duration the creative scroll can be set to (7 days). */
    public static final int MAX_SECONDS = 604_800;
    public static final String TAG_BLOCK_MATERIAL = "PetrifyBlockMaterial";

    /** True when the statue should be made of the block the target stands on. */
    public static boolean isBlockMaterial(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_BLOCK_MATERIAL);
    }

    // ---- Tier rules -------------------------------------------------------------------------
    /** How many tiers exist. Items petrification_scroll_t1 .. _tN are registered from this. */
    public static final int MAX_TIER = 5;
    /** Tier 1 lasts this long; every next tier adds the same amount. */
    public static final int SECONDS_PER_TIER = 20;
    /** Cooldown after a use for tier 1, in seconds. */
    public static final double BASE_COOLDOWN_SECONDS = 300.0;
    /** Every next tier has this fraction of the previous tier's cooldown (0.9 = 10% shorter). */
    public static final double COOLDOWN_FACTOR = 0.9;
    /** Durability (number of uses) of tier 1; every next tier adds the same amount. */
    public static final int USES_PER_TIER = 4;

    private static final int SHORT_COOLDOWN_TICKS = 20;

    private final boolean restore;
    private final int tier;

    public PetrifyScrollItem(Properties properties, boolean restore, int tier) {
        super(properties);
        this.restore = restore;
        this.tier = tier;
    }

    public boolean isRestore() {
        return restore;
    }

    public boolean isTiered() {
        return !restore && tier > 0;
    }

    public int getTier() {
        return tier;
    }

    public static int usesForTier(int tier) {
        return USES_PER_TIER * tier;
    }

    public static int maxSecondsForTier(int tier) {
        return SECONDS_PER_TIER * tier;
    }

    public static double cooldownSecondsForTier(int tier) {
        return BASE_COOLDOWN_SECONDS * Math.pow(COOLDOWN_FACTOR, tier - 1);
    }

    /** Longest duration this scroll may be set to. */
    public int getMaxSeconds() {
        return isTiered() ? maxSecondsForTier(tier) : MAX_SECONDS;
    }

    public int getCooldownTicks() {
        return isTiered() ? (int) Math.round(cooldownSecondsForTier(tier) * 20.0) : SHORT_COOLDOWN_TICKS;
    }

    /**
     * @return the duration stored on the scroll in seconds. For tiered scrolls this is always between
     * 1 and the tier maximum (a scroll nobody has configured uses the full tier duration).
     * For the creative scroll 0 means "until restored".
     */
    public static int getSeconds(ItemStack stack) {
        if (!(stack.getItem() instanceof PetrifyScrollItem scroll)) {
            return 0;
        }
        int max = scroll.getMaxSeconds();
        CompoundTag tag = stack.getTag();
        boolean hasValue = tag != null && tag.contains(TAG_SECONDS);
        int value = hasValue ? tag.getInt(TAG_SECONDS) : (scroll.isTiered() ? max : 0);

        if (scroll.isTiered()) {
            return Mth.clamp(value <= 0 ? max : value, 1, max);
        }
        return Mth.clamp(value, 0, max);
    }

    public static Component formatDuration(int seconds) {
        if (seconds <= 0) {
            return Component.translatable("gui.petrify.forever");
        }
        int days = seconds / 86_400;
        int hours = seconds % 86_400 / 3_600;
        int minutes = seconds % 3_600 / 60;
        int secs = seconds % 60;

        StringBuilder text = new StringBuilder();
        if (days > 0) text.append(days).append(" d ");
        if (hours > 0) text.append(hours).append(" h ");
        if (minutes > 0) text.append(minutes).append(" min ");
        if (secs > 0) text.append(secs).append(" s");
        return Component.literal(text.toString().trim());
    }

    /** The solid block of a statue gets in front of the entity, so the restoration scroll also works on the block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!restore || player == null || player.getCooldowns().isOnCooldown(this)
                || !(level.getBlockState(context.getClickedPos()).getBlock() instanceof StatueBlock)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(context.getClickedPos()) instanceof StatueBlockEntity be) {
            LivingEntity target = be.findEntity((ServerLevel) level);
            if (target != null && PetrifyHelper.isPetrified(target)) {
                PetrifyHelper.restore(target);
                consume(player, context.getItemInHand(), context.getHand());
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.PASS;
        }
        boolean petrified = PetrifyHelper.isPetrified(target);
        if (restore != petrified) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (restore) {
            PetrifyHelper.restore(target);
        } else {
            PetrifyHelper.petrify(target, getSeconds(stack), isBlockMaterial(stack));
        }
        consume(player, stack, hand);
        return InteractionResult.CONSUME;
    }

    /**
     * Petrification scroll, right click in the air: open the duration menu.
     * Sneak + right click: cast it on yourself.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (restore) {
            return InteractionResultHolder.pass(stack);
        }

        if (player.isShiftKeyDown()) {
            if (PetrifyHelper.isPetrified(player)) {
                return InteractionResultHolder.pass(stack);
            }
            if (!level.isClientSide) {
                PetrifyHelper.petrify(player, getSeconds(stack), isBlockMaterial(stack));
                consume(player, stack, hand);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        if (level.isClientSide) {
            int seconds = getSeconds(stack);
            int max = getMaxSeconds();
            boolean allowForever = !isTiered();
            boolean blockMaterial = isBlockMaterial(stack);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> PetrifyClientHooks.openScrollScreen(hand, seconds, max, allowForever, blockMaterial));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Cooldown and wear after a successful use. Creative mode players are never limited. */
    private void consume(Player player, ItemStack stack, InteractionHand hand) {
        if (player.getAbilities().instabuild) {
            return;
        }
        player.getCooldowns().addCooldown(this, getCooldownTicks());
        if (isTiered()) {
            stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
        } else if (restore) {
            stack.shrink(1);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (restore) {
            tooltip.add(Component.translatable("item.petrify.restoration_scroll.desc"));
            return;
        }

        if (isTiered()) {
            tooltip.add(Component.translatable("item.petrify.tier", tier));
            tooltip.add(Component.translatable("item.petrify.max_duration", formatDuration(getMaxSeconds())));
            tooltip.add(Component.translatable("item.petrify.cooldown",
                    formatDuration((int) Math.round(cooldownSecondsForTier(tier)))));
        } else {
            tooltip.add(Component.translatable("item.petrify.creative_scroll"));
        }
        tooltip.add(Component.translatable("item.petrify.petrification_scroll.desc"));
        tooltip.add(Component.translatable("item.petrify.petrification_scroll.desc2"));
        tooltip.add(Component.translatable("item.petrify.petrification_scroll.desc3"));
        tooltip.add(Component.translatable("item.petrify.duration", formatDuration(getSeconds(stack))));
        tooltip.add(Component.translatable("item.petrify.mode", Component.translatable(
                isBlockMaterial(stack) ? "gui.petrify.mode.block" : "gui.petrify.mode.stone")));
    }
}
