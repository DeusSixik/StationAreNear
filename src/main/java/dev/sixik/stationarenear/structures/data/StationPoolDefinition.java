package dev.sixik.stationarenear.structures.data;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record StationPoolDefinition(
        ResourceLocation id,
        List<ResourceLocation> startPieces,
        List<ResourceLocation> roomPieces,
        int minRooms,
        int maxRooms
) {

    public StationPoolDefinition {
        startPieces = new ObjectArrayList<>(startPieces);
        roomPieces = new ObjectArrayList<>(roomPieces);
    }

    public StationPoolDefinition withPiece(ResourceLocation pieceId, boolean startPiece) {
        Set<ResourceLocation> starts = new LinkedHashSet<>(startPieces);
        Set<ResourceLocation> rooms = new LinkedHashSet<>(roomPieces);

        if (startPiece) {
            starts.add(pieceId);
        } else {
            rooms.add(pieceId);
        }

        return new StationPoolDefinition(id, List.copyOf(starts), List.copyOf(rooms), minRooms, maxRooms);
    }

    public StationPoolDefinition withoutPiece(ResourceLocation pieceId) {
        Set<ResourceLocation> starts = new LinkedHashSet<>(startPieces);
        Set<ResourceLocation> rooms = new LinkedHashSet<>(roomPieces);
        starts.remove(pieceId);
        rooms.remove(pieceId);
        return new StationPoolDefinition(id, List.copyOf(starts), List.copyOf(rooms), minRooms, maxRooms);
    }

    public StationPoolDefinition withRoomLimits(int minRooms, int maxRooms) {
        return new StationPoolDefinition(id, startPieces, roomPieces, Math.max(1, minRooms), Math.max(minRooms, maxRooms));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.put("startPieces", saveIds(startPieces));
        tag.put("roomPieces", saveIds(roomPieces));
        tag.putInt("minRooms", minRooms);
        tag.putInt("maxRooms", maxRooms);
        return tag;
    }

    public static StationPoolDefinition load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
        if (id == null) {
            throw new IllegalArgumentException("Invalid station pool resource location");
        }

        return new StationPoolDefinition(
                id,
                loadIds(tag.getList("startPieces", Tag.TAG_STRING)),
                loadIds(tag.getList("roomPieces", Tag.TAG_STRING)),
                tag.contains("minRooms") ? tag.getInt("minRooms") : 4,
                tag.contains("maxRooms") ? tag.getInt("maxRooms") : 10
        );
    }

    private static ListTag saveIds(List<ResourceLocation> ids) {
        ListTag list = new ListTag();
        for (ResourceLocation id : ids) {
            list.add(StringTag.valueOf(id.toString()));
        }
        return list;
    }

    private static List<ResourceLocation> loadIds(ListTag list) {
        List<ResourceLocation> ids = new ObjectArrayList<>();
        for (Tag tag : list) {
            if (tag instanceof StringTag stringTag) {
                ResourceLocation id = ResourceLocation.tryParse(stringTag.getAsString());
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
