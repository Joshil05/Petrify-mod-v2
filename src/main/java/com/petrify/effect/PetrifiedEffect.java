package com.petrify.effect;

import com.petrify.util.PetrifyHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PetrifiedEffect extends MobEffect {
    private static final String TAG_NOAI = "petrify_noai";
    private static final String TAG_SILENT = "petrify_silent";

    public PetrifiedEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8A8A8A);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide) {
            return;
        }

        CompoundTag data = entity.getPersistentData();

        // Timed petrification: remove the effect once the end time has passed.
        long until = data.getLong(PetrifyHelper.TAG_UNTIL);
        if (until > 0 && entity.level().getGameTime() >= until) {
            PetrifyHelper.restore(entity);
            return;
        }

        // Freeze horizontal movement, keep gravity so a statue can still fall to the ground.
        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, Math.min(motion.y, 0.0), 0.0);

        if (entity instanceof Mob mob) {
            if (!mob.isNoAi()) {
                mob.setNoAi(true);
                data.putBoolean(TAG_NOAI, true);
            }
            // NoAI mobs still make ambient sounds in vanilla; a statue should be quiet.
            if (!mob.isSilent()) {
                mob.setSilent(true);
                data.putBoolean(TAG_SILENT, true);
            }
        }

        // Server-authoritative position lock for players (also stops hacked clients walking away).
        if (entity instanceof ServerPlayer player && data.contains(PetrifyHelper.TAG_X)) {
            double x = data.getDouble(PetrifyHelper.TAG_X);
            double z = data.getDouble(PetrifyHelper.TAG_Z);
            double dx = player.getX() - x;
            double dz = player.getZ() - z;
            if (dx * dx + dz * dz > 0.0025) {
                player.connection.teleport(x, player.getY(), z, player.getYRot(), player.getXRot());
            }
        }
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        super.removeAttributeModifiers(entity, attributes, amplifier);
        CompoundTag data = entity.getPersistentData();
        if (entity instanceof Mob mob && data.getBoolean(TAG_NOAI)) {
            mob.setNoAi(false);
            data.remove(TAG_NOAI);
        }
        if (entity instanceof Mob mob && data.getBoolean(TAG_SILENT)) {
            mob.setSilent(false);
            data.remove(TAG_SILENT);
        }
        data.remove(PetrifyHelper.TAG_UNTIL);
        PetrifyHelper.syncRemoval(entity);
    }

    /** Milk and other cures must not free a statue; only the Restoration Scroll or /unpetrify can. */
    @Override
    public List<ItemStack> getCurativeItems() {
        return new ArrayList<>();
    }
}
