package com.petrify.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.petrify.PetrifyMod;
import com.petrify.registry.ModEffects;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** Draws the stone version of the entity's own skin over any living entity that has the Petrified effect. */
public class StoneLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    /** Used only while the real skin texture is not available yet. */
    private static final ResourceLocation FALLBACK_TEXTURE =
            new ResourceLocation(PetrifyMod.MOD_ID, "textures/entity/stone.png");

    /** The statue turns to stone feet first: legs, then torso and arms, then the head. */
    private static final boolean ANIMATE = true;

    /** 1.0 = fully stone. Lower values let a little of the original color show through. */
    private static final float STONE_ALPHA = 1.0F;
    /** Moment (0..1 of the animation) when each stage starts, and how long its fade lasts. */
    private static final float[] STAGE_START = {0.0F, 0.30F, 0.60F};
    private static final float STAGE_LENGTH = 0.40F;

    private final RenderLayerParent<T, M> parent;

    public StoneLayer(RenderLayerParent<T, M> parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        MobEffectInstance effect = entity.getEffect(ModEffects.PETRIFIED.get());
        if (effect == null) {
            return;
        }

        ResourceLocation skin = this.parent.getTextureLocation(entity);
        ClientMaterials.Entry material = ClientMaterials.get(entity.getId());
        ResourceLocation stone = material == null ? StoneTextures.get(skin)
                : StoneTextures.getMaterial(skin, material.state(), material.pos());
        if (stone == null && material != null) {
            stone = StoneTextures.get(skin);
        }
        if (stone == null) {
            stone = FALLBACK_TEXTURE;
        }

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(stone));

        // The effect starts at BASE_DURATION and counts down on both sides, so the elapsed time is
        // known even for entities that were already petrified when they came into view.
        float progress = 1.0F;
        if (ANIMATE) {
            float elapsed = (PetrifyMod.BASE_DURATION - effect.getDuration()) + partialTick;
            progress = Mth.clamp(elapsed / PetrifyMod.ANIMATION_TICKS, 0.0F, 1.0F);
        }

        if (progress >= 1.0F) {
            // Same geometry as the normal model (no scaling), so no gaps and no skin peeking through.
            this.getParentModel().renderToBuffer(poseStack, consumer,
                    packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, STONE_ALPHA);
        } else if (this.getParentModel() instanceof HumanoidModel<?> humanoid) {
            renderStaged(humanoid, poseStack, consumer, packedLight, progress);
        } else {
            // Models without arms and legs: the stone simply fades in.
            this.getParentModel().renderToBuffer(poseStack, consumer,
                    packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, STONE_ALPHA * progress);
        }
    }

    /**
     * Draws the stone in three passes, each with only some body parts visible. This does not depend on
     * where the camera is, unlike a height based effect.
     */
    private static void renderStaged(HumanoidModel<?> model, PoseStack poseStack, VertexConsumer consumer,
                                     int packedLight, float progress) {
        List<List<ModelPart>> groups = new ArrayList<>();
        List<ModelPart> legs = new ArrayList<>(List.of(model.rightLeg, model.leftLeg));
        List<ModelPart> torso = new ArrayList<>(List.of(model.body, model.rightArm, model.leftArm));
        List<ModelPart> head = new ArrayList<>(List.of(model.head, model.hat));
        if (model instanceof PlayerModel<?> player) {
            legs.add(player.leftPants);
            legs.add(player.rightPants);
            torso.add(player.jacket);
            torso.add(player.leftSleeve);
            torso.add(player.rightSleeve);
        }
        groups.add(legs);
        groups.add(torso);
        groups.add(head);

        List<boolean[]> original = new ArrayList<>();
        for (List<ModelPart> group : groups) {
            boolean[] visible = new boolean[group.size()];
            for (int i = 0; i < visible.length; i++) {
                visible[i] = group.get(i).visible;
            }
            original.add(visible);
        }

        for (int g = 0; g < groups.size(); g++) {
            float alpha = Mth.clamp((progress - STAGE_START[g]) / STAGE_LENGTH, 0.0F, 1.0F) * STONE_ALPHA;
            if (alpha <= 0.0F) {
                continue;
            }
            for (List<ModelPart> group : groups) {
                for (ModelPart part : group) {
                    part.visible = false;
                }
            }
            List<ModelPart> group = groups.get(g);
            for (int i = 0; i < group.size(); i++) {
                group.get(i).visible = original.get(g)[i];
            }
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, alpha);
        }

        for (int g = 0; g < groups.size(); g++) {
            for (int i = 0; i < groups.get(g).size(); i++) {
                groups.get(g).get(i).visible = original.get(g)[i];
            }
        }
    }
}
