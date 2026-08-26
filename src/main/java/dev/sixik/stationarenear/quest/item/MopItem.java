package dev.sixik.stationarenear.quest.item;

import dev.sixik.stationarenear.quest.registry.QuestTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class MopItem extends Item {

    public MopItem(Properties properties) {
        super(properties);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (state.is(QuestTags.TRASH_BLOCKS)) {
            return 12.0F;
        }
        return super.getDestroySpeed(stack, state);
    }
}
