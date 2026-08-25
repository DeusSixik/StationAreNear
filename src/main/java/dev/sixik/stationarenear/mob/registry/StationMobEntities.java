package dev.sixik.stationarenear.mob.registry;

import dev.sixik.stationarenear.StationAreNear;
import dev.sixik.stationarenear.mob.entity.CadaverEntity;
import dev.sixik.stationarenear.mob.entity.LivingTrashEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class StationMobEntities {

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StationAreNear.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationAreNear.MODID);

    public static final RegistryObject<EntityType<LivingTrashEntity>> LIVING_TRASH = ENTITIES.register(
            "living_trash",
            () -> EntityType.Builder.of(LivingTrashEntity::new, MobCategory.MONSTER)
                    .sized(0.55F, 0.55F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build(StationAreNear.MODID + ":living_trash")
    );

    public static final RegistryObject<EntityType<CadaverEntity>> CADAVER = ENTITIES.register(
            "cadaver",
            () -> EntityType.Builder.of(CadaverEntity::new, MobCategory.MONSTER)
                    .sized(0.65F, 1.95F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .build(StationAreNear.MODID + ":cadaver")
    );

    public static final RegistryObject<Item> LIVING_TRASH_SPAWN_EGG = ITEMS.register(
            "living_trash_spawn_egg",
            () -> new ForgeSpawnEggItem(LIVING_TRASH, 0x6B4E2E, 0xA68B63, new Item.Properties())
    );

    public static final RegistryObject<Item> CADAVER_SPAWN_EGG = ITEMS.register(
            "cadaver_spawn_egg",
            () -> new ForgeSpawnEggItem(CADAVER, 0x4C3F38, 0x8E1F1F, new Item.Properties())
    );

    private StationMobEntities() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITIES.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(StationMobEntities::addCreativeTabItems);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(LIVING_TRASH.get(), LivingTrashEntity.createAttributes().build());
        event.put(CADAVER.get(), CadaverEntity.createAttributes().build());
    }

    private static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(LIVING_TRASH_SPAWN_EGG.get());
            event.accept(CADAVER_SPAWN_EGG.get());
        }
    }
}
