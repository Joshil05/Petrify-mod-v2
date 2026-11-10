package com.petrify.util;

import com.petrify.block.StatueBlockEntity;
import com.petrify.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Collecting petrified mobs and players into statue items, and placing them again.
 * A collected player keeps spectating whoever carries the item until the statue is placed.
 */
public final class StatueManager {
    public static final String TAG_ENTITY = "PetrifyEntity";
    public static final String TAG_PLAYER = "PetrifyPlayer";
    public static final String TAG_ID = "PetrifyStatueId";
    public static final String TAG_NAME = "PetrifyName";
    public static final String TAG_REMAINING = "PetrifyRemaining";
    public static final String TAG_MATERIAL = "PetrifyBlockMaterial";

    /** Stored on a collected player: id of the statue item they are inside. */
    private static final String P_ITEM = "petrify_item";
    private static final String P_GAMEMODE = "petrify_gamemode";
    private static final String P_X = "petrify_last_x";
    private static final String P_Y = "petrify_last_y";
    private static final String P_Z = "petrify_last_z";

    private static final Map<UUID, ItemEntity> DROPPED = new HashMap<>();

    private StatueManager() {}

    public static boolean isCollected(ServerPlayer player) {
        return player.getPersistentData().hasUUID(P_ITEM);
    }

    /** Called when the statue block is broken: turns the statue into an item for the collector. */
    public static void collect(ServerLevel level, StatueBlockEntity be, ServerPlayer collector) {
        collect(level, be.findEntity(level), collector);
    }

    /** Same, for a statue that is targeted directly (also works for statues that have no block under them). */
    public static void collect(ServerLevel level, LivingEntity target, ServerPlayer collector) {
        if (target == null || target == collector || !PetrifyHelper.isPetrified(target)) {
            return;
        }

        CompoundTag data = target.getPersistentData();
        long until = data.getLong(PetrifyHelper.TAG_UNTIL);
        int remaining = until > 0 ? (int) Math.max(1L, (until - level.getGameTime() + 19L) / 20L) : 0;
        boolean material = data.contains(PetrifyHelper.TAG_STATE);

        ItemStack stack = new ItemStack(ModItems.STATUE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString(TAG_NAME, target.getDisplayName().getString());
        tag.putInt(TAG_REMAINING, remaining); // the timer is paused while the statue is an item
        tag.putBoolean(TAG_MATERIAL, material);

        if (target instanceof ServerPlayer victim) {
            PetrifyHelper.release(victim);
            UUID id = UUID.randomUUID();
            tag.putUUID(TAG_PLAYER, victim.getUUID());
            tag.putUUID(TAG_ID, id);

            CompoundTag victimData = victim.getPersistentData();
            victimData.putUUID(P_ITEM, id);
            victimData.putInt(P_GAMEMODE, victim.gameMode.getGameModeForPlayer().getId());
            rememberPosition(victimData, collector);
            victim.setGameMode(GameType.SPECTATOR);
            victim.setCamera(collector);
        } else {
            if (!target.save(new CompoundTag())) {
                return; // cannot be stored (for example a passenger): leave it as it is
            }
            PetrifyHelper.release(target);
            CompoundTag entityTag = new CompoundTag();
            target.save(entityTag);
            entityTag.remove("UUID");
            tag.put(TAG_ENTITY, entityTag);
            target.discard();
        }

        ItemStack held = collector.getMainHandItem();
        if (held.is(ModItems.CHISEL.get())) {
            held.hurtAndBreak(1, collector, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        if (!collector.getInventory().add(stack)) {
            collector.drop(stack, false);
        }
    }

    /** Puts the statue from the item at the given block position. @return true if it was placed. */
    public static boolean place(ServerLevel level, ServerPlayer placer, ItemStack stack, BlockPos pos) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return false;
        }
        int remaining = tag.getInt(TAG_REMAINING);
        boolean material = tag.getBoolean(TAG_MATERIAL);
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        if (tag.hasUUID(TAG_PLAYER)) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(tag.getUUID(TAG_PLAYER));
            if (target == null) {
                placer.displayClientMessage(Component.translatable("message.petrify.statue.offline"), true);
                return false;
            }
            CompoundTag data = target.getPersistentData();
            if (!data.hasUUID(P_ITEM) || !data.getUUID(P_ITEM).equals(tag.getUUID(TAG_ID))) {
                placer.displayClientMessage(Component.translatable("message.petrify.statue.invalid"), true);
                return false;
            }
            free(target);
            target.teleportTo(level, x, y, z, placer.getYRot() + 180.0F, 0.0F);
            PetrifyHelper.petrify(target, remaining, material);
            return true;
        }

        if (tag.contains(TAG_ENTITY)) {
            Entity entity = EntityType.loadEntityRecursive(tag.getCompound(TAG_ENTITY), level, e -> e);
            if (entity == null) {
                return false;
            }
            entity.setUUID(UUID.randomUUID());
            entity.moveTo(x, y, z, entity.getYRot(), entity.getXRot());
            if (entity instanceof Mob mob) {
                mob.setPersistenceRequired();
            }
            if (!level.addFreshEntity(entity)) {
                return false;
            }
            if (entity instanceof LivingEntity living) {
                PetrifyHelper.petrify(living, remaining, material);
            }
            return true;
        }
        return false;
    }

