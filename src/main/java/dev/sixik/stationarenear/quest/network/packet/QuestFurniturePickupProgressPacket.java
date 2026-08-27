package dev.sixik.stationarenear.quest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record QuestFurniturePickupProgressPacket(float progress, boolean visible) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(progress);
        buffer.writeBoolean(visible);
    }

    public static QuestFurniturePickupProgressPacket decode(FriendlyByteBuf buffer) {
        return new QuestFurniturePickupProgressPacket(buffer.readFloat(), buffer.readBoolean());
    }

    public static void handle(QuestFurniturePickupProgressPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.quest.client.QuestFurniturePickupOverlay.sync(packet.progress(), packet.visible())
        ));
        context.setPacketHandled(true);
    }
}
