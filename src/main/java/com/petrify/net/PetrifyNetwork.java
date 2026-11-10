package com.petrify.net;

import com.petrify.PetrifyMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class PetrifyNetwork {
    private static final String PROTOCOL = "2";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(PetrifyMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private PetrifyNetwork() {}

    public static void register() {
        CHANNEL.registerMessage(0, SetDurationPacket.class,
                SetDurationPacket::encode, SetDurationPacket::decode, SetDurationPacket::handle);
        CHANNEL.registerMessage(1, MaterialPacket.class,
                MaterialPacket::encode, MaterialPacket::decode, MaterialPacket::handle);
    }
}
