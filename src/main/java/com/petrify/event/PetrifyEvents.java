package com.petrify.event;

import com.petrify.PetrifyMod;
import com.petrify.registry.ModEffects;
import com.petrify.util.PetrifyHelper;
import com.petrify.net.MaterialPacket;
import com.petrify.net.PetrifyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Everything a statue must not be able to do while petrified, and how the world treats it. */
@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID)
public class PetrifyEvents {

    /** Hotbar slot the player had selected when they turned to stone. */
    private static final String TAG_SLOT = "petrify_slot";

    private static void cancelIfPetrified(LivingEntity entity, Event event) {
        if (entity != null && PetrifyHelper.isPetrified(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        cancelIfPetrified(event.getPlayer(), event);
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        cancelIfPetrified(event.getEntity(), event);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        cancelIfPetrified(event.getPlayer(), event);
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        cancelIfPetrified(player, event);
    }

    /** Statues are immune to damage, except things that bypass invulnerability (/kill, the void). */
    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (PetrifyHelper.isPetrified(entity) && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Mobs do not pick a statue as their target: it is just a piece of stone to them. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity target = event.getNewTarget();
        if (target != null && PetrifyHelper.isPetrified(target)) {
            event.setCanceled(true);
        }
    }

    /** A mob that was already hunting someone when they turned to stone loses interest. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && PetrifyHelper.isPetrified(target)) {
            mob.setTarget(null);
            if (mob instanceof NeutralMob neutral) {
                neutral.stopBeingAngry();
            }
        }
    }

    /** A player who starts watching a statue (walks into range) must see it as stone right away. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof LivingEntity target) {
            MobEffectInstance effect = target.getEffect(ModEffects.PETRIFIED.get());
            if (effect != null) {
                viewer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect));
            }
            CompoundTag data = target.getPersistentData();
            if (data.contains(PetrifyHelper.TAG_STATE)) {
                PetrifyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer),
                        new MaterialPacket(target.getId(), data.getInt(PetrifyHelper.TAG_STATE),
                                BlockPos.of(data.getLong(PetrifyHelper.TAG_POS))));
            }
        }
    }

    /**
     * Server-side lock of the selected hotbar slot, so a petrified player keeps holding the same item
     * even if their client tries to change slots (the client also blocks the keys and scrolling).
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        CompoundTag data = player.getPersistentData();
        if (!PetrifyHelper.isPetrified(player)) {
            data.remove(TAG_SLOT);
            return;
        }
        if (!data.contains(TAG_SLOT)) {
            data.putInt(TAG_SLOT, player.getInventory().selected);
            return;
        }

        int slot = data.getInt(TAG_SLOT);
        if (player.getInventory().selected != slot) {
            player.getInventory().selected = slot;
            player.connection.send(new ClientboundSetCarriedItemPacket(slot));
        }
    }
}
