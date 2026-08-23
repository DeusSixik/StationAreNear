package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.client.StationZoneEditorClient;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record OpenStationZoneEditorPacket(CompoundTag editorTag, List<ResourceLocation> poolIds) {

    public OpenStationZoneEditorPacket(CompoundTag editorTag) {
        this(editorTag, List.of());
    }

    public static OpenStationZoneEditorPacket fromStack(ItemStack stack) {
        return new OpenStationZoneEditorPacket(StationStructureEditorStick.editorTag(stack));
    }

    public OpenStationZoneEditorPacket withPoolIds(List<ResourceLocation> poolIds) {
        return new OpenStationZoneEditorPacket(editorTag, poolIds);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(editorTag);
        buffer.writeVarInt(poolIds.size());
        for (ResourceLocation poolId : poolIds) {
            buffer.writeResourceLocation(poolId);
        }
    }

    public static OpenStationZoneEditorPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        int poolCount = buffer.readVarInt();
        ObjectArrayList<ResourceLocation> poolIds = new ObjectArrayList<>(poolCount);
        for (int i = 0; i < poolCount; i++) {
            poolIds.add(buffer.readResourceLocation());
        }
        return new OpenStationZoneEditorPacket(tag == null ? new CompoundTag() : tag, poolIds);
    }

    public static void handle(OpenStationZoneEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                StationZoneEditorClient.open(packet)
        ));
        context.setPacketHandled(true);
    }
}
