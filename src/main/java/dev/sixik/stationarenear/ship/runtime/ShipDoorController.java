package dev.sixik.stationarenear.ship.runtime;

import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorResolver;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class ShipDoorController {

    private ShipDoorController() {
    }

    public static DoorControlResult status(ServerLevel level, BlockPos terminalPos) {
        Optional<ShipDockingAnchor> anchor = shipAnchor(level, terminalPos);
        if (anchor.isEmpty()) {
            return new DoorControlResult(false, false, false, "Door command failed: terminal is not bound to a ship.");
        }

        Optional<DoorLookup> door = findPressureDoor(level, anchor.get());
        if (door.isEmpty()) {
            return new DoorControlResult(false, false, false, "Door command failed: no pressure-tight door found in docking aperture.");
        }

        return new DoorControlResult(true, false, door.get().open(), "Pressure door: " + stateText(door.get().open()) + ".");
    }

    public static DoorControlResult setOpen(ServerLevel level, BlockPos terminalPos, boolean open) {
        Optional<ShipDockingAnchor> anchor = shipAnchor(level, terminalPos);
        if (anchor.isEmpty()) {
            return new DoorControlResult(false, false, false, "Door command failed: terminal is not bound to a ship.");
        }

        Optional<DoorLookup> door = findPressureDoor(level, anchor.get());
        if (door.isEmpty()) {
            return new DoorControlResult(false, false, false, "Door command failed: no pressure-tight door found in docking aperture.");
        }

        DoorLookup lookup = door.get();
        if (lookup.open() == open) {
            return new DoorControlResult(true, false, open, "Pressure door already " + stateText(open) + ".");
        }

        boolean changed = PressureTightDoorBlock.setOpen(level, lookup.masterPos(), open);
        if (!changed) {
            return new DoorControlResult(false, false, lookup.open(), "Door command failed: pressure door block is no longer valid.");
        }

        return new DoorControlResult(true, true, open, open ? "Pressure door opened." : "Pressure door sealed.");
    }

    private static Optional<ShipDockingAnchor> shipAnchor(ServerLevel level, BlockPos terminalPos) {
        return ShipDockingAnchorSavedData.get(level)
                .anchor(terminalPos)
                .or(() -> ShipDockingAnchorResolver.bindNearbyShip(level, terminalPos));
    }

    private static Optional<DoorLookup> findPressureDoor(ServerLevel level, ShipDockingAnchor anchor) {
        for (BlockPos pos : dockingAperture(anchor)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PressureTightDoorBlock)) {
                continue;
            }

            BlockPos masterPos = PressureTightDoorBlock.masterPos(pos, state);
            BlockState masterState = level.getBlockState(masterPos);
            if (masterState.getBlock() instanceof PressureTightDoorBlock) {
                return Optional.of(new DoorLookup(masterPos, PressureTightDoorBlock.isOpen(masterState)));
            }
        }
        return Optional.empty();
    }

    private static Iterable<BlockPos> dockingAperture(ShipDockingAnchor anchor) {
        Set<BlockPos> positions = new HashSet<>();
        Direction side = anchor.direction().getClockWise();
        int minSide = -anchor.width() / 2;
        int maxSide = anchor.width() - anchor.width() / 2 - 1;
        int minY = -anchor.height() / 2;
        int maxY = anchor.height() - anchor.height() / 2 - 1;
        for (int sideOffset = minSide; sideOffset <= maxSide; sideOffset++) {
            for (int yOffset = minY; yOffset <= maxY; yOffset++) {
                positions.add(anchor.anchorPos().relative(side, sideOffset).above(yOffset));
            }
        }
        return positions;
    }

    private static String stateText(boolean open) {
        return open ? "OPEN" : "SEALED";
    }

    public record DoorControlResult(boolean success, boolean changed, boolean open, String message) {
    }

    private record DoorLookup(BlockPos masterPos, boolean open) {
    }
}
