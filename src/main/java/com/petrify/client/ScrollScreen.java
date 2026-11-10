package com.petrify.client;

import com.petrify.item.PetrifyScrollItem;
import com.petrify.net.PetrifyNetwork;
import com.petrify.net.SetDurationPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;

/**
 * Menu opened by right clicking a Scroll of Petrification in the air: sets how long the statue lasts
 * and what it is made of. Tiered scrolls cannot be set above their tier maximum and cannot be
 * "until restored".
 */
public class ScrollScreen extends Screen {
    private static final int[] CREATIVE_PRESETS = {10, 30, 60, 300, 600, 1800, 3600};
    private static final int[] TIER_PRESETS = {5, 10, 20, 30, 40, 60, 80};

    private final InteractionHand hand;
    private final int initialSeconds;
    private final int maxSeconds;
    private final boolean allowForever;
    private boolean blockMode;
    private EditBox field;

    public ScrollScreen(InteractionHand hand, int initialSeconds, int maxSeconds, boolean allowForever, boolean blockMode) {
        super(Component.translatable("gui.petrify.scroll.title"));
        this.hand = hand;
        this.initialSeconds = initialSeconds;
        this.maxSeconds = maxSeconds;
        this.allowForever = allowForever;
        this.blockMode = blockMode;
    }

    private int top() {
        return this.height / 2 - 62;
    }

    private int parseSeconds() {
        int value = 0;
        String text = this.field.getValue();
        if (!text.isEmpty()) {
            try {
                value = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                value = 0;
            }
        }
        if (this.allowForever) {
            return Mth.clamp(value, 0, this.maxSeconds);
        }
        // Tiered scrolls: an empty or zero field means "the full tier duration".
        return value <= 0 ? this.maxSeconds : Mth.clamp(value, 1, this.maxSeconds);
    }

    private Component modeLabel() {
        return Component.translatable("gui.petrify.mode",
                Component.translatable(this.blockMode ? "gui.petrify.mode.block" : "gui.petrify.mode.stone"));
    }

    private void addPreset(Component label, int seconds, int index) {
        int cx = this.width / 2;
        this.addRenderableWidget(Button.builder(label, button -> this.field.setValue(String.valueOf(seconds)))
                .bounds(cx - 124 + (index % 4) * 62, top() + (index / 4) * 24, 60, 20)
                .build());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int top = top();

        this.field = new EditBox(this.font, cx - 62, top + 58, 124, 20, Component.translatable("gui.petrify.seconds_label"));
        this.field.setFilter(text -> text.matches("\\d{0,6}"));
        this.field.setValue(String.valueOf(this.initialSeconds));

        int index = 0;
        int lastPreset = 0;
        for (int seconds : this.allowForever ? CREATIVE_PRESETS : TIER_PRESETS) {
            if (seconds <= this.maxSeconds) {
                addPreset(PetrifyScrollItem.formatDuration(seconds), seconds, index++);
                lastPreset = seconds;
            }
        }
        if (this.allowForever) {
            addPreset(Component.translatable("gui.petrify.forever"), 0, index);
        } else if (lastPreset != this.maxSeconds) {
            addPreset(Component.translatable("gui.petrify.max"), this.maxSeconds, index);
        }

        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        // Click to switch between plain stone and the block the target stands on.
        this.addRenderableWidget(Button.builder(modeLabel(), button -> {
                    this.blockMode = !this.blockMode;
                    button.setMessage(modeLabel());
                })
                .bounds(cx - 82, top + 106, 164, 20)
                .build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
                    PetrifyNetwork.CHANNEL.sendToServer(new SetDurationPacket(
                            this.hand == InteractionHand.MAIN_HAND, parseSeconds(), this.blockMode));
                    this.onClose();
                })
                .bounds(cx - 62, top + 132, 60, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(cx + 2, top + 132, 60, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int cx = this.width / 2;
        int top = top();

        Component label = this.allowForever
                ? Component.translatable("gui.petrify.seconds_label")
                : Component.translatable("gui.petrify.seconds_label_tier", this.maxSeconds);

        graphics.drawCenteredString(this.font, this.title, cx, top - 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, label, cx, top + 48, 0xAAAAAA);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, PetrifyScrollItem.formatDuration(parseSeconds()), cx, top + 84, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
