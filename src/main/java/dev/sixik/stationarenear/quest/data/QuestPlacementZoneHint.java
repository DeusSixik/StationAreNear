package dev.sixik.stationarenear.quest.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public record QuestPlacementZoneHint(
        String questId,
        String label,
        BlockPos min,
        BlockPos max,
        String requiredItemId,
        float red,
        float green,
        float blue,
        int textColor
) {
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(questId);
        buf.writeUtf(label);
        buf.writeBlockPos(min);
        buf.writeBlockPos(max);
        buf.writeUtf(requiredItemId);
        buf.writeFloat(red);
        buf.writeFloat(green);
        buf.writeFloat(blue);
        buf.writeInt(textColor);
    }

    public static QuestPlacementZoneHint decode(FriendlyByteBuf buf) {
        return new QuestPlacementZoneHint(
                buf.readUtf(),
                buf.readUtf(),
                buf.readBlockPos(),
                buf.readBlockPos(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readInt()
        );
    }
}
