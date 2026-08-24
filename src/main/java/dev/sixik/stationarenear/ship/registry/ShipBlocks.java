package dev.sixik.stationarenear.ship.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.ship.block.PressureTightDoorBlock;
import dev.sixik.stationarenear.ship.block.entity.PressureTightDoorBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ShipBlocks {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StationAreNear.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StationAreNear.MODID);

    public static final RegistryObject<PressureTightDoorBlock> PRESSURE_TIGHT_DOOR = BLOCKS.register(
            "pressure_tight_door",
            () -> new PressureTightDoorBlock(BlockBehaviour.Properties.of()
                    .strength(4.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    .requiresCorrectToolForDrops())
    );

    public static final RegistryObject<Item> PRESSURE_TIGHT_DOOR_ITEM = ITEMS.register(
            "pressure_tight_door",
            () -> new BlockItem(PRESSURE_TIGHT_DOOR.get(), new Item.Properties())
    );

    public static final RegistryObject<BlockEntityType<PressureTightDoorBlockEntity>> PRESSURE_TIGHT_DOOR_ENTITY = BLOCK_ENTITIES.register(
            "pressure_tight_door",
            () -> BlockEntityType.Builder.of(PressureTightDoorBlockEntity::new, PRESSURE_TIGHT_DOOR.get()).build(null)
    );

    private ShipBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(ShipBlocks::addCreativeTabItems);
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(PRESSURE_TIGHT_DOOR_ITEM.get());
        }
    }
}
