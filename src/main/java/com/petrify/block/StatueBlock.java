package com.petrify.block;

import com.petrify.registry.ModItems;
import com.petrify.util.StatueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Invisible solid block placed in the cell of a statue. The statue itself is still drawn by the entity
 * renderer; the block gives it a hitbox that can be stood on, and lets it be collected like any block.
 */
public class StatueBlock extends BaseEntityBlock {
    /** Footprint and height of the statue, centered in the cell; the default is the size of a player. */
    private static VoxelShape shapeOf(BlockGetter level, BlockPos pos, double maxHeight) {
        double width = 0.6;
        double height = 1.8;
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity be) {
            width = Math.min(Math.max(be.getWidth(), 0.2F), 1.0F);
            height = Math.min(Math.max(be.getHeight(), 0.2F), 4.0F);
        }
        double half = width / 2.0;
        return Shapes.box(0.5 - half, 0.0, 0.5 - half, 0.5 + half, Math.min(height, maxHeight), 0.5 + half);
    }

    public StatueBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(1.5F, 3_600_000.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .noLootTable()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StatueBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(level, pos, 4.0); // what you aim at: the full shape of the statue
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeOf(level, pos, 1.0); // what you walk into: at most one block high, so it can be jumped onto
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.BLOCK;
    }

    /**
     * Mining speed follows the material of the statue: stone wants a pickaxe, sand a shovel, and so on.
     * The sculptor's chisel is fast on every material.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        BlockState material = Blocks.STONE.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof StatueBlockEntity be && be.getStateId() >= 0) {
            material = Block.stateById(be.getStateId());
        }
        float hardness = material.getDestroySpeed(level, pos);
        if (hardness < 0.0F) {
            hardness = 50.0F; // statues of unbreakable blocks are just very hard
        }
        if (player.getMainHandItem().is(ModItems.CHISEL.get())) {
            return 12.0F / hardness / 30.0F;
        }
        int divisor = player.hasCorrectToolForDrops(material) ? 30 : 100;
        return player.getDestroySpeed(material) / hardness / divisor;
    }

    /** Breaking the block collects the statue into the inventory of the player. */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer collector
                && level.getBlockEntity(pos) instanceof StatueBlockEntity be) {
            StatueManager.collect((ServerLevel) level, be, collector);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
