package dev.sixik.stationarenear.quest.network.packet;

import dev.sixik.stationarenear.quest.client.QuestPlacementZoneClientState;
import dev.sixik.stationarenear.quest.data.QuestPlacementZoneHint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncQuestPlacementZonesPacket(List<QuestPlacementZoneHint> hints) {

    public static void encode(SyncQuestPlacementZonesPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.hints.size());
        for (QuestPlacementZoneHint hint : packet.hints) {
            hint.encode(buf);
        }
    }

    public static SyncQuestPlacementZonesPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<QuestPlacementZoneHint> hints = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            hints.add(QuestPlacementZoneHint.decode(buf));
        }
        return new SyncQuestPlacementZonesPacket(hints);
    }

    public static void handle(SyncQuestPlacementZonesPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> QuestPlacementZoneClientState.setHints(packet.hints())));
        context.setPacketHandled(true);
    }
}
