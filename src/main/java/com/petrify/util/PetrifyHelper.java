package com.petrify.util;

import com.petrify.PetrifyMod;
import com.petrify.block.StatueBlock;
import com.petrify.block.StatueBlockEntity;
import com.petrify.registry.ModBlocks;
import com.petrify.net.MaterialPacket;
import com.petrify.net.PetrifyNetwork;
import com.petrify.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public final class PetrifyHelper {
    public static final String TAG_X = "petrify_x";
    public static final String TAG_Z = "petrify_z";
    public static final String TAG_UNTIL = "petrify_until";
    /** Block state id of the material of a "block underfoot" statue (absent for plain stone). */
    public static final String TAG_STATE = "petrify_state";
    public static final String TAG_POS = "petrify_pos";
    /** Position of the invisible solid block under the statue. */
    public static final String TAG_BLOCK = "petrify_block";

    private PetrifyHelper() {}

    public static boolean isPetrified(LivingEntity entity) {
        return entity.hasEffect(ModEffects.PETRIFIED.get());
    }

    /** Plain stone statue. @param seconds how long it lasts; zero or less means until restored. */
    public static void petrify(LivingEntity entity, int seconds) {
        petrify(entity, seconds, false);
    }

    /**
     * @param seconds       how long the statue lasts; zero or less means until restored.
     * @param blockMaterial true = the statue is made of the block it stands on instead of stone.
     */
    public static void petrify(LivingEntity entity, int seconds, boolean blockMaterial) {
        if (entity.level().isClientSide) {
            return;
        }
        restore(entity, false);

        CompoundTag data = entity.getPersistentData();
        data.putDouble(TAG_X, entity.getX());
        data.putDouble(TAG_Z, entity.getZ());
        data.putLong(TAG_UNTIL, seconds > 0 ? entity.level().getGameTime() + seconds * 20L : 0L);

        entity.addEffect(new MobEffectInstance(ModEffects.PETRIFIED.get(), PetrifyMod.BASE_DURATION, 0, false, false, true));
        syncApplied(entity);
        int stateId = applyMaterial(entity, blockMaterial);
        placeStatueBlock(entity, stateId);
        entity.setDeltaMovement(0.0, 0.0, 0.0);
        entity.hurtMarked = true;
        effects(entity, true);
    }

    /** Removes the petrification without sound and particles (used when a statue is collected). */
    public static void release(LivingEntity entity) {
        restore(entity, false);
    }

    /** Places the solid block that lets the statue be stood on and collected. Skipped if the cell is taken. */
    private static void placeStatueBlock(LivingEntity entity, int stateId) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = entity.blockPosition();
        BlockState current = level.getBlockState(pos);
        if (!(current.isAir() || (current.canBeReplaced() && current.getFluidState().isEmpty()))) {
            return;
        }
        level.setBlock(pos, ModBlocks.STATUE.get().defaultBlockState(), 3);
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity be) {
            be.setup(entity.getUUID(), stateId, entity.getBbWidth(), entity.getBbHeight());
        }
        entity.getPersistentData().putLong(TAG_BLOCK, pos.asLong());
    }

    public static void restore(LivingEntity entity) {
        restore(entity, true);
    }

    private static void restore(LivingEntity entity, boolean withEffects) {
        if (entity.level().isClientSide || !isPetrified(entity)) {
            return;
        }
        entity.removeEffect(ModEffects.PETRIFIED.get());
        if (withEffects) {
            effects(entity, false);
        }
    }

    /** Works out and announces what the statue is made of. Always sent, so a plain-stone cast clears an old material. */
    private static int applyMaterial(LivingEntity entity, boolean blockMaterial) {
        CompoundTag data = entity.getPersistentData();
        data.remove(TAG_STATE);
        data.remove(TAG_POS);

        int stateId = -1;
        BlockPos pos = BlockPos.ZERO;
        if (blockMaterial) {
            pos = entity.getOnPos();
            BlockState state = PetrifyMaterials.resolve(entity.level(), pos);
            if (state != null && state.getBlock() != Blocks.AIR) {
                stateId = Block.getId(state);
                data.putInt(TAG_STATE, stateId);
                data.putLong(TAG_POS, pos.asLong());
            }
        }
        PetrifyNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new MaterialPacket(entity.getId(), stateId, pos));
        return stateId;
    }

    /**
     * Vanilla only tells the affected player about a new effect, other players learn about it only
     * when the entity comes into their view. So we send the effect to everyone who is watching.
     */
    private static void syncApplied(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        MobEffectInstance effect = entity.getEffect(ModEffects.PETRIFIED.get());
        if (effect != null) {
            level.getChunkSource().broadcast(entity, new ClientboundUpdateMobEffectPacket(entity.getId(), effect));
        }
    }

    /** Called when the effect is removed for any reason, so everyone watching sees the statue come back to life. */
    public static void syncRemoval(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        data.remove(TAG_STATE);
        data.remove(TAG_POS);
        if (data.contains(TAG_BLOCK)) {
            BlockPos blockPos = BlockPos.of(data.getLong(TAG_BLOCK));
            data.remove(TAG_BLOCK);
            if (level.getBlockState(blockPos).getBlock() instanceof StatueBlock) {
                level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        level.getChunkSource().broadcast(entity,
                new ClientboundRemoveMobEffectPacket(entity.getId(), ModEffects.PETRIFIED.get()));
    }

    private static void effects(LivingEntity entity, boolean petrifying) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        level.playSound(null, entity.blockPosition(),
                petrifying ? SoundEvents.GRINDSTONE_USE : SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, petrifying ? 0.6F : 1.2F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(),
                petrifying ? 40 : 25, entity.getBbWidth() / 2.0, entity.getBbHeight() / 2.0, entity.getBbWidth() / 2.0, 0.05);
    }
}
