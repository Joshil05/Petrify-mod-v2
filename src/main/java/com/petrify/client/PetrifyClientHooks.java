package com.petrify.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/** Client-only entry points, kept in a separate class so the server never loads GUI code. */
public final class PetrifyClientHooks {
    private PetrifyClientHooks() {}

    public static void openScrollScreen(InteractionHand hand, int currentSeconds, int maxSeconds,
                                        boolean allowForever, boolean blockMode) {
        Minecraft.getInstance().setScreen(new ScrollScreen(hand, currentSeconds, maxSeconds, allowForever, blockMode));
    }
}
