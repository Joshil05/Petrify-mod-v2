package com.petrify.net;

import com.petrify.item.PetrifyScrollItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent by the client when the player confirms the menu of a Scroll of Petrification. */
public record SetDurationPacket(boolean mainHand, int seconds, boolean blockMaterial) {

    public static void encode(SetDurationPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.mainHand);
        buf.writeVarInt(packet.seconds);
        buf.writeBoolean(packet.blockMaterial);
    }

    public static SetDurationPacket decode(FriendlyByteBuf buf) {
        return new SetDurationPacket(buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SetDurationPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            InteractionHand hand = packet.mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof PetrifyScrollItem scroll && !scroll.isRestore()) {
                // The server never trusts the client: the value is limited by the scroll itself.
                int max = scroll.getMaxSeconds();
                int seconds = scroll.isTiered() ? Mth.clamp(packet.seconds, 1, max) : Mth.clamp(packet.seconds, 0, max);
                CompoundTag tag = stack.getOrCreateTag();
                tag.putInt(PetrifyScrollItem.TAG_SECONDS, seconds);
                tag.putBoolean(PetrifyScrollItem.TAG_BLOCK_MATERIAL, packet.blockMaterial);
            }
        });
        context.setPacketHandled(true);
    }
}
