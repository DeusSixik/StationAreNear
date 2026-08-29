package dev.sixik.stationarenear.quest.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record QuestFurniturePickupProgressPacket(float progress, boolean visible, String title) {

    public QuestFurniturePickupProgressPacket(float progress, boolean visible) {
        this(progress, visible, "");
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(progress);
        buffer.writeBoolean(visible);
        buffer.writeUtf(title == null ? "" : title);
    }

    public static QuestFurniturePickupProgressPacket decode(FriendlyByteBuf buffer) {
        float progress = buffer.readFloat();
        boolean visible = buffer.readBoolean();
        String title = buffer.readableBytes() > 0 ? buffer.readUtf() : "";
        return new QuestFurniturePickupProgressPacket(progress, visible, title);
    }

    public static void handle(QuestFurniturePickupProgressPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.quest.client.QuestFurniturePickupOverlay.sync(packet.progress(), packet.visible(), packet.title())
        ));
        context.setPacketHandled(true);
    }
}
