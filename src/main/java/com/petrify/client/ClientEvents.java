package com.petrify.client;

import com.petrify.PetrifyMod;
import com.petrify.util.PetrifyHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    /** Whether the local player was crouching just before turning to stone (the statue keeps that stance). */
    private static boolean localWasCrouching = false;

    /**
     * A petrified player cannot walk, jump or change stance (the server also enforces the position).
     * The only thing that is kept is the crouch they were in when they were hit.
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!PetrifyHelper.isPetrified(event.getEntity())) {
            return;
        }
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        input.shiftKeyDown = localWasCrouching;
    }

    // ---------------------------------------------------------------------------------------
    // Client tick: remembers the walking pose of everything that is not stone yet, and locks the
    // held item of a petrified local player (slot keys, scrolling, swapping hands, dropping and
    // the inventory screen are all blocked).
    // ---------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        // The latest walking state before petrification is what the statue will freeze in.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && !PetrifyHelper.isPetrified(living)) {
                WalkAnimationState walk = living.walkAnimation;
                LAST_WALK.put(living, new WalkSnapshot(walk.speed(), walk.position()));
            }
        }

        if (mc.level.getGameTime() % 200L == 0L) {
            ClientMaterials.prune(mc.level);
        }

        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (!PetrifyHelper.isPetrified(player)) {
            localWasCrouching = player.isCrouching();
            return;
        }

        // Throw away key presses before the game gets to act on them.
        for (KeyMapping key : mc.options.keyHotbarSlots) {
            while (key.consumeClick()) {
                // discard
            }
        }
        while (mc.options.keySwapOffhand.consumeClick()) {
            // discard
        }
        while (mc.options.keyInventory.consumeClick()) {
            // discard
        }
        while (mc.options.keyDrop.consumeClick()) {
            // discard
        }

        // No attacking, digging or using items: this also stops the arm swing animation,
        // which the game starts on the client the moment the button is pressed.
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        while (mc.options.keyAttack.consumeClick()) {
            // discard
        }
        while (mc.options.keyUse.consumeClick()) {
            // discard
        }

        // If the inventory was already open when the player turned to stone, close it.
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            player.closeContainer();
        }
    }

    /** Backup for the above: cancels any attack / use / pick-block click and its arm swing. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && PetrifyHelper.isPetrified(player)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && PetrifyHelper.isPetrified(player)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Frozen pose: while a petrified entity is being drawn, its head, body, pitch, animation clock
    // and walking animation are replaced with the values from the moment it turned to stone. The
    // head/body values are put back right after drawing, so the camera and the game logic are not
    // affected.
    // ---------------------------------------------------------------------------------------

    private static final class Frozen {
        final float bodyYaw;
        final float headYaw;
        final float pitch;
        final int tick;

        Frozen(LivingEntity e) {
            this.bodyYaw = e.yBodyRot;
            this.headYaw = e.yHeadRot;
            this.pitch = e.getXRot();
            this.tick = e.tickCount;
        }
    }

    private static final class Saved {
        final float bodyYaw, bodyYawO, headYaw, headYawO, pitch, pitchO, attack, attackO;
        final int tick, hurt;

        Saved(LivingEntity e) {
            this.bodyYaw = e.yBodyRot;
            this.bodyYawO = e.yBodyRotO;
            this.headYaw = e.yHeadRot;
            this.headYawO = e.yHeadRotO;
            this.pitch = e.getXRot();
            this.pitchO = e.xRotO;
            this.tick = e.tickCount;
            this.attack = e.attackAnim;
            this.attackO = e.oAttackAnim;
            this.hurt = e.hurtTime;
        }

        void restore(LivingEntity e) {
            e.yBodyRot = bodyYaw;
            e.yBodyRotO = bodyYawO;
            e.yHeadRot = headYaw;
            e.yHeadRotO = headYawO;
            e.setXRot(pitch);
            e.xRotO = pitchO;
            e.tickCount = tick;
            e.attackAnim = attack;
            e.oAttackAnim = attackO;
            e.hurtTime = hurt;
        }
    }

    private static final class WalkSnapshot {
        final float speed;
        final float position;

        WalkSnapshot(float speed, float position) {
            this.speed = speed;
            this.position = position;
        }
    }

    private static final Map<LivingEntity, Frozen> FROZEN = new WeakHashMap<>();
    private static final Map<LivingEntity, Saved> SAVED = new WeakHashMap<>();
    private static final Map<LivingEntity, WalkSnapshot> LAST_WALK = new WeakHashMap<>();
    private static final Map<LivingEntity, WalkSnapshot> FROZEN_WALK = new WeakHashMap<>();

    /**
     * Forces the walking animation to show exactly the limb pose of the snapshot, every frame.
     *
     * The animation state only offers "set speed" and "advance by speed", and what is drawn is
     * speed(partialTick) for the swing size and position(partialTick) = position - speed * (1 - partialTick)
     * for the stride phase. So we first add whatever is needed to reach the wanted position, then
     * leave the speed (and previous speed) at the snapshot value.
     */
    private static void applyWalk(WalkAnimationState walk, WalkSnapshot snapshot, float partialTick) {
        float speed = snapshot.speed;
        float wantedPosition = snapshot.position + speed * (1.0F - partialTick);
        float adjust = wantedPosition - walk.position() - speed;

        walk.setSpeed(adjust);
        walk.update(adjust, 0.0F); // position += adjust, previous speed = speed = adjust
        walk.setSpeed(speed);
        walk.update(speed, 0.0F);  // position += speed, previous speed = speed = snapshot speed
    }

    @SubscribeEvent
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!PetrifyHelper.isPetrified(entity)) {
            FROZEN.remove(entity);
            FROZEN_WALK.remove(entity);
            return;
        }

        Frozen frozen = FROZEN.computeIfAbsent(entity, Frozen::new);
        SAVED.put(entity, new Saved(entity));

        entity.yBodyRot = frozen.bodyYaw;
        entity.yBodyRotO = frozen.bodyYaw;
        entity.yHeadRot = frozen.headYaw;
        entity.yHeadRotO = frozen.headYaw;
        entity.setXRot(frozen.pitch);
        entity.xRotO = frozen.pitch;
        entity.tickCount = frozen.tick;
        // A statue never swings its arms or flashes red when hit, whatever the source of the animation.
        entity.attackAnim = 0.0F;
        entity.oAttackAnim = 0.0F;
        entity.hurtTime = 0;

        WalkSnapshot walk = FROZEN_WALK.get(entity);
        if (walk == null) {
            WalkSnapshot last = LAST_WALK.get(entity);
            walk = last != null ? last : new WalkSnapshot(0.0F, entity.walkAnimation.position());
            FROZEN_WALK.put(entity, walk);
        }
        applyWalk(entity.walkAnimation, walk, event.getPartialTick());
    }

    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        Saved saved = SAVED.remove(entity);
        if (saved != null) {
            saved.restore(entity);
        }
    }
}
