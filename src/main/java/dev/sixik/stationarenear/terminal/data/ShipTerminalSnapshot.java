package dev.sixik.stationarenear.terminal.data;

import dev.sixik.stationarenear.navigation.data.SolarNavigationShipState;
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.ship.data.ShipState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

public record ShipTerminalSnapshot(
        BlockPos terminalPos,
        BlockPos shipStateTerminalPos,
        BlockPos navigationTerminalPos,
        boolean boundToShip,
        boolean docked,
        boolean doorOpen,
        boolean hullBreach,
        ShipState shipState,
        SolarNavigationShipState navigationState,
        List<SolarNavigationStationInfo> nearbyStations
) {

    public static final ShipTerminalSnapshot EMPTY = new ShipTerminalSnapshot(
            BlockPos.ZERO,
            BlockPos.ZERO,
            BlockPos.ZERO,
            false,
            false,
            false,
            false,
            ShipState.createDefault(),
            SolarNavigationShipState.DEFAULT,
            List.of()
    );

    public ShipTerminalSnapshot {
        shipState = shipState == null ? ShipState.createDefault() : shipState;
        navigationState = navigationState == null ? SolarNavigationShipState.DEFAULT : navigationState;
        nearbyStations = List.copyOf(nearbyStations == null ? List.of() : nearbyStations);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(terminalPos);
        buffer.writeBlockPos(shipStateTerminalPos);
        buffer.writeBlockPos(navigationTerminalPos);
        buffer.writeBoolean(boundToShip);
        buffer.writeBoolean(docked);
        buffer.writeBoolean(doorOpen);
        buffer.writeBoolean(hullBreach);
        shipState.encode(buffer);
        navigationState.encode(buffer);
        buffer.writeVarInt(nearbyStations.size());
        for (SolarNavigationStationInfo station : nearbyStations) {
            station.encode(buffer);
        }
    }

    public static ShipTerminalSnapshot decode(FriendlyByteBuf buffer) {
        BlockPos terminalPos = buffer.readBlockPos();
        BlockPos shipStateTerminalPos = buffer.readBlockPos();
        BlockPos navigationTerminalPos = buffer.readBlockPos();
        boolean boundToShip = buffer.readBoolean();
        boolean docked = buffer.readBoolean();
        boolean doorOpen = buffer.readBoolean();
        boolean hullBreach = buffer.readBoolean();
        ShipState shipState = ShipState.decode(buffer);
        SolarNavigationShipState navigationState = SolarNavigationShipState.decode(buffer);
        int stationCount = buffer.readVarInt();
        List<SolarNavigationStationInfo> stations = new ArrayList<>(stationCount);
        for (int i = 0; i < stationCount; i++) {
            stations.add(SolarNavigationStationInfo.decode(buffer));
        }
        return new ShipTerminalSnapshot(
                terminalPos,
                shipStateTerminalPos,
                navigationTerminalPos,
                boundToShip,
                docked,
                doorOpen,
                hullBreach,
                shipState,
                navigationState,
                stations
        );
    }
}
