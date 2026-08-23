package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record SyncStationTemplateSelectionsPacket(List<TemplateSelectionEntry> entries) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(saveEntries(entries));
    }

    public static SyncStationTemplateSelectionsPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        return new SyncStationTemplateSelectionsPacket(loadEntries(tag == null ? new CompoundTag() : tag));
    }

    public static void handle(SyncStationTemplateSelectionsPacket packet, java.util.function.Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
        net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.structures.client.StationEditorClientState.setTemplateSelections(packet.entries())
        ));
        context.setPacketHandled(true);
    }

    static CompoundTag saveEntries(List<TemplateSelectionEntry> entries) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (TemplateSelectionEntry entry : entries) {
            list.add(entry.save());
        }
        tag.put("entries", list);
        return tag;
    }

    static List<TemplateSelectionEntry> loadEntries(CompoundTag tag) {
        List<TemplateSelectionEntry> entries = new ArrayList<>();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (Tag entryTag : list) {
            entries.add(TemplateSelectionEntry.load((CompoundTag) entryTag));
        }
        return entries;
    }
}