    /** Lets a collected player out: normal game mode and their own camera again. */
    public static void free(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(P_ITEM)) {
            return;
        }
        DROPPED.remove(data.getUUID(P_ITEM));
        player.setCamera(player);
        player.setGameMode(GameType.byId(data.getInt(P_GAMEMODE)));
        data.remove(P_ITEM);
        data.remove(P_GAMEMODE);
        data.remove(P_X);
        data.remove(P_Y);
        data.remove(P_Z);
    }

    /** Every tick for a collected player: follow the carrier of the statue item. */
    public static void tick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(P_ITEM)) {
            return;
        }
        Entity carrier = findCarrier(player.server, data.getUUID(P_ITEM), player.tickCount);
        if (carrier != null) {
            if (player.getCamera() != carrier) {
                player.setCamera(carrier);
            }
            if (player.tickCount % 20 == 0) {
                rememberPosition(data, carrier);
            }
            return;
        }
        // The item is somewhere we cannot see (a chest, for example): stay where the item was last seen.
        if (player.getCamera() != player) {
            player.setCamera(player);
        }
        double x = data.getDouble(P_X);
        double y = data.getDouble(P_Y);
        double z = data.getDouble(P_Z);
        if (player.distanceToSqr(x, y, z) > 0.25D) {
            player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        }
    }

    private static void rememberPosition(CompoundTag data, Entity entity) {
        data.putDouble(P_X, entity.getX());
        data.putDouble(P_Y, entity.getY());
        data.putDouble(P_Z, entity.getZ());
    }

    private static Entity findCarrier(MinecraftServer server, UUID id, int tick) {
        for (ServerPlayer holder : server.getPlayerList().getPlayers()) {
            Inventory inventory = holder.getInventory();
            if (contains(inventory.items, id) || contains(inventory.offhand, id) || contains(inventory.armor, id)) {
                return holder;
            }
        }
        ItemEntity cached = DROPPED.get(id);
        if (cached != null && !cached.isRemoved() && matches(cached.getItem(), id)) {
            return cached;
        }
        DROPPED.remove(id);
        if (tick % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof ItemEntity item && matches(item.getItem(), id)) {
                        DROPPED.put(id, item);
                        return item;
                    }
                }
            }
        }
        return null;
    }

    private static boolean contains(NonNullList<ItemStack> stacks, UUID id) {
        for (ItemStack stack : stacks) {
            if (matches(stack, id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack stack, UUID id) {
        CompoundTag tag = stack.getTag();
        return stack.is(ModItems.STATUE.get()) && tag != null && tag.hasUUID(TAG_ID) && tag.getUUID(TAG_ID).equals(id);
    }
}
