package dev.sixik.stationarenear.ship.block.entity;

import dev.sixik.stationarenear.quest.runtime.QuestObjectiveFormatter;
import dev.sixik.stationarenear.ship.registry.ShipBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ShipTelevisionBlockEntity extends BlockEntity {

    private static final String LEGACY_TEXT_KEY = "Text";
    private static final String MANUAL_TEXT_KEY = "ManualText";
    private static final String QUEST_TEXT_KEY = "QuestText";
    private static final String MANUAL_TEXT_POSITION_KEY = "ManualTextPosition";
    private static final String MANUAL_TEXT_SCALE_KEY = "ManualTextScale";
    private static final String MANUAL_CONTENT_MODE_KEY = "ManualContentMode";
    private static final String CONTROLLER_TERMINAL_POS_KEY = "ControllerTerminalPos";

    private String manualText = "";
    private String questText = "";
    private TelevisionTextPosition manualTextPosition = TelevisionTextPosition.CENTER;
    private TelevisionContentMode manualContentMode = TelevisionContentMode.TEXT;
    private BlockPos controllerTerminalPos = BlockPos.ZERO;
    private float manualTextScale = 1.0F;
    private float lastScanShipX = Float.NaN;
    private float lastScanShipY = Float.NaN;

    public ShipTelevisionBlockEntity(BlockPos pos, BlockState blockState) {
        super(ShipBlocks.SHIP_TELEVISION_ENTITY.get(), pos, blockState);
    }

    public String text() {
        if (!questText.isBlank()) {
            return questText;
        }
        if (!manualText.isBlank()) {
            return manualText;
        }
        return QuestObjectiveFormatter.IDLE_TEXT;
    }

    public void text(String text) {
        manualText(text);
    }

    public void manualText(String text) {
        manualText = clean(text);
        manualContentMode = TelevisionContentMode.TEXT;
        sync();
    }

    public TelevisionTextPosition textPosition() {
        return questText.isBlank() ? manualTextPosition : TelevisionTextPosition.CENTER;
    }

    public void manualTextPosition(TelevisionTextPosition position) {
        manualTextPosition = position == null ? TelevisionTextPosition.CENTER : position;
        sync();
    }

    public float textScale() {
        return questText.isBlank() ? manualTextScale : 1.0F;
    }

    public void manualTextScale(float scale) {
        manualTextScale = clampScale(scale);
        sync();
    }

    public float manualTextScale() {
        return manualTextScale;
    }

    public void questText(String text) {
        questText = clean(text);
        sync();
    }

    public TelevisionContentMode manualContentMode() {
        return manualContentMode;
    }

    public BlockPos controllerTerminalPos() {
        return controllerTerminalPos;
    }

    public void dynamicManualText(TelevisionContentMode mode, BlockPos terminalPos, String text) {
        manualContentMode = mode == null ? TelevisionContentMode.TEXT : mode;
        controllerTerminalPos = terminalPos == null ? BlockPos.ZERO : terminalPos;
        manualText = clean(text);
        lastScanShipX = Float.NaN;
        lastScanShipY = Float.NaN;
        sync();
    }

    public void updateDynamicManualText(String text) {
        manualText = clean(text);
        sync();
    }

    public boolean scanShipPositionChanged(float shipX, float shipY) {
        if (!Float.isFinite(lastScanShipX) || !Float.isFinite(lastScanShipY)) {
            lastScanShipX = shipX;
            lastScanShipY = shipY;
            return true;
        }
        float deltaX = Math.abs(lastScanShipX - shipX);
        float deltaY = Math.abs(lastScanShipY - shipY);
        if (deltaX <= 0.05F && deltaY <= 0.05F) {
            return false;
        }
        lastScanShipX = shipX;
        lastScanShipY = shipY;
        return true;
    }

    public boolean questTextEquals(String text) {
        return questText.equals(clean(text));
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(MANUAL_TEXT_KEY, manualText);
        tag.putString(QUEST_TEXT_KEY, questText);
        tag.putString(MANUAL_TEXT_POSITION_KEY, manualTextPosition.name());
        tag.putFloat(MANUAL_TEXT_SCALE_KEY, manualTextScale);
        tag.putString(MANUAL_CONTENT_MODE_KEY, manualContentMode.name());
        tag.putLong(CONTROLLER_TERMINAL_POS_KEY, controllerTerminalPos.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        manualText = tag.contains(MANUAL_TEXT_KEY) ? tag.getString(MANUAL_TEXT_KEY) : tag.getString(LEGACY_TEXT_KEY);
        questText = tag.getString(QUEST_TEXT_KEY);
        manualTextPosition = TelevisionTextPosition.fromName(tag.getString(MANUAL_TEXT_POSITION_KEY));
        manualTextScale = tag.contains(MANUAL_TEXT_SCALE_KEY) ? clampScale(tag.getFloat(MANUAL_TEXT_SCALE_KEY)) : 1.0F;
        manualContentMode = TelevisionContentMode.fromName(tag.getString(MANUAL_CONTENT_MODE_KEY));
        controllerTerminalPos = tag.contains(CONTROLLER_TERMINAL_POS_KEY) ? BlockPos.of(tag.getLong(CONTROLLER_TERMINAL_POS_KEY)) : BlockPos.ZERO;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static String clean(String text) {
        return text == null ? "" : text;
    }

    private static float clampScale(float scale) {
        if (!Float.isFinite(scale)) {
            return 1.0F;
        }
        return Math.max(0.35F, Math.min(3.0F, scale));
    }

    public enum TelevisionContentMode {
        TEXT,
        SHIP_STATUS,
        SHIP_SCAN;

        public static TelevisionContentMode fromName(String name) {
            if (name == null || name.isBlank()) {
                return TEXT;
            }
            for (TelevisionContentMode mode : values()) {
                if (mode.name().equalsIgnoreCase(name.trim())) {
                    return mode;
                }
            }
            return TEXT;
        }
    }

    public enum TelevisionTextPosition {
        TOP,
        CENTER,
        DOWN;

        public static TelevisionTextPosition fromName(String name) {
            if (name == null || name.isBlank()) {
                return CENTER;
            }
            for (TelevisionTextPosition position : values()) {
                if (position.name().equalsIgnoreCase(name.trim())) {
                    return position;
                }
            }
            return CENTER;
        }
    }
}
