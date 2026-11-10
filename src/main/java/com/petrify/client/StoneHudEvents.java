package com.petrify.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.petrify.PetrifyMod;
import com.petrify.util.PetrifyHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While the local player is petrified, the hearts and the hunger bar are drawn in gray stone
 * instead of red and brown. The values are the real ones, only the look changes.
 */
@Mod.EventBusSubscriber(modid = PetrifyMod.MOD_ID, value = Dist.CLIENT)
public class StoneHudEvents {
    private static final ResourceLocation ICONS = new ResourceLocation(PetrifyMod.MOD_ID, "textures/gui/stone_icons.png");
    private static final int SHEET = 32;

    // Positions of the 9x9 icons inside the sheet.
    private static final int HEART_V = 0;
    private static final int FOOD_V = 9;
    private static final int CONTAINER_U = 0;
    private static final int FULL_U = 9;
    private static final int HALF_U = 18;

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer()
                || !(mc.getCameraEntity() instanceof Player) || !PetrifyHelper.isPetrified(player)
                || !(mc.gui instanceof ForgeGui gui)) {
            return;
        }

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        if (event.getOverlay() == VanillaGuiOverlay.PLAYER_HEALTH.type()) {
            event.setCanceled(true);
            drawHearts(event.getGuiGraphics(), player, gui, width, height);
        } else if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
            // While riding a living mount the vanilla game shows the mount's health here instead.
            if (player.getVehicle() instanceof LivingEntity) {
                return;
            }
            event.setCanceled(true);
            drawFood(event.getGuiGraphics(), player, gui, width, height);
        }
    }

    private static void icon(GuiGraphics graphics, int x, int y, int u, int v) {
        graphics.blit(ICONS, x, y, (float) u, (float) v, 9, 9, SHEET, SHEET);
    }

    private static void drawHearts(GuiGraphics graphics, LocalPlayer player, ForgeGui gui, int width, int height) {
        int health = Mth.ceil(player.getHealth());
        float maxHealth = player.getMaxHealth();
        int absorption = Mth.ceil(player.getAbsorptionAmount());

        int hearts = Mth.ceil(maxHealth / 2.0F);
        int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (rows - 2), 3);

        int left = width / 2 - 91;
        int top = height - gui.leftHeight;

        // Keep the other bars (armor, air...) where they would normally be.
        gui.leftHeight += rows * rowHeight;
        if (rowHeight != 10) {
            gui.leftHeight += 10 - rowHeight;
        }

        RenderSystem.enableBlend();
        for (int i = 0; i < hearts; i++) {
            int x = left + (i % 10) * 8;
            int y = top - (i / 10) * rowHeight;
            icon(graphics, x, y, CONTAINER_U, HEART_V);
            if (health >= 2 * i + 2) {
                icon(graphics, x, y, FULL_U, HEART_V);
            } else if (health == 2 * i + 1) {
                icon(graphics, x, y, HALF_U, HEART_V);
            }
        }
        RenderSystem.disableBlend();
    }

    private static void drawFood(GuiGraphics graphics, LocalPlayer player, ForgeGui gui, int width, int height) {
        int food = player.getFoodData().getFoodLevel();
        int left = width / 2 + 91;
        int top = height - gui.rightHeight;
        gui.rightHeight += 10;

        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int x = left - i * 8 - 9;
            icon(graphics, x, top, CONTAINER_U, FOOD_V);
            if (food >= 2 * i + 2) {
                icon(graphics, x, top, FULL_U, FOOD_V);
            } else if (food == 2 * i + 1) {
                icon(graphics, x, top, HALF_U, FOOD_V);
            }
        }
        RenderSystem.disableBlend();
    }
}
