package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.quest.block.ConsoleNoAngleBlock;
import dev.sixik.stationarenear.quest.block.EnergyPanelBlock;
import dev.sixik.stationarenear.quest.block.FridgeBlock;
import dev.sixik.stationarenear.quest.block.KitchenSinkBlock;
import dev.sixik.stationarenear.quest.block.MicrowaveBlock;
import dev.sixik.stationarenear.quest.block.WallMountedPanelBlock;
import dev.sixik.stationarenear.quest.block.WorkbenchBlock;
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
                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false))
    );

    public static final RegistryObject<Item> FRIDGE_ITEM = ITEMS.register(
            "fridge",
            () -> new dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem(FRIDGE.get(), new Item.Properties().stacksTo(1))
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
            () -> new dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem(KITCHEN_SINK.get(), new Item.Properties().stacksTo(1))
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
            () -> new dev.sixik.stationarenear.quest.item.HeavyApplianceBlockItem(MICROWAVE.get(), new Item.Properties().stacksTo(1))
    );


    public static final RegistryObject<EnergyPanelBlock> ENERGY_PANEL = BLOCKS.register(
            "energy_panel",
            () -> new EnergyPanelBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false))
    );

    public static final RegistryObject<Item> ENERGY_PANEL_ITEM = ITEMS.register(
            "energy_panel",
            () -> new BlockItem(ENERGY_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BROKEN_ENERGY_PANEL_ITEM = ITEMS.register(
            "broken_energy_panel",
            () -> new dev.sixik.stationarenear.quest.item.BrokenEnergyPanelItem(ENERGY_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<WallMountedPanelBlock> OXYGEN_PANEL = BLOCKS.register(
            "oxygen_panel",
            () -> new WallMountedPanelBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false), 4.0D)
    );

    public static final RegistryObject<Item> OXYGEN_PANEL_ITEM = ITEMS.register(
            "oxygen_panel",
            () -> new BlockItem(OXYGEN_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BROKEN_OXYGEN_PANEL_ITEM = ITEMS.register(
            "broken_oxygen_panel",
            () -> new dev.sixik.stationarenear.quest.item.BrokenWallMountedPanelItem(OXYGEN_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<WallMountedPanelBlock> GRAVITATION_PANEL = BLOCKS.register(
            "gravitation_panel",
            () -> new WallMountedPanelBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F, 1200.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false), 6.0D)
    );

    public static final RegistryObject<Item> GRAVITATION_PANEL_ITEM = ITEMS.register(
            "gravitation_panel",
            () -> new BlockItem(GRAVITATION_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<Item> BROKEN_GRAVITATION_PANEL_ITEM = ITEMS.register(
            "broken_gravitation_panel",
            () -> new dev.sixik.stationarenear.quest.item.BrokenWallMountedPanelItem(GRAVITATION_PANEL.get(), new Item.Properties())
    );

    public static final RegistryObject<ConsoleNoAngleBlock> CONSOLE_NO_ANGLE = BLOCKS.register(
            "console_no_angle",
            () -> new ConsoleNoAngleBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Item> CONSOLE_NO_ANGLE_ITEM = ITEMS.register(
            "console_no_angle",
            () -> new BlockItem(CONSOLE_NO_ANGLE.get(), new Item.Properties())
    );

    public static final RegistryObject<WorkbenchBlock> WORKBENCH = BLOCKS.register(
            "workbench",
            () -> new WorkbenchBlock(BlockBehaviour.Properties.of()
                    .strength(2.5F, 8.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, getter, pos) -> false)
                    .isSuffocating((state, getter, pos) -> false)
                    .isRedstoneConductor((state, getter, pos) -> false))
    );

    public static final RegistryObject<Item> WORKBENCH_ITEM = ITEMS.register(
            "workbench",
            () -> new BlockItem(WORKBENCH.get(), new Item.Properties())
    );

    private QuestBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
