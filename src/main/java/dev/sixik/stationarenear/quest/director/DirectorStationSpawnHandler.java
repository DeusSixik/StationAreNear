package dev.sixik.stationarenear.quest.director;

import dev.sixik.stationarenear.quest.config.director.DirectorConfigManager;
import dev.sixik.stationarenear.quest.config.director.StationOfferType;
import dev.sixik.stationarenear.structures.data.PlacedTriggerZone;
import dev.sixik.stationarenear.structures.data.StationInstance;
import dev.sixik.stationarenear.structures.trigger.StationStructureSpawnTriggerEvent;
import dev.sixik.stationarenear.structures.trigger.StationStructureTriggerType;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class DirectorStationSpawnHandler {

    private DirectorStationSpawnHandler() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, DirectorStationSpawnHandler::onStructureSpawnTrigger);
    }

    public static boolean hasDirectorPlan(StationInstance station) {
        return station != null && station.customData().contains(DirectorConfigManager.DIRECTOR_PLAN_KEY, Tag.TAG_COMPOUND);
    }

    private static void onStructureSpawnTrigger(StationStructureSpawnTriggerEvent event) {
        CompoundTag plan = directorPlan(event.getStation());
        if (plan.isEmpty()) {
            return;
        }
        if (event.getTriggerType() == StationStructureTriggerType.MOB_SPAWN) {
            applyMobOffer(event, plan);
        } else if (event.getTriggerType() == StationStructureTriggerType.DOOR_TRIGGER) {
            applyDoorOffer(event, plan);
        } else if (event.getTriggerType() == StationStructureTriggerType.OBJECT_ZONE_PLACER) {
            applyObjectOffer(event, plan);
        } else if (event.getTriggerType() == StationStructureTriggerType.QUEST_PLACE || event.getTriggerType() == StationStructureTriggerType.QUEST_OBJECT_PLACER || event.getTriggerType() == StationStructureTriggerType.OBJECT_PLACER) {
            applyEnergyFailureOffer(event, plan);
            applyGravitationFailureOffer(event, plan);
            applyOxygenFailureOffer(event, plan);
        }
    }

    private static void applyMobOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.MOB, event.getZone());
        if (offer.isEmpty()) {
            return;
        }
        int count = consume(offer, Math.min(3, offer.getInt("remaining")));
        if (count <= 0) {
            return;
        }
        event.setForcedMobCount(count);
        event.setForcedMob(offer.getString("entity"));
    }

    private static void applyDoorOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.BROKEN_DOOR, event.getZone());
        if (offer.isEmpty()) {
            event.getZone().data().putBoolean("broken", false);
            return;
        }
        consume(offer, 1);
        event.getZone().data().putBoolean("place", true);
        event.getZone().data().putInt("chance", 100);
        event.getZone().data().putBoolean("broken", true);
        event.getZone().data().putInt("openChance", 0);
    }

    private static void applyObjectOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.OBJECT, event.getZone());
        if (offer.isEmpty()) {
            return;
        }
        int count = consume(offer, offer.getInt("remaining"));
        if (count <= 0) {
            return;
        }
        event.setForcePlaceObjectZone(true);
        event.setForcedObjectZoneCount(count);
    }

    private static void applyEnergyFailureOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.ENERGY_FAILURE, event.getZone());
        if (offer.isEmpty()) {
            return;
        }
        consume(offer, 1);
        event.getZone().data().putBoolean("energyPanel", true);
        event.getZone().data().putInt("energyPanelChance", 100);
        event.getZone().data().putBoolean("broken", true);
    }

    private static void applyGravitationFailureOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.GRAVITATION_FAILURE, event.getZone());
        if (offer.isEmpty()) {
            return;
        }
        consume(offer, 1);
        event.getZone().data().putBoolean("placeGravitationPanel", true);
        event.getZone().data().putBoolean("broken", true);
        event.getZone().data().putBoolean("brokenGravitation", true);
    }

    private static void applyOxygenFailureOffer(StationStructureSpawnTriggerEvent event, CompoundTag plan) {
        CompoundTag offer = takeOffer(plan, StationOfferType.OXYGEN_FAILURE, event.getZone());
        if (offer.isEmpty()) {
            return;
        }
        consume(offer, 1);
        event.getZone().data().putBoolean("placeOxygenPanel", true);
        event.getZone().data().putBoolean("broken", true);
        event.getZone().data().putBoolean("brokenOxygen", true);
    }

    private static CompoundTag takeOffer(CompoundTag plan, StationOfferType type, PlacedTriggerZone zone) {
        ListTag offers = plan.getList("stationOffers", Tag.TAG_COMPOUND);
        for (int i = 0; i < offers.size(); i++) {
            CompoundTag offer = offers.getCompound(i);
            if (offer.getInt("remaining") <= 0 || StationOfferType.from(offer.getString("type")) != type || !matchesTags(offer, zone)) {
                continue;
            }
            return offer;
        }
        return new CompoundTag();
    }

    private static int consume(CompoundTag offer, int maxCount) {
        int remaining = Math.max(0, offer.getInt("remaining"));
        int count = Math.max(0, Math.min(maxCount, remaining));
        offer.putInt("remaining", remaining - count);
        return count;
    }

    private static boolean matchesTags(CompoundTag offer, PlacedTriggerZone zone) {
        ListTag targetTags = offer.getList("targetTags", Tag.TAG_STRING);
        if (targetTags.isEmpty()) {
            return true;
        }
        Set<String> zoneTags = zoneTags(zone);
        for (int i = 0; i < targetTags.size(); i++) {
            if (zoneTags.contains(targetTags.getString(i).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> zoneTags(PlacedTriggerZone zone) {
        Set<String> tags = new HashSet<>();
        tags.add(zone.type().toLowerCase(Locale.ROOT));
        addTags(tags, zone.data().getString(TagsConstants.Keys.TAG));
        addTags(tags, zone.data().getString(TagsConstants.Keys.TAGS));
        return tags;
    }

    private static void addTags(Set<String> tags, String value) {
        if (value == null) {
            return;
        }
        for (String part : value.split("[,;]")) {
            if (!part.isBlank()) {
                tags.add(part.trim().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static CompoundTag directorPlan(StationInstance station) {
        if (!hasDirectorPlan(station)) {
            return new CompoundTag();
        }
        return station.customData().getCompound(DirectorConfigManager.DIRECTOR_PLAN_KEY);
    }
}
