package com.petrify.client;

import com.petrify.util.PetrifyHelper;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** What each petrified entity that this client can see is made of (entity id to block). Missing = plain stone. */
public final class ClientMaterials {
    public record Entry(BlockState state, BlockPos pos) {}

    private static final Map<Integer, Entry> MATERIALS = new HashMap<>();

    private ClientMaterials() {}

    public static void set(int entityId, int stateId, BlockPos pos) {
        if (stateId < 0) {
            MATERIALS.remove(entityId);
            return;
        }
        MATERIALS.put(entityId, new Entry(Block.stateById(stateId), pos));
    }

    @Nullable
    public static Entry get(int entityId) {
        return MATERIALS.get(entityId);
    }

    /** Forgets entries of entities that are gone or are no longer stone. */
    public static void prune(ClientLevel level) {
        MATERIALS.keySet().removeIf(id -> {
            Entity entity = level.getEntity(id);
            return !(entity instanceof LivingEntity living) || !PetrifyHelper.isPetrified(living);
        });
    }
}
