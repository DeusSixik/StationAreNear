package dev.sixik.stationarenear.terminal.network.packet;

import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record SyncTerminalHistoryPacket(BlockPos terminalPos, List<TerminalHistoryLine> history) {

    public SyncTerminalHistoryPacket {
        history = List.copyOf(history == null ? List.of() : history);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeVarInt(history.size());
        for (TerminalHistoryLine line : history) {
            line.encode(buffer);
        }
    }

    public static SyncTerminalHistoryPacket decode(FriendlyByteBuf buffer) {
        BlockPos terminalPos = buffer.readBlockPos();
        int count = buffer.readVarInt();
        List<TerminalHistoryLine> history = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            history.add(TerminalHistoryLine.decode(buffer));
        }
        return new SyncTerminalHistoryPacket(terminalPos, history);
    }

    public static void handle(SyncTerminalHistoryPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                dev.sixik.stationarenear.terminal.client.RetroTerminalScreen.syncHistory(packet.terminalPos(), packet.history())
        ));
        context.setPacketHandled(true);
    }
}
