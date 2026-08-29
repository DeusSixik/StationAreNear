package dev.sixik.stationarenear.terminal.client;

import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.UiRenderPolicy;
import dev.sixik.unigui.api.text.TextOverflowMode;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftClipboardService;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftWidgetScreen;
import dev.sixik.unigui.impl.core.DefaultUIContext;
import dev.sixik.stationarenear.terminal.map.data.StationMapConnection;
import dev.sixik.stationarenear.terminal.map.model.StationMapData;
import dev.sixik.stationarenear.terminal.map.data.StationMapPiece;
import dev.sixik.stationarenear.terminal.map.data.StationMapSnapshot;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.unigui.impl.style.DefaultTheme;
import dev.sixik.unigui.widgets.containers.Box;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Label;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.map.MapCanvas;
import dev.sixik.unigui.widgets.world.WorldCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StationMapScreen {
    private static final double BLOCK_UI_MAX_DISTANCE_SQ = 16.0D;

    private StationMapScreen() {
    }

    public static void open() {
        open(OpenParams.snapshot(sampleSnapshot()).markers(sampleMarkers()));
    }

    public static void open(StationMapData data) {
        open(OpenParams.data(data));
    }

    public static void open(StationMapData data, StationMapMarkers markers) {
        open(OpenParams.data(data).markers(markers));
    }

    public static void open(StationMapSnapshot snapshot) {
        open(OpenParams.snapshot(snapshot));
    }

    public static void open(StationMapSnapshot snapshot, StationMapMarkers markers) {
        open(OpenParams.snapshot(snapshot).markers(markers));
    }

    public static void open(OpenParams params) {
        OpenParams resolved = params == null ? OpenParams.snapshot(sampleSnapshot()) : params;
        StationMapSnapshot snapshot = resolved.snapshotOrSample();
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;

        DefaultUIContext context = new DefaultUIContext(new MinecraftClipboardService());
        UnityLikeUIScaleProvider scale = new UnityLikeUIScaleProvider()
                .referenceResolution(1920.0f, 1080.0f)
                .matchBalanced()
                .scaleRange(0.75f, 6.0f)
                .userScale(resolved.userScale());
        context.scaleProvider(scale);

        StationMapMarkers markers = resolved.markers();
        StationLevelMap map = new StationLevelMap(snapshot, markers, resolved.initialFloor());
        LevelSidePanel sidePanel = new LevelSidePanel(map);
        Widget root = root(map, sidePanel);

        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal(resolved.title()), root, context) {
            @Override
            public void tick() {
                super.tick();
                if (shouldCloseBecauseTooFar(snapshot.terminalPos())) {
                    onClose();
                }
            }

            @Override
            public void onClose() {
                if (resolved.returnToTerminal()) {
                    Minecraft.getInstance().setScreen(null);
                    TerminalNetwork.requestOpenTerminal(snapshot.terminalPos());
                    return;
                }
                Minecraft.getInstance().setScreen(previous);
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        };
        screen.useContextScale().scaleWithMinecraftGui(false);
        screen.useSdfDefaultFont();
        screen.renderPolicy(UiRenderPolicy.continuous());
        screen.postEffect(RetroTerminalScreen.retroEffect(scale));
        minecraft.setScreen(screen);
    }

    private static boolean shouldCloseBecauseTooFar(BlockPos terminalPos) {
        if (terminalPos.equals(BlockPos.ZERO)) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return true;
        }

        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(terminalPos)) <= BLOCK_UI_MAX_DISTANCE_SQ) {
            return false;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    mutable.set(playerPos.getX() + dx, playerPos.getY() + dy, playerPos.getZ() + dz);
                    net.minecraft.world.level.block.state.BlockState state = minecraft.level.getBlockState(mutable);
                    if (state.is(dev.sixik.stationarenear.quest.registry.QuestBlocks.CONSOLE_NO_ANGLE.get())
                            || state.is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())
                            || state.is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.STATION_MAP_TERMINAL.get())) {
                        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(mutable)) <= BLOCK_UI_MAX_DISTANCE_SQ) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    public static final class OpenParams {
        private StationMapSnapshot snapshot;
        private StationMapMarkers markers = new StationMapMarkers();
        private int initialFloor;
        private float userScale = 2.5f;
        private String title = "Station Map";
        private boolean returnToTerminal;

        private OpenParams(StationMapSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        public static OpenParams snapshot(StationMapSnapshot snapshot) {
            return new OpenParams(snapshot);
        }

        public static OpenParams data(StationMapData data) {
            return new OpenParams(snapshotFrom(data));
        }


        public OpenParams markers(StationMapMarkers markers) {
            this.markers = markers == null ? new StationMapMarkers() : markers;
            return this;
        }

        public OpenParams mark(String templateId, String markerId, MutableColor color, String title, String description) {
            markers.mark(templateId, markerId, color, title, description);
            return this;
        }

        public OpenParams markAll(String markerId, MutableColor color, String title, String description, String... templateIds) {
            markers.markAll(markerId, color, title, description, templateIds);
            return this;
        }

        public OpenParams initialFloor(int initialFloor) {
            this.initialFloor = initialFloor;
            return this;
        }

        public OpenParams userScale(float userScale) {
            this.userScale = Math.max(0.1f, userScale);
            return this;
        }

        public OpenParams title(String title) {
            this.title = title == null || title.isBlank() ? "Station Map" : title;
            return this;
        }

        public OpenParams returnToTerminal(boolean returnToTerminal) {
            this.returnToTerminal = returnToTerminal;
            return this;
        }

        private StationMapSnapshot snapshotOrSample() {
            return snapshot == null ? sampleSnapshot() : snapshot;
        }

        private StationMapMarkers markers() {
            return markers == null ? new StationMapMarkers() : markers;
        }

        private int initialFloor() {
            return initialFloor;
        }

        private float userScale() {
            return userScale;
        }

        private String title() {
            return title;
        }

        private boolean returnToTerminal() {
            return returnToTerminal;
        }
    }

    private static Widget root(StationLevelMap map, LevelSidePanel sidePanel) {
        Box root = panel(0.011f, 0.014f, 0.020f, 1.0f, 0.10f, 0.15f, 0.22f, 0.0f);
        root.borderVisible(false);
        root.layout(style -> style.sizePercent(100.0f, 100.0f)
                .padding(28.0f)
                .overflow(Overflow.HIDDEN));

        VBox shell = new VBox();
        shell.spacing(14.0f);
        shell.layout(style -> style.sizePercent(100.0f, 100.0f).flexGrow(1.0f).flexShrink(1.0f));

        Label title = label("STATION MAP / " + map.stationCode(), 18.0f, COLOR_TITLE);
        title.layout(style -> style.height(28.0f).flexShrink(0.0f));
        shell.addChild(title);

        HBox body = new HBox();
        body.spacing(14.0f);
        body.layout(style -> style.sizePercent(100.0f, 100.0f).flexGrow(1.0f).flexShrink(1.0f));

        Box mapPanel = panel(0.020f, 0.027f, 0.041f, 0.96f, 0.20f, 0.32f, 0.52f, 0.70f);
        mapPanel.radius(8.0f).borderWidth(1.0f);
        mapPanel.layout(style -> style.sizePercent(100.0f, 100.0f)
                .padding(12.0f)
                .flexGrow(1.0f)
                .flexShrink(1.0f));
        map.layout(style -> style.sizePercent(100.0f, 100.0f).flexGrow(1.0f).flexShrink(1.0f));
        mapPanel.addChild(map);

        sidePanel.layout(style -> style.width(330.0f)
                .heightPercent(100.0f)
                .flexGrow(0.0f)
                .flexShrink(0.0f));

        body.addChild(mapPanel);
        body.addChild(sidePanel);
        shell.addChild(body);
        root.addChild(shell);
        return new OverlayLayer(root);
    }

    private static Box panel(float r, float g, float b, float a, float br, float bg, float bb, float ba) {
        Box box = new Box();
        box.themeEnabled(false);
        box.backgroundVisible(true);
        box.borderVisible(true);
        box.background().set(r, g, b, a);
        box.borderColor().set(br, bg, bb, ba);
        return box;
    }

    private static Label label(String text, float size, MutableColor color) {
        Label label = new Label(text);
        label.font(MinecraftFonts.defaultFace(), size);
        label.color(color);
        label.noWrap();
        label.overflowMode(TextOverflowMode.CLIP);
        return label;
    }

    private static String pieceKey(StationMapPiece piece) {
        return piece.definitionId() + "|" + piece.minFloor() + ':' + piece.maxFloor()
                + '|' + piece.minX() + ':' + piece.minZ()
                + '|' + piece.maxX() + ':' + piece.maxZ();
    }



    private static final MutableColor COLOR_TITLE = MutableColor.rgba(0.74f, 0.90f, 1.0f, 1.0f);
    private static final MutableColor COLOR_TEXT = MutableColor.rgba(0.72f, 0.78f, 0.86f, 1.0f);
    private static final MutableColor COLOR_MUTED = MutableColor.rgba(0.42f, 0.50f, 0.62f, 1.0f);
    private static final MutableColor COLOR_ACCENT = MutableColor.rgba(0.38f, 0.78f, 1.0f, 1.0f);
    private static final MutableColor COLOR_SHIP = MutableColor.rgba(0.18f, 0.34f, 0.48f, 0.82f);
    private static final MutableColor COLOR_SHIP_BORDER = MutableColor.rgba(0.52f, 0.86f, 1.0f, 1.0f);
    private static final MutableColor COLOR_ROOM = MutableColor.rgba(0.08f, 0.11f, 0.16f, 0.90f);
    private static final MutableColor COLOR_ROOM_BORDER = MutableColor.rgba(0.30f, 0.44f, 0.64f, 0.86f);
    private static final MutableColor COLOR_COMBAT = MutableColor.rgba(0.20f, 0.10f, 0.09f, 0.92f);
    private static final MutableColor COLOR_COMBAT_BORDER = MutableColor.rgba(0.92f, 0.34f, 0.24f, 0.92f);
    private static final MutableColor COLOR_TREASURE = MutableColor.rgba(0.22f, 0.17f, 0.07f, 0.92f);
    private static final MutableColor COLOR_TREASURE_BORDER = MutableColor.rgba(1.0f, 0.72f, 0.25f, 0.96f);
    private static final MutableColor COLOR_DEMO_QUEST = MutableColor.rgba(0.30f, 0.20f, 0.04f, 0.96f);
    private static final MutableColor COLOR_DEMO_QUEST_BORDER = MutableColor.rgba(1.0f, 0.82f, 0.16f, 1.0f);
    private static final MutableColor COLOR_ELEVATOR = MutableColor.rgba(0.11f, 0.18f, 0.18f, 0.95f);
    private static final MutableColor COLOR_ELEVATOR_BORDER = MutableColor.rgba(0.42f, 1.0f, 0.82f, 0.92f);
    private static final MutableColor COLOR_CONNECTION = MutableColor.rgba(0.44f, 0.82f, 1.0f, 0.88f);
    private static final MutableColor COLOR_CONNECTION_BORDER = MutableColor.rgba(0.76f, 0.95f, 1.0f, 0.92f);
    private static final MutableColor COLOR_VERTICAL_LINK = MutableColor.rgba(0.50f, 0.95f, 0.82f, 0.92f);
    public record RoomMarker(String id, String title, String description, MutableColor fill, MutableColor border) {
        public RoomMarker {
            id = id == null || id.isBlank() ? "room" : id;
            title = title == null || title.isBlank() ? id.toUpperCase() : title;
            description = description == null ? "" : description;
            fill = fill == null ? COLOR_ROOM.copy() : fill.copy();
            border = border == null ? COLOR_ROOM_BORDER.copy() : border.copy();
        }
    }

    public static final class StationMapMarkers {
        private final Map<String, RoomMarker> markers = new LinkedHashMap<>();
        private final Map<String, String> templateBindings = new LinkedHashMap<>();
        private final Map<String, String> pieceBindings = new LinkedHashMap<>();

        public StationMapMarkers() {
            define(new RoomMarker("ship", "SHIP", "Текущая позиция", COLOR_SHIP, COLOR_SHIP_BORDER));
            define(new RoomMarker("room", "ROOM", "Обычный сегмент станции", COLOR_ROOM, COLOR_ROOM_BORDER));
            define(new RoomMarker("quest", "ЗАДАНИЕ", "Зона активного задания", COLOR_TREASURE, COLOR_TREASURE_BORDER));
            define(new RoomMarker("lift", "LIFT", "Переход между этажами", COLOR_ELEVATOR, COLOR_ELEVATOR_BORDER));
        }

        public StationMapMarkers define(String markerId, MutableColor color, String title, String description) {
            return define(new RoomMarker(markerId, title, description, dimFill(color), color));
        }

        public StationMapMarkers define(String markerId, MutableColor fill, MutableColor border, String title, String description) {
            return define(new RoomMarker(markerId, title, description, fill, border));
        }

        public StationMapMarkers define(RoomMarker marker) {
            if (marker != null) {
                markers.put(marker.id(), marker);
            }
            return this;
        }

        public StationMapMarkers bind(String templateId, String markerId) {
            ResourceLocation location = resourceLocation(templateId, "unigui_testmod", "room");
            return bind(location, markerId);
        }

        public StationMapMarkers bind(ResourceLocation templateId, String markerId) {
            if (templateId == null || markerId == null || markerId.isBlank()) return this;
            templateBindings.put(templateId.toString(), markerId);
            templateBindings.put(templateId.getPath(), markerId);
            return this;
        }

        public StationMapMarkers bindAll(String markerId, String... templateIds) {
            if (templateIds == null) return this;
            for (String templateId : templateIds) {
                bind(templateId, markerId);
            }
            return this;
        }

        public StationMapMarkers bindPiece(StationMapPiece piece, String markerId) {
            if (piece == null || markerId == null || markerId.isBlank()) return this;
            pieceBindings.put(pieceKey(piece), markerId);
            return this;
        }

        public StationMapMarkers clearGeneratedQuestBindings() {
            pieceBindings.entrySet().removeIf(entry -> "quest".equals(entry.getValue()));
            return this;
        }

        public StationMapMarkers mark(String templateId, String markerId, MutableColor color, String title, String description) {
            return define(markerId, color, title, description).bind(templateId, markerId);
        }

        public StationMapMarkers mark(ResourceLocation templateId, String markerId, MutableColor color, String title, String description) {
            return define(markerId, color, title, description).bind(templateId, markerId);
        }

        public StationMapMarkers markAll(String markerId, MutableColor color, String title, String description, String... templateIds) {
            define(markerId, color, title, description);
            return bindAll(markerId, templateIds);
        }
        public RoomMarker markerFor(StationMapPiece piece) {
            if (piece == null) return marker("room");
            String markerId = pieceBindings.get(pieceKey(piece));
            if (markerId == null && !piece.dockPiece() && !piece.markerId().isBlank()) markerId = piece.markerId();
            if (markerId == null) markerId = piece.dockPiece() ? "ship" : templateBindings.get(piece.definitionId().toString());
            if (markerId == null) markerId = templateBindings.get(piece.definitionId().getPath());
            if (markerId == null) markerId = fallbackMarkerId(piece);
            return marker(markerId);
        }

        public RoomMarker marker(String markerId) {
            RoomMarker marker = markers.get(markerId);
            return marker != null ? marker : markers.get("room");
        }

        private String fallbackMarkerId(StationMapPiece piece) {
            return piece.minFloor() != piece.maxFloor() ? "lift" : "room";
        }
    }

    private static final class LevelSidePanel extends Box {
        private final StationLevelMap map;
        private final int[] floors;
        private final ComboBox floorDropBox;
        private final VBox legend;

        private LevelSidePanel(StationLevelMap map) {
            this.map = map;
            themeEnabled(false);
            backgroundVisible(true);
            borderVisible(true);
            radius(8.0f);
            borderWidth(1.0f);
            background().set(0.020f, 0.026f, 0.036f, 0.96f);
            borderColor().set(0.20f, 0.32f, 0.52f, 0.68f);
            layout(style -> style.padding(14.0f));

            floors = floors(map.minFloor(), map.maxFloor());
            floorDropBox = floorDropBox(floors, map.activeFloor());
            floorDropBox.onSelectionChanged(event -> {
                int index = floorDropBox.selectedIndex();
                if (index < 0 || index >= floors.length) return;
                map.activeFloor(floors[index]);
                refresh();
            });

            HBox floorSelector = new HBox();
            floorSelector.spacing(10.0f);
            floorSelector.layout(style -> style.height(30.0f).alignItems(Align.CENTER).flexGrow(0.0f).flexShrink(0.0f));
            Label floorLabel = label("Уровень карты", 11.5f, COLOR_MUTED);
            floorLabel.layout(style -> style.height(30.0f).flexGrow(1.0f).flexShrink(1.0f));
            floorDropBox.layout(style -> style.width(108.0f).height(30.0f).flexGrow(0.0f).flexShrink(0.0f));
            floorSelector.addChild(floorLabel);
            floorSelector.addChild(floorDropBox);

            legend = new VBox();
            legend.spacing(8.0f);
            legend.layout(style -> style.widthPercent(100.0f).flexGrow(0.0f).flexShrink(0.0f));

            ScrollView legendScroll = new ScrollView(legend);
            legendScroll.scrollStep(56.0f);
            legendScroll.scrollbarGap(2.0f);
            legendScroll.scrollbarTrackColor().set(0.0f, 0.0f, 0.0f, 0.34f);
            legendScroll.scrollbarThumbColor().set(0.44f, 0.82f, 1.0f, 0.82f);
            legendScroll.layout(style -> style
                    .sizePercent(100.0f, 100.0f)
                    .overflowX(Overflow.HIDDEN)
                    .overflowY(Overflow.AUTO)
                    .flexGrow(1.0f)
                    .flexShrink(1.0f));

            VBox content = new VBox();
            content.spacing(12.0f);
            content.layout(style -> style.sizePercent(100.0f, 100.0f)
                    .flexGrow(1.0f)
                    .flexShrink(1.0f));
            content.addChild(floorSelector);
            content.addChild(legendScroll);
            addChild(content);
            refresh();
        }

        private ComboBox floorDropBox(int[] floors, int selectedFloor) {
            List<String> labels = new ArrayList<>(floors.length);
            int selectedIndex = 0;
            for (int i = 0; i < floors.length; i++) {
                labels.add(floorLabel(floors[i]));
                if (floors[i] == selectedFloor) selectedIndex = i;
            }

            ComboBox combo = new ComboBox();
            combo.items(labels);
            combo.silentSelectedIndex(selectedIndex);
            combo.dropDownSameWidth();
            combo.useOverlay();
            combo.optionRowHeight(24.0f);
            combo.maxVisibleOptions(6);
            combo.layout(style -> style.width(108.0f).height(30.0f).flexGrow(0.0f).flexShrink(0.0f));
            combo.headerButton().themeEnabled(false);
            combo.headerButton().backgroundVisible(true);
            combo.headerButton().borderVisible(true);
            combo.headerButton().radius(4.0f);
            combo.headerButton().borderWidth(1.0f);
            combo.headerButton().background().set(0.055f, 0.070f, 0.098f, 0.96f);
            combo.headerButton().borderColor().set(0.25f, 0.40f, 0.62f, 0.75f);
            combo.headerButton().textColor().set(COLOR_TEXT);
            combo.headerButton().textPadding(10.0f, 4.0f);
            combo.optionsHost().themeEnabled(false);
            combo.optionsHost().radius(4.0f);
            combo.optionsHost().background().set(0.020f, 0.026f, 0.036f, 0.99f);
            combo.optionsHost().borderColor().set(0.25f, 0.52f, 0.78f, 0.86f);
            combo.optionsScroll().scrollbarThumbColor().set(0.44f, 0.82f, 1.0f, 0.82f);
            return combo;
        }

        private Box legendRow(RoomMarker marker) {
            Box row = panel(0.030f, 0.038f, 0.052f, 0.76f, 0.13f, 0.19f, 0.28f, 0.55f);
            row.radius(5.0f).borderWidth(1.0f);
            row.layout(style -> style.height(48.0f).padding(10.0f, 7.0f).flexShrink(0.0f));

            HBox content = new HBox();
            content.spacing(9.0f);
            content.layout(style -> style.sizePercent(100.0f, 100.0f).alignItems(Align.CENTER));

            MutableColor color = marker.border();
            Box swatch = panel(color.r(), color.g(), color.b(), 0.28f, color.r(), color.g(), color.b(), 1.0f);
            swatch.radius(3.0f).borderWidth(1.0f);
            swatch.layout(style -> style.size(30.0f, 30.0f).flexShrink(0.0f));

            VBox text = new VBox();
            text.spacing(1.0f);
            text.layout(style -> style.flexGrow(1.0f).flexShrink(1.0f));
            Label name = label(marker.title(), 12.0f, COLOR_TITLE);
            name.layout(style -> style.height(17.0f).flexShrink(0.0f));
            Label desc = label(marker.description(), 10.0f, COLOR_MUTED);
            desc.layout(style -> style.height(16.0f).flexShrink(0.0f));
            text.addChild(name);
            text.addChild(desc);

            content.addChild(swatch);
            content.addChild(text);
            row.addChild(content);
            return row;
        }

        private void refresh() {
            int floor = map.activeFloor();
            for (int i = 0; i < floors.length; i++) {
                if (floors[i] == floor && floorDropBox.selectedIndex() != i) {
                    floorDropBox.silentSelectedIndex(i);
                    break;
                }
            }

            legend.clearChildren();
            legend.applyQueuedMutations();
            for (RoomMarker marker : map.legendMarkers(floor)) {
                legend.addChild(legendRow(marker));
            }
            legend.applyQueuedMutations();
        }

        private static int[] floors(int minFloor, int maxFloor) {
            int count = Math.max(1, maxFloor - minFloor + 1);
            int[] result = new int[count];
            for (int i = 0; i < count; i++) {
                result[i] = minFloor + i;
            }
            return result;
        }
    }
    private static final class StationLevelMap extends MapCanvas {
        private static final float CELL = 18.0f;
        private static final float MAP_PADDING = 110.0f;

        private final StationMapSnapshot snapshot;
        private final StationMapMarkers markers;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;
        private boolean initialViewportCentered;
        private int activeFloor;

        private StationLevelMap(StationMapSnapshot snapshot, StationMapMarkers markers, int initialFloor) {
            this.snapshot = snapshot;
            this.markers = markers == null ? new StationMapMarkers() : markers;
            Bounds bounds = Bounds.of(snapshot.pieces());
            minX = bounds.minX();
            minZ = bounds.minZ();
            maxX = bounds.maxX();
            maxZ = bounds.maxZ();
            activeFloor = clampFloor(initialFloor);

            float width = Math.max(900.0f, (maxX - minX + 1) * CELL + MAP_PADDING * 2.0f);
            float height = Math.max(600.0f, (maxZ - minZ + 1) * CELL + MAP_PADDING * 2.0f);
            mapSize(width, height);
            viewport(32.0f, 28.0f, 0.72f);
            zoomRange(0.35f, 1.55f);
            gridSize(CELL * 4.0f);
            clampToMapBounds(true);
            panningEnabled(true);
            panButton(PointerButton.PRIMARY);
            wheelPanStep(42.0f);
            zoomStep(1.12f);
            backgroundColor().set(0.008f, 0.012f, 0.020f, 1.0f);
            mapColor().set(0.022f, 0.031f, 0.047f, 0.94f);
            borderColor().set(0.20f, 0.36f, 0.58f, 0.76f);
            gridColor().set(0.16f, 0.26f, 0.38f, 0.26f);
            addWorldLayer(this::renderStationLayer);
        }

        @Override
        public void arrange(RectView bounds) {
            super.arrange(bounds);
            if (!initialViewportCentered && layoutBounds().width() > 0.0f && layoutBounds().height() > 0.0f) {
                initialViewportCentered = true;
                centerViewportOnShip();
            }
        }

        private void centerViewportOnShip() {
            StationMapPiece target = null;
            for (StationMapPiece piece : snapshot.pieces()) {
                if (piece.dockPiece()) {
                    target = piece;
                    break;
                }
            }
            if (target == null && !snapshot.pieces().isEmpty()) {
                target = snapshot.pieces().get(0);
            }
            if (target == null) {
                return;
            }

            float shipMapX = MAP_PADDING + (pieceCenterX(target) - minX) * CELL;
            float shipMapY = MAP_PADDING + (pieceCenterZ(target) - minZ) * CELL;
            float zoom = viewport().zoom();
            float centeredX = layoutBounds().width() * 0.5f - shipMapX * zoom;
            float centeredY = layoutBounds().height() * 0.5f - shipMapY * zoom;
            viewport(centeredX, centeredY, zoom);
        }

        private String stationCode() {
            return snapshot.stationCode();
        }

        private int minFloor() {
            return snapshot.minFloor();
        }

        private int maxFloor() {
            return snapshot.maxFloor();
        }

        private int activeFloor() {
            return activeFloor;
        }

        private void activeFloor(int activeFloor) {
            int clamped = clampFloor(activeFloor);
            if (this.activeFloor == clamped) return;
            this.activeFloor = clamped;
            invalidate(InvalidationFlags.VISUAL | InvalidationFlags.LAYOUT);
        }

        private String floorDescription(int floor) {
            int visible = 0;
            int connections = 0;
            for (StationMapPiece piece : snapshot.pieces()) {
                if (!pieceVisibleOnFloor(piece, floor)) continue;
                visible++;
                for (StationMapConnection connection : piece.connections()) {
                    if (connection.floor() == floor) connections++;
                }
            }
            return "Станция " + snapshot.stationCode()
                    + ": " + visible + " сегментов, "
                    + connections + " точек стыковки. Dock Y=" + snapshot.dockY() + ".";
        }

        private List<RoomMarker> legendMarkers(int floor) {
            Map<String, RoomMarker> result = new LinkedHashMap<>();
            for (StationMapPiece piece : snapshot.pieces()) {
                if (!pieceVisibleOnFloor(piece, floor)) continue;
                RoomMarker marker = markers.markerFor(piece);
                result.putIfAbsent(marker.id(), marker);
            }
            return new ArrayList<>(result.values());
        }
        private void renderStationLayer(WorldCanvas ignored, DrawScope draw) {
            for (StationMapPiece piece : snapshot.pieces()) {
                if (!pieceVisibleOnFloor(piece, activeFloor)) continue;
                drawPiece(draw, piece);
            }
            for (StationMapPiece piece : snapshot.pieces()) {
                if (!pieceVisibleOnFloor(piece, activeFloor)) continue;
                drawConnections(draw, piece);
            }
            drawPanels(draw);
            drawPlayers(draw);
        }

        private void drawPlayers(DrawScope draw) {
            for (dev.sixik.stationarenear.terminal.map.data.StationMapPlayer player : snapshot.trackedPlayers()) {
                if (player.floor() != activeFloor) continue;
                float px = mapX((float) player.x());
                float py = mapY((float) player.z());
                float r = Math.max(5.0f, 7.0f * viewport().zoom());
                draw.addCircleFilled(px, py, r + 2.0f, MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.75f), 24);
                draw.addCircleFilled(px, py, r, MutableColor.rgba(0.20f, 0.95f, 0.35f, 0.95f), 24);
                draw.addCircle(px, py, r, MutableColor.rgba(1.0f, 1.0f, 1.0f, 0.90f), 24, 1.2f);
                draw.addText(player.name(), px - 35.0f, py - r - 13.0f, 70.0f, 12.0f, MutableColor.rgba(0.90f, 1.0f, 0.90f, 0.95f));
            }
        }

        private void drawPanels(DrawScope draw) {
            for (dev.sixik.stationarenear.terminal.map.data.StationMapPanel panel : snapshot.trackedPanels()) {
                if (panel.floor() != activeFloor) continue;
                float px = mapX(panel.x() + 0.5f);
                float py = mapY(panel.z() + 0.5f);
                float size = Math.max(8.0f, 10.0f * viewport().zoom());
                MutableColor fill = panel.broken() ? MutableColor.rgba(0.95f, 0.25f, 0.20f, 0.95f) : MutableColor.rgba(0.25f, 0.75f, 1.0f, 0.95f);
                draw.addRectFilled(px - size * 0.5f, py - size * 0.5f, size, size, 2.0f, fill);
                draw.addRect(px - size * 0.5f, py - size * 0.5f, size, size, 2.0f, MutableColor.rgba(1.0f, 1.0f, 1.0f, 0.85f), 1.0f);
                draw.addText(panel.broken() ? "!" : "+", px - 10.0f, py - 6.0f, 20.0f, 12.0f, MutableColor.rgba(1.0f, 1.0f, 1.0f, 0.95f));
            }
        }

        private void drawFloorBadge(DrawScope draw) {
            float x = layoutBounds().x() + 18.0f;
            float y = layoutBounds().y() + 16.0f;
            draw.addRectFilled(x, y, 182.0f, 34.0f, 5.0f, MutableColor.rgba(0.018f, 0.026f, 0.038f, 0.90f));
            draw.addRect(x, y, 182.0f, 34.0f, 5.0f, MutableColor.rgba(0.28f, 0.48f, 0.72f, 0.72f), 1.0f);
            draw.addText("FLOOR " + activeFloor + " / " + snapshot.stationCode(), x + 10.0f, y + 7.0f, 162.0f, 20.0f, COLOR_TITLE);
        }

        private void drawQuestHighlight(DrawScope draw, float x, float y, float w, float h, float radius) {
            float pulse = (float) ((Math.sin(System.nanoTime() / 1_000_000_000.0D * 3.2D) + 1.0D) * 0.5D);
            MutableColor glow = MutableColor.rgba(1.0f, 0.86f, 0.18f, 0.48f + pulse * 0.32f);
            MutableColor core = MutableColor.rgba(1.0f, 0.92f, 0.38f, 0.84f);
            float line = Math.max(1.8f, 2.2f * viewport().zoom());
            float corner = Math.min(Math.min(w, h) * 0.28f, Math.max(7.0f, 12.0f * viewport().zoom()));

            draw.addRect(x - 3.0f, y - 3.0f, w + 6.0f, h + 6.0f, radius, glow, line);
            draw.addRectFilled(x + 3.0f, y + 3.0f, corner, line, radius, core);
            draw.addRectFilled(x + 3.0f, y + 3.0f, line, corner, radius, core);
            draw.addRectFilled(x + w - 3.0f - corner, y + 3.0f, corner, line, radius, core);
            draw.addRectFilled(x + w - 3.0f - line, y + 3.0f, line, corner, radius, core);
            draw.addRectFilled(x + 3.0f, y + h - 3.0f - line, corner, line, radius, core);
            draw.addRectFilled(x + 3.0f, y + h - 3.0f - corner, line, corner, radius, core);
            draw.addRectFilled(x + w - 3.0f - corner, y + h - 3.0f - line, corner, line, radius, core);
            draw.addRectFilled(x + w - 3.0f - line, y + h - 3.0f - corner, line, corner, radius, core);
            if (w >= 26.0f && h >= 22.0f) {
                draw.addText("!", x, y + h * 0.5f - 10.0f, w, 18.0f, MutableColor.rgba(1.0f, 0.96f, 0.54f, 0.95f));
            }
        }

        private void drawConnections(DrawScope draw, StationMapPiece piece) {
            for (StationMapConnection connection : piece.connections()) {
                if (connection.floor() != activeFloor || connection.direction().getAxis().isVertical()) continue;
                drawConnectionMarker(draw, connection);
            }
      /*      if (verticalMarkerVisible(piece, activeFloor)) {
                drawVerticalConnection(draw, piece);
            }*/
        }

        private void drawConnectionMarker(DrawScope draw, StationMapConnection connection) {
            float centerX = mapX(connection.x() + 0.5f);
            float centerY = mapY(connection.z() + 0.5f);
            float longSide = Math.max(8.0f, CELL * 0.86f * viewport().zoom());
            float shortSide = Math.max(3.0f, CELL * 0.20f * viewport().zoom());
            boolean horizontal = connection.direction() == Direction.NORTH || connection.direction() == Direction.SOUTH;
            float w = horizontal ? longSide : shortSide;
            float h = horizontal ? shortSide : longSide;
            float x = centerX - w * 0.5f;
            float y = centerY - h * 0.5f;
            float radius = 0.0f;

            draw.addRectFilled(x, y, w, h, radius, COLOR_CONNECTION);
            draw.addRect(x, y, w, h, radius, COLOR_CONNECTION_BORDER, 1.0f);
        }

        private void drawVerticalConnection(DrawScope draw, StationMapPiece piece) {
            float x = mapX(pieceCenterX(piece));
            float y = mapY(pieceCenterZ(piece));
            float innerRadius = Math.max(7.0f, 11.0f * viewport().zoom());
            float outerRadius = Math.max(12.0f, 19.0f * viewport().zoom());
            draw.addCircleFilled(x, y, innerRadius, COLOR_VERTICAL_LINK, 32);
            draw.addCircle(x, y, outerRadius, MutableColor.rgba(0.68f, 1.0f, 0.90f, 0.62f), 32, 1.5f);
        }

        private float pieceCenterX(StationMapPiece piece) {
            int min = Math.min(piece.minX(), piece.maxX());
            int max = Math.max(piece.minX(), piece.maxX());
            return min + (max - min + 1) * 0.5f;
        }

        private float pieceCenterZ(StationMapPiece piece) {
            int min = Math.min(piece.minZ(), piece.maxZ());
            int max = Math.max(piece.minZ(), piece.maxZ());
            return min + (max - min + 1) * 0.5f;
        }

        private void drawPiece(DrawScope draw, StationMapPiece piece) {
            RoomMarker marker = markers.markerFor(piece);
            float x = mapX(Math.min(piece.minX(), piece.maxX()));
            float y = mapY(Math.min(piece.minZ(), piece.maxZ()));
            float w = Math.max(CELL, (Math.abs(piece.maxX() - piece.minX()) + 1) * CELL * viewport().zoom());
            float h = Math.max(CELL, (Math.abs(piece.maxZ() - piece.minZ()) + 1) * CELL * viewport().zoom());
            float radius = 0.0f;

            draw.addRectFilled(x + 4.0f, y + 5.0f, w, h, radius, MutableColor.rgba(0.0f, 0.0f, 0.0f, 0.26f));
            draw.addRectFilled(x, y, w, h, radius, marker.fill());
            draw.addRect(x, y, w, h, radius, marker.border(), piece.dockPiece() ? 3.0f : 1.4f);
            if ("quest".equals(marker.id())) {
                drawQuestHighlight(draw, x, y, w, h, radius);
            }

            if (piece.dockPiece()) {
                draw.addRect(x - 7.0f, y - 7.0f, w + 14.0f, h + 14.0f, radius + 6.0f, marker.border(), 1.5f);
            }
        }

        private float mapX(float stationX) {
            return worldToRootX(MAP_PADDING + (stationX - minX) * CELL);
        }

        private float mapY(float stationZ) {
            return worldToRootY(MAP_PADDING + (stationZ - minZ) * CELL);
        }

        private int clampFloor(int floor) {
            return Math.max(snapshot.minFloor(), Math.min(snapshot.maxFloor(), floor));
        }
    }




    private static boolean verticalMarkerVisible(StationMapPiece piece, int floor) {
        return containsFloor(piece, floor) && piece.minFloor() != piece.maxFloor();
    }
    private static boolean pieceVisibleOnFloor(StationMapPiece piece, int floor) {
        return containsFloor(piece, floor);
    }

    private static boolean containsFloor(StationMapPiece piece, int floor) {
        return floor >= piece.minFloor() && floor <= piece.maxFloor();
    }

    private static String floorLabel(int floor) {
        if (floor > 0) return "+" + floor;
        return Integer.toString(floor);
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
        private static Bounds of(List<StationMapPiece> pieces) {
            if (pieces.isEmpty()) return new Bounds(-20, -14, 20, 14);
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (StationMapPiece piece : pieces) {
                minX = Math.min(minX, Math.min(piece.minX(), piece.maxX()));
                minZ = Math.min(minZ, Math.min(piece.minZ(), piece.maxZ()));
                maxX = Math.max(maxX, Math.max(piece.minX(), piece.maxX()));
                maxZ = Math.max(maxZ, Math.max(piece.minZ(), piece.maxZ()));
                for (StationMapConnection connection : piece.connections()) {
                    minX = Math.min(minX, connection.x() - 2);
                    minZ = Math.min(minZ, connection.z() - 2);
                    maxX = Math.max(maxX, connection.x() + 2);
                    maxZ = Math.max(maxZ, connection.z() + 2);
                }
            }
            return new Bounds(minX, minZ, maxX, maxZ);
        }
    }

    private static StationMapSnapshot snapshotFrom(StationMapData data) {
        if (data == null) return sampleSnapshot();
        StationMapData.Point3i dock = data.dockWorld();
        List<StationMapPiece> pieces = new ArrayList<>(data.rooms().size());
        for (StationMapData.Room room : data.rooms()) {
            StationMapData.Box3i bounds = room.localSelectionBounds();
            List<StationMapConnection> connections = new ArrayList<>(room.passages().size());
            for (StationMapData.Passage passage : room.passages()) {
                Direction direction = Direction.byName(passage.direction());
                StationMapData.Point3i position = passage.localPosition();
                connections.add(new StationMapConnection(
                        passage.floor(),
                        position.x(),
                        position.z(),
                        direction == null ? Direction.NORTH : direction
                ));
            }
            pieces.add(new StationMapPiece(
                    resourceLocation(room.templateId(), "unigui_testmod", "room"),
                    room.minFloor(),
                    room.maxFloor(),
                    bounds.minX(),
                    bounds.minZ(),
                    bounds.maxX(),
                    bounds.maxZ(),
                    room.dockRoom(),
                    connections
            ));
        }
        String stationId = data.stationId() == null || data.stationId().isBlank() ? data.stationCode() : data.stationId();
        return new StationMapSnapshot(
                new BlockPos(dock.x(), dock.y(), dock.z()),
                UUID.nameUUIDFromBytes(stationId.getBytes(StandardCharsets.UTF_8)),
                data.stationCode(),
                dock.y(),
                dock.x(),
                dock.z(),
                data.minFloor(),
                data.maxFloor(),
                pieces
        );
    }
    public static StationMapMarkers sampleMarkers() {
        return new StationMapMarkers()
                .markAll("quest", MutableColor.rgba(1.0f, 0.72f, 0.25f, 1.0f),
                        "ЗАДАНИЕ", "Зона активного задания",
                        "unigui_testmod:hostile_reactor",
                        "unigui_testmod:treasure_storage",
                        "unigui_testmod:observation_room");
    }
    private static StationMapSnapshot sampleSnapshot() {
        List<StationMapPiece> pieces = List.of(
                piece("ship_dock", 0, 0, -16, -5, -2, 5, true,
                        conn(0, -1, 0, Direction.EAST)),
                piece("junction_main_a", 0, 0, -1, -1, 4, 1, false,
                        conn(0, -1, 0, Direction.WEST), conn(0, 5, 0, Direction.EAST)),
                piece("lift_core", -1, 1, 5, -3, 10, 3, false,
                        conn(0, 4, 0, Direction.WEST), conn(0, 11, 0, Direction.EAST),
                        conn(-1, 8, 0, Direction.DOWN), conn(1, 8, 0, Direction.UP)),
                piece("junction_main_b", 0, 0, 11, -1, 16, 1, false,
                        conn(0, 10, 0, Direction.WEST), conn(0, 17, 0, Direction.EAST)),
                piece("hostile_reactor", 0, 0, 17, -7, 29, 4, false,
                        conn(0, 16, 0, Direction.WEST), conn(0, 30, 0, Direction.EAST)),
                piece("treasure_storage", 0, 0, 30, -3, 39, 4, false,
                        conn(0, 29, 0, Direction.WEST)),

                piece("lower_lift", -1, -1, 4, -2, 11, 4, false,
                        conn(-1, 12, 1, Direction.EAST), conn(-1, 8, 0, Direction.UP)),
                piece("service_link", -1, -1, 12, 0, 19, 2, false,
                        conn(-1, 11, 1, Direction.WEST), conn(-1, 20, 1, Direction.EAST)),
                piece("hostile_service_room", -1, -1, 20, -2, 34, 7, false,
                        conn(-1, 19, 1, Direction.WEST), conn(-1, 35, 2, Direction.EAST)),
                piece("cache_server", -1, -1, 35, -4, 47, 5, false,
                        conn(-1, 34, 2, Direction.WEST)),

                piece("upper_lift", 1, 1, 5, -2, 11, 4, false,
                        conn(1, 12, 1, Direction.EAST), conn(1, 8, 0, Direction.DOWN)),
                piece("upper_link", 1, 1, 12, 0, 19, 2, false,
                        conn(1, 11, 1, Direction.WEST), conn(1, 20, 1, Direction.EAST)),
                piece("observation_room", 1, 1, 20, -7, 32, 4, false,
                        conn(1, 19, 1, Direction.WEST), conn(1, 33, 0, Direction.EAST)),
                piece("hostile_command", 1, 1, 33, -4, 45, 5, false,
                        conn(1, 32, 0, Direction.WEST))
        );
        return new StationMapSnapshot(
                new BlockPos(0, 64, 0),
                UUID.nameUUIDFromBytes("unigui-test-station".getBytes(StandardCharsets.UTF_8)),
                "AURORA-07",
                64,
                0,
                0,
                -1,
                1,
                pieces
        );
    }

    private static StationMapPiece piece(String path, int minFloor, int maxFloor,
                                         int minX, int minZ, int maxX, int maxZ,
                                         boolean dockPiece,
                                         StationMapConnection... connections) {
        return new StationMapPiece(
                resourceLocation("unigui_testmod:" + path, "unigui_testmod", "room"),
                minFloor,
                maxFloor,
                minX,
                minZ,
                maxX,
                maxZ,
                dockPiece,
                List.of(connections)
        );
    }

    private static StationMapConnection conn(int floor, int x, int z, Direction direction) {
        return new StationMapConnection(floor, x, z, direction);
    }

    private static MutableColor dimFill(MutableColor color) {
        if (color == null) return COLOR_ROOM.copy();
        return MutableColor.rgba(color.r() * 0.28f, color.g() * 0.28f, color.b() * 0.28f, 0.88f);
    }
    private static ResourceLocation resourceLocation(String id, String fallbackNamespace, String fallbackPath) {
        String normalized = id == null || id.isBlank()
                ? fallbackNamespace + ":" + fallbackPath
                : id.contains(":") ? id : fallbackNamespace + ":" + id;
        ResourceLocation location = ResourceLocation.tryParse(normalized);
        return location == null ? ResourceLocation.tryParse(fallbackNamespace + ":" + fallbackPath) : location;
    }
}
