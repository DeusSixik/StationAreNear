package dev.sixik.stationarenear.quest.item;

import dev.sixik.stationarenear.minigames.ForkInSocketMinigameScreen;
import dev.sixik.stationarenear.quest.network.QuestNetwork;
import dev.sixik.stationarenear.quest.registry.QuestBlocks;
import dev.sixik.stationarenear.quest.registry.StationSounds;
import dev.sixik.stationarenear.quest.runtime.SocketPlacementHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class HeavyApplianceBlockItem extends BlockItem {

    public HeavyApplianceBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Item item = context.getItemInHand().getItem();
        boolean isSocketAppliance = (item == QuestBlocks.FRIDGE_ITEM.get() || item == QuestBlocks.MICROWAVE_ITEM.get());

        if (isSocketAppliance) {
            Level level = context.getLevel();
            BlockPos clickedPos = context.getClickedPos();
            BlockPos placePos = clickedPos.relative(context.getClickedFace());

            if (SocketPlacementHelper.isNearSocket(level, clickedPos) || SocketPlacementHelper.isNearSocket(level, placePos)) {
                if (level.isClientSide) {
                    BlockPos targetClickedPos = clickedPos.immutable();
                    Direction targetClickedFace = context.getClickedFace();
                    InteractionHand targetHand = context.getHand();

                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                        ForkInSocketMinigameScreen.open(
                                () -> QuestNetwork.sendForkInSocketSuccess(targetClickedPos, targetClickedFace, targetHand),
                                () -> {
                                    if (Minecraft.getInstance().player != null) {
                                        Minecraft.getInstance().player.playSound(StationSounds.ELECTRIC_SHOCK.get(), 0.8F, 1.0F);
                                    }
                                    QuestNetwork.sendForkInSocketMiss();
                                }
                        );
                    });
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return super.useOn(context);
    }

    public static boolean isRestrictedFromChests(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof HeavyApplianceBlockItem
                || item == QuestBlocks.FRIDGE_ITEM.get()
                || item == QuestBlocks.MICROWAVE_ITEM.get()
                || item == QuestBlocks.KITCHEN_SINK_ITEM.get();
    }
}
