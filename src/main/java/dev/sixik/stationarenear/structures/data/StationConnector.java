package dev.sixik.stationarenear.structures.data;

import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Set;

public record StationConnector(
        String name,
        BlockPos position,
        Direction direction,
        Set<String> tags,
        Set<String> accepts,
        int priority,
        BlockPos min,
        BlockPos max,
        int width,
        int height,
        String acceptedSizes,
        boolean requiresPassage
) {

    public static final String KEY_REQUIRES_PASSAGE = "requiresPassage";

    public StationConnector(String name, BlockPos position, Direction direction, Set<String> tags, Set<String> accepts, int priority) {
        this(name, position, direction, tags, accepts, priority, position, position, 1, 1, "1x1", false);
    }

    public StationConnector(String name, BlockPos position, Direction direction, Set<String> tags, Set<String> accepts, int priority, BlockPos min, BlockPos max, int width, int height, String acceptedSizes) {
        this(name, position, direction, tags, accepts, priority, min, max, width, height, acceptedSizes, false);
    }

    public StationConnector {
        position = position == null ? BlockPos.ZERO : position;
        direction = direction == null ? Direction.NORTH : direction;
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        accepts = Set.copyOf(accepts == null ? Set.of() : accepts);
        min = min == null ? position : min;
        max = max == null ? position : max;
        BlockPos normalizedMin = new BlockPos(
                Math.min(min.getX(), max.getX()),
                Math.min(min.getY(), max.getY()),
                Math.min(min.getZ(), max.getZ())
        );
        BlockPos normalizedMax = new BlockPos(
                Math.max(min.getX(), max.getX()),
                Math.max(min.getY(), max.getY()),
                Math.max(min.getZ(), max.getZ())
        );
        min = normalizedMin;
        max = normalizedMax;
        width = Math.max(1, width);
        height = Math.max(1, height);
        acceptedSizes = acceptedSizes == null || acceptedSizes.isBlank() ? width + "x" + height : acceptedSizes.trim();
    }

    public boolean isCompatibleWith(StationConnector other) {
        return accepts(other.tags) && other.accepts(tags);
    }

    private boolean accepts(Set<String> otherTags) {
        if (accepts.isEmpty() || otherTags.isEmpty()) {
            return true;
        }

        for (String tag : otherTags) {
            if (accepts.contains(tag)) {
                return true;
            }
        }

        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", name);
        tag.put("position", NbtPos.save(position));
        tag.putString("direction", direction.getSerializedName());
        tag.put(TagsConstants.Keys.TAGS, saveStrings(tags));
        tag.put("accepts", saveStrings(accepts));
        tag.putInt("priority", priority);
        tag.put("min", NbtPos.save(min));
        tag.put("max", NbtPos.save(max));
        tag.putInt("width", width);
        tag.putInt("height", height);
        tag.putString("acceptedSizes", acceptedSizes);
        tag.putBoolean(KEY_REQUIRES_PASSAGE, requiresPassage);
        return tag;
    }

    public static StationConnector load(CompoundTag tag) {
        Direction direction = Direction.byName(tag.getString("direction"));
        if (direction == null) {
            direction = Direction.NORTH;
        }
        BlockPos position = NbtPos.load(tag.getCompound("position"));
        BlockPos min = tag.contains("min", Tag.TAG_COMPOUND) ? NbtPos.load(tag.getCompound("min")) : position;
        BlockPos max = tag.contains("max", Tag.TAG_COMPOUND) ? NbtPos.load(tag.getCompound("max")) : position;
        int width = tag.contains("width") ? tag.getInt("width") : Math.max(1, max.getX() - min.getX() + 1);
        int height = tag.contains("height") ? tag.getInt("height") : Math.max(1, max.getY() - min.getY() + 1);
        String acceptedSizes = tag.contains("acceptedSizes") ? tag.getString("acceptedSizes") : width + "x" + height;

        return new StationConnector(
                tag.getString("name"),
                position,
                direction,
                loadStrings(tag.getList(TagsConstants.Keys.TAGS, Tag.TAG_STRING)),
                loadStrings(tag.getList("accepts", Tag.TAG_STRING)),
                tag.getInt("priority"),
                min,
                max,
                width,
                height,
                acceptedSizes,
                loadRequiresPassage(tag)
        );
    }

    public static boolean loadRequiresPassage(CompoundTag tag) {
        return tag.getBoolean(KEY_REQUIRES_PASSAGE)
                || tag.getBoolean("requiredPassage")
                || tag.getBoolean("mustBuildPassage");
    }

    static ListTag saveStrings(Set<String> strings) {
        ListTag list = new ListTag();
        for (String value : strings) {
            list.add(StringTag.valueOf(value));
        }
        return list;
    }

    static Set<String> loadStrings(ListTag list) {
        Set<String> strings = new LinkedHashSet<>();
        for (Tag tag : list) {
            if (tag instanceof StringTag stringTag) {
                strings.add(stringTag.getAsString());
            }
        }
        return strings;
    }
}
