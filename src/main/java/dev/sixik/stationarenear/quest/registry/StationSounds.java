package dev.sixik.stationarenear.quest.registry;

import dev.sixik.stationarenear.StationAreNear;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class StationSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, StationAreNear.MODID);

    public static final RegistryObject<SoundEvent> LIGHT_TURN_ON = registerSound("lightturn_on");
    public static final RegistryObject<SoundEvent> LIGHT_TURN_OFF = registerSound("lightturn_off");
    public static final RegistryObject<SoundEvent> ELECTRIC_SHOCK = registerSound("electric_shock");
    public static final RegistryObject<SoundEvent> METEOR_COLLIDE = registerSound("meteor_collide");
    public static final RegistryObject<SoundEvent> SPANNER = registerSound("spanner");

    private StationSounds() {
    }

    private static RegistryObject<SoundEvent> registerSound(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(StationAreNear.MODID, name)));
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }
}