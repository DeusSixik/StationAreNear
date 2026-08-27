package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.block.EnergyPanelBlock;
import dev.sixik.stationarenear.quest.block.FridgeBlock;
import dev.sixik.stationarenear.quest.block.KitchenSinkBlock;
import dev.sixik.stationarenear.quest.block.MicrowaveBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class QuestBlocks {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StationAreNear.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);

    public static final RegistryObject<FridgeBlock> FRIDGE = BLOCKS.register(
            "fridge",
            () -> new FridgeBlock(BlockBehaviour.Properties.of()
                    .strength(1.8F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final RegistryObject<Item> FRIDGE_ITEM = ITEMS.register(
            "fridge",
            () -> new BlockItem(FRIDGE.get(), new Item.Properties())
    );

    public static final RegistryObject<KitchenSinkBlock> KITCHEN_SINK = BLOCKS.register(
            "kitchen_sink",
            () -> new KitchenSinkBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion())
    );

    public static final RegistryObject<Item> KITCHEN_SINK_ITEM = ITEMS.register(
            "kitchen_sink",
            () -> new BlockItem(KITCHEN_SINK.get(), new Item.Properties())
    );

    public static final RegistryObject<MicrowaveBlock> MICROWAVE = BLOCKS.register(
            "microwave",
            () -> new MicrowaveBlock(BlockBehaviour.Properties.of()
                    .strength(1.2F, 4.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final RegistryObject<Item> MICROWAVE_ITEM = ITEMS.register(
            "microwave",
            () -> new BlockItem(MICROWAVE.get(), new Item.Properties())
    );


    public static final RegistryObject<EnergyPanelBlock> ENERGY_PANEL = BLOCKS.register(
            "energy_panel",
            () -> new EnergyPanelBlock(BlockBehaviour.Properties.of()
                    .strength(1.6F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion())
    );

    public static final RegistryObject<Item> ENERGY_PANEL_ITEM = ITEMS.register(
            "energy_panel",
            () -> new BlockItem(ENERGY_PANEL.get(), new Item.Properties())
    );

    private QuestBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
