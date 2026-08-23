package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record SaveStationZoneEditorPacket(CompoundTag editorTag, boolean saveStructure, boolean allowOverwrite, boolean clearWand) {

    public SaveStationZoneEditorPacket(CompoundTag editorTag) {
        this(editorTag, false, false, false);
    }

    public SaveStationZoneEditorPacket(CompoundTag editorTag, boolean saveStructure) {
        this(editorTag, saveStructure, false, false);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeNbt(editorTag);
        buffer.writeBoolean(saveStructure);
        buffer.writeBoolean(allowOverwrite);
        buffer.writeBoolean(clearWand);
    }

    public static SaveStationZoneEditorPacket decode(FriendlyByteBuf buffer) {
        CompoundTag tag = buffer.readNbt();
        boolean saveStructure = buffer.readBoolean();
        boolean allowOverwrite = buffer.readBoolean();
        boolean clearWand = buffer.readBoolean();
        return new SaveStationZoneEditorPacket(tag == null ? new CompoundTag() : tag, saveStructure, allowOverwrite, clearWand);
    }

    public static void handle(SaveStationZoneEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            if (packet.clearWand() && !packet.saveStructure()) {
                StationStructureEditorStick.clearEditorTag(player);
                return;
            }

            StationStructureEditorStick.applyEditorTag(player, packet.editorTag());
            if (packet.saveStructure()) {
                boolean saved = StationStructureToolItem.saveStructure(player.serverLevel(), player, player.getMainHandItem(), packet.allowOverwrite());
                if (saved && packet.clearWand()) {
                    StationStructureEditorStick.clearEditorTag(player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
