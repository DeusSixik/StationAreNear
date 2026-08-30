package dev.sixik.stationarenear.quest.runtime;

import dev.sixik.stationarenear.quest.event.QuestStartedEvent;
import dev.sixik.stationarenear.sam.SamTextSanitizer;
import dev.sixik.stationarenear.sam.SamVoice;
import dev.sixik.stationarenear.sam.network.SamNetwork;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchor;
import dev.sixik.stationarenear.ship.docking.ShipDockingAnchorSavedData;
import dev.sixik.stationarenear.structures.data.StationPieceDefinition;
import dev.sixik.stationarenear.structures.data.StationTriggerZone;
import dev.sixik.stationarenear.structures.generation.StationPlacementUtil;
import dev.sixik.stationarenear.structures.util.StationStructureIds;
import dev.sixik.stationarenear.structures.util.TagsConstants;
import dev.sixik.stationarenear.structures.world.StationStructureLibraryData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QuestAnnouncementHandler {

    public static final String SAM_SPEAK_TRIGGER_ID = "sam_speak";

    private static final ResourceLocation SHIP_POOL = StationStructureIds.pool("space_ship");

    private QuestAnnouncementHandler() {
    }

    public static void onQuestStarted(QuestStartedEvent event) {
        ServerLevel level = event.getLevel();
        dev.sixik.stationarenear.quest.world.QuestSavedData questData = dev.sixik.stationarenear.quest.world.QuestSavedData.get(level);
        dev.sixik.stationarenear.quest.data.QuestStationState station = questData.stationIfPresent(event.getStationId()).orElse(null);
        String stationCode = station != null && !station.displayStationCode().isBlank()
                ? station.displayStationCode()
                : dev.sixik.stationarenear.navigation.StationCodeGenerator.code(event.getStationId());

        long durationMillis = event.getDurationMillis();
        long totalSeconds = Math.max(0L, (durationMillis + 999L) / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        String timeStr = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes, seconds);

        Map<String, String> placeholders = Map.of(
                "station", stationCode,
                "time", timeStr,
                "reward", String.format(java.util.Locale.ROOT, "%.0f", event.getMoneyReward())
        );

        dev.sixik.stationarenear.quest.config.QuestPhraseManager.PhraseEntry phrase = dev.sixik.stationarenear.quest.config.QuestPhraseManager.getQuestStartPhrase(level.getRandom());
        String chatText = dev.sixik.stationarenear.quest.config.QuestPhraseManager.format(phrase.text(), placeholders);
        String samRawText = dev.sixik.stationarenear.quest.config.QuestPhraseManager.format(phrase.sam(), placeholders);
        if (samRawText.isBlank()) {
            samRawText = event.getAnnouncementText();
        }

        if (!chatText.isBlank()) {
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(chatText));
            }
        }

        speak(level, event.getStationId(), samRawText);
    }

    public static void speak(ServerLevel level, java.util.UUID contextId, String rawText) {
        String text = SamTextSanitizer.normalizeForNetwork(rawText);
        if (text.isBlank()) {
            return;
        }

        List<Vec3> positions = new ArrayList<>(shipSamSpeakerPositions(level));
        if (positions.isEmpty()) {
            for (dev.sixik.stationarenear.ship.block.entity.ShipTelevisionBlockEntity television : dev.sixik.stationarenear.ship.runtime.ShipTelevisionManager.shipTelevisions(level)) {
                positions.add(Vec3.atCenterOf(television.getBlockPos()));
            }
        }
        if (positions.isEmpty()) {
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                positions.add(player.position());
            }
        }

        java.util.UUID id = contextId == null ? java.util.UUID.randomUUID() : contextId;
        for (Vec3 position : positions) {
            long seed = level.getSeed()
                    ^ id.getMostSignificantBits()
                    ^ id.getLeastSignificantBits()
                    ^ SAM_SPEAK_TRIGGER_ID.hashCode()
                    ^ Double.doubleToLongBits(position.x())
                    ^ Long.rotateLeft(Double.doubleToLongBits(position.y()), 17)
                    ^ Long.rotateLeft(Double.doubleToLongBits(position.z()), 31);
            SamNetwork.play(level, position, text, SamVoice.random(seed));
        }
    }

    private static List<Vec3> shipSamSpeakerPositions(ServerLevel level) {
        StationStructureLibraryData library = StationStructureLibraryData.get(level);
        List<Vec3> positions = new ArrayList<>();
        Set<String> addedPositions = new HashSet<>();

        for (ShipDockingAnchor anchor : ShipDockingAnchorSavedData.get(level).anchors()) {
            addShipSamSpeakerPositions(library, anchor.shipBounds(), positions, addedPositions);
        }

        if (positions.isEmpty()) {
            for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
                for (StationPieceDefinition piece : library.pieces()) {
                    if (isShipPieceSelection(piece, entry.getKey())) {
                        addSamSpeakerPositions(piece, entry.getValue(), positions, addedPositions);
                    }
                }
            }
        }

        return positions;
    }

    private static void addShipSamSpeakerPositions(StationStructureLibraryData library, BoundingBox shipBounds, List<Vec3> positions, Set<String> addedPositions) {
        for (Map.Entry<ResourceLocation, BoundingBox> entry : library.savedTemplateSelections().entrySet()) {
            if (!sameBounds(shipBounds, entry.getValue())) {
                continue;
            }
            for (StationPieceDefinition piece : library.pieces()) {
                if (isShipPieceSelection(piece, entry.getKey())) {
                    addSamSpeakerPositions(piece, shipBounds, positions, addedPositions);
                }
            }
        }
    }

    private static void addSamSpeakerPositions(StationPieceDefinition piece, BoundingBox shipBounds, List<Vec3> positions, Set<String> addedPositions) {
        BlockPos origin = new BlockPos(shipBounds.minX(), shipBounds.minY(), shipBounds.minZ()).subtract(piece.selectionMin());
        for (StationTriggerZone triggerZone : piece.triggerZones()) {
            if (!isSamSpeakTrigger(triggerZone)) {
                continue;
            }

            BoundingBox triggerBounds = StationPlacementUtil.transformBox(origin, triggerZone.min(), triggerZone.max(), Rotation.NONE);
            Vec3 position = center(triggerBounds);
            if (addedPositions.add(positionKey(position))) {
                positions.add(position);
            }
        }
    }

    private static boolean isShipPieceSelection(StationPieceDefinition piece, ResourceLocation selectionId) {
        return piece.pool().equals(SHIP_POOL) && (piece.template().equals(selectionId) || piece.id().equals(selectionId));
    }

    private static boolean isSamSpeakTrigger(StationTriggerZone triggerZone) {
        if (!SAM_SPEAK_TRIGGER_ID.equals(triggerZone.id())) {
            return false;
        }
        String type = triggerZone.type() == null ? "" : triggerZone.type().trim();
        return type.isBlank() || type.equalsIgnoreCase(TagsConstants.Trigger.TRIGGER) || type.equalsIgnoreCase(TagsConstants.Trigger.OTHER);
    }

    private static Vec3 center(BoundingBox bounds) {
        return new Vec3(
                (bounds.minX() + bounds.maxX() + 1.0D) * 0.5D,
                (bounds.minY() + bounds.maxY() + 1.0D) * 0.5D,
                (bounds.minZ() + bounds.maxZ() + 1.0D) * 0.5D
        );
    }

    private static String positionKey(Vec3 position) {
        return position.x() + ":" + position.y() + ":" + position.z();
    }

    private static boolean sameBounds(BoundingBox left, BoundingBox right) {
        return left.minX() == right.minX()
                && left.minY() == right.minY()
                && left.minZ() == right.minZ()
                && left.maxX() == right.maxX()
                && left.maxY() == right.maxY()
                && left.maxZ() == right.maxZ();
    }
}
