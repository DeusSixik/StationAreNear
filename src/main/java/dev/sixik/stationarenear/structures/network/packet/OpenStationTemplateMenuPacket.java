package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record OpenStationTemplateMenuPacket(List<TemplateSelectionEntry> entries) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(SyncStationTemplateSelectionsPacket.saveEntries(entries));
    }

    public static OpenStationTemplateMenuPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new OpenStationTemplateMenuPacket(SyncStationTemplateSelectionsPacket.loadEntries(tag == null ? new CompoundTag() : tag));
    }

    public static void handle(OpenStationTemplateMenuPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        // Просто почему-то без этой срани крашит на сервере :/
        net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.structures.client.StationTemplateSelectionClient.open(packet.entries())
        ));
        context.setPacketHandled(true);
    }
}
