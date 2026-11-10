package com.petrify.block;

import com.petrify.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Data of the solid block under a statue: which entity it belongs to and what the statue is made of
 * (block state id, -1 = stone). The material is synced to clients so mining speed matches on both sides.
 */
public class StatueBlockEntity extends BlockEntity {
    @Nullable
    private UUID entity;
    private int stateId = -1;
    private float width = 0.6F;
    private float height = 1.8F;

    public StatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATUE.get(), pos, state);
    }

    public void setup(UUID entity, int stateId, float width, float height) {
        this.entity = entity;
        this.stateId = stateId;
        this.width = width;
        this.height = height;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /** Size of the statue, so the block has the shape of the entity. */
    public float getWidth() {
        return this.width;
    }

    public float getHeight() {
        return this.height;
    }

    public int getStateId() {
        return this.stateId;
    }

    /** The petrified entity this block belongs to, if it is currently loaded. */
    @Nullable
    public LivingEntity findEntity(ServerLevel level) {
        if (this.entity != null && level.getEntity(this.entity) instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.entity != null) {
            tag.putUUID("Entity", this.entity);
        }
        tag.putInt("State", this.stateId);
        tag.putFloat("Width", this.width);
        tag.putFloat("Height", this.height);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.entity = tag.hasUUID("Entity") ? tag.getUUID("Entity") : null;
        this.stateId = tag.contains("State") ? tag.getInt("State") : -1;
        this.width = tag.contains("Width") ? tag.getFloat("Width") : 0.6F;
        this.height = tag.contains("Height") ? tag.getFloat("Height") : 1.8F;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
