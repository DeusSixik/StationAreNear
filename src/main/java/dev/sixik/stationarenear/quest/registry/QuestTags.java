package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class QuestTags {

    public static final TagKey<Block> TRASH_BLOCKS = block("trash_blocks");
    public static final TagKey<Block> REPAIRABLE_BLOCKS = block("repairable_blocks");
    public static final TagKey<Block> BUILD_TARGET_BLOCKS = block("build_target_blocks");
    public static final TagKey<Block> REPAIRABLE_PRESSURE_DOORS = block("repairable_pressure_doors");
    public static final TagKey<Block> HANGING_CABLES = block("hanging_cables");
    public static final TagKey<Item> MOPS = item("mops");

    private QuestTags() {
    }

    private static TagKey<Block> block(String path) {
        return TagKey.create(Registries.BLOCK, new ResourceLocation(StationAreNear.MODID, path));
    }

    private static TagKey<Item> item(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(StationAreNear.MODID, path));
    }
}
