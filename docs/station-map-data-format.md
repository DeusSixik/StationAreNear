# Station structure and map data format

This file describes the data that the Station Map Terminal needs. It is intentionally UI-agnostic: render it in UniGUI, ImGui, Swing, web canvas, or any other UI.

## Where generated stations are stored

Generated stations are stored in Minecraft `SavedData` named:

```text
stationarenear_stations
```

Root NBT shape:

```text
stations: List<Compound>
activatedTriggers: List<Compound>
```

`activatedTriggers` is only runtime trigger state. The geometry lives in `stations`.

## StationInstance NBT

Each entry in `stations` is `StationInstance`:

```text
id: UUID
pool: string ResourceLocation
shuttleDoorCenter: { x:int, y:int, z:int }
stationDirection: string Direction
 danger: float
seed: long
customData: Compound
pieces: List<PlacedStationPiece>
```

Important map anchors:

- `shuttleDoorCenter` is the docking origin.
- The floor containing `shuttleDoorCenter.y` is map level `0`.
- Current floor calculation is `floor = floorDiv(worldY - shuttleDoorCenter.y, 16)`.

## PlacedStationPiece NBT

Each room/piece is saved as:

```text
definitionId: string ResourceLocation
template: string ResourceLocation
origin: { x:int, y:int, z:int }
rotation: string Rotation
bounds: { minX:int, minY:int, minZ:int, maxX:int, maxY:int, maxZ:int }
selectionBounds: { minX:int, minY:int, minZ:int, maxX:int, maxY:int, maxZ:int }
openConnectors: List<StationConnector>
triggerZones: List<PlacedTriggerZone>
```

For the map, prefer `selectionBounds`. This is the authored structure zone and is more stable than raw template bounds.

## StationConnector NBT

Connector shape:

```text
name: string
position: { x:int, y:int, z:int }
direction: string Direction
tags: List<string>
accepts: List<string>
priority: int
min: { x:int, y:int, z:int }
max: { x:int, y:int, z:int }
width: int
height: int
acceptedSizes: string
```

`PlacedStationPiece.openConnectors` contains only currently unconnected/dead-end connectors. For visible passages on the map, use all connectors from `StationPieceDefinition.connectors`, transform them by `piece.origin + piece.rotation`, and filter out:

- vertical connectors (`UP` / `DOWN`), unless your map wants stairs/lifts;
- connectors present in `piece.openConnectors`, because they are dead ends.

## UI-independent map DTO

The portable DTO is here:

```text
src/main/java/dev/sixik/stationarenear/terminal/map/model/StationMapData.java
```

It has no Minecraft, UniGUI, packet, or rendering imports. Main shape:

```java
StationMapData(
    String stationId,
    String stationCode,
    String poolId,
    float danger,
    long seed,
    Point3i dockWorld,
    int minFloor,
    int maxFloor,
    List<Room> rooms
)
```

Room shape:

```java
Room(
    String id,
    String templateId,
    int minFloor,
    int maxFloor,
    Box3i worldBounds,
    Box3i worldSelectionBounds,
    Box3i localSelectionBounds,
    boolean dockRoom,
    List<Passage> passages
)
```

Passage shape:

```java
Passage(
    String id,
    int floor,
    Point3i worldPosition,
    Point3i localPosition,
    String direction,
    Box3i worldBounds,
    Box3i localBounds,
    int width,
    int height,
    String acceptedSizes
)
```

`local*` coordinates are already relative to `dockWorld`, so a renderer can ignore Minecraft world coordinates.

## How to get map data on server

Use:

```java
Optional<StationMapData> data = StationMapSnapshotFactory.createData(serverLevel, terminalPos);
```

This returns empty if the ship is not docked to a generated station.

## Simple renderer rules

Recommended renderer logic:

1. Pick an active floor, default `0`.
2. Draw rooms where `room.minFloor <= activeFloor <= room.maxFloor`.
3. Use `room.localSelectionBounds.minX/minZ/maxX/maxZ` as X/Z map coordinates.
4. If rooms must look square, render a square around the room center with side `max(widthX, depthZ)`.
5. Draw `room.passages` only when `passage.floor == activeFloor`.
6. `room.dockRoom == true` is the ship/docking room. Render it as blue and label it `?? ?????` or `You are here`.
