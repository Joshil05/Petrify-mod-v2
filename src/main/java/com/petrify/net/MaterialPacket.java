package com.petrify.net;

import com.petrify.client.ClientMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: what a statue is made of. {@code stateId} is a block state id, or -1 for plain stone.
 * {@code pos} is the block it stands on (used for biome tints).
 */
public record MaterialPacket(int entityId, int stateId, BlockPos pos) {

    public static void encode(MaterialPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeInt(packet.stateId);
        buf.writeBlockPos(packet.pos);
    }

    public static MaterialPacket decode(FriendlyByteBuf buf) {
        return new MaterialPacket(buf.readVarInt(), buf.readInt(), buf.readBlockPos());
    }

    public static void handle(MaterialPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientMaterials.set(packet.entityId, packet.stateId, packet.pos)));
        context.setPacketHandled(true);
    }
}
