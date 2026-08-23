package dev.sixik.stationarenear.structures.network.packet;

import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.world.StationTemplateSelectionManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TemplateSelectionActionPacket(String template, String action) {

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(template);
        buffer.writeUtf(action);
    }

    public static TemplateSelectionActionPacket decode(FriendlyByteBuf buffer) {
        return new TemplateSelectionActionPacket(buffer.readUtf(), buffer.readUtf());
    }

    public static void handle(TemplateSelectionActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                if (packet.action().equals("edit")) {
                    StationTemplateSelectionManager.edit(player, packet.template());
                } else if (packet.action().equals("delete")) {
                    StationTemplateSelectionManager.delete(player, packet.template());
                } else if (packet.action().equals("open")) {
                    StationStructureNetwork.openTemplateMenu(player);
                }
            }
        });
        context.setPacketHandled(true);
    }
}
