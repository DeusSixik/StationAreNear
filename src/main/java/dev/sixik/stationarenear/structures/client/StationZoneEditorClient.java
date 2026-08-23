package dev.sixik.stationarenear.structures.client;

import dev.sixik.stationarenear.structures.editor.StationEditorNodeType;
import dev.sixik.stationarenear.structures.editor.StationStructureEditorStick;
import dev.sixik.stationarenear.structures.item.StationStructureToolItem;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.OpenStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.network.packet.SaveStationZoneEditorPacket;
import dev.sixik.stationarenear.structures.network.packet.TemplateSelectionActionPacket;
import dev.sixik.stationarenear.structures.util.NbtPos;
import dev.sixik.unigui.api.layout.Alignment;
import dev.sixik.unigui.api.layout.LayoutConstraints;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.*;
import dev.sixik.unigui.widgets.display.Label;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.interaction.Button;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import dev.sixik.unigui.widgets.interaction.ToggleButton;
import dev.sixik.unigui.widgets.navigation.TreeView;
import dev.sixik.unigui.widgets.navigation.TreeViewNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Objects;
import java.util.Locale;

public final class StationZoneEditorClient {

    private StationZoneEditorClient() {
    }

    public static void open(OpenStationZoneEditorPacket packet) {
        StationEditorClientState.setEditorTag(packet.editorTag());
        StationEditorClientState.setPoolIds(packet.poolIds());
        Minecraft minecraft = Minecraft.getInstance();
        Widget root = new EditorRoot(packet.editorTag()).root();

        try {
            Object screen = Class.forName("dev.sixik.unigui.backend.minecraft.MinecraftWidgetScreen")
                    .getConstructor(Component.class, Widget.class)
                    .newInstance(Component.literal("Station Structure Editor"), root);
            minecraft.setScreen((Screen) screen);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            minecraft.setScreen(null);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Unable to open UniGUI station editor: " + exception.getClass().getSimpleName()), false);
            }
        }
    }

    private static final class EditorRoot {
        private final CompoundTag tag;
        private final StackPanel viewport = new StackPanel();
        private final OverlayLayer overlayLayer = new OverlayLayer(viewport);
        private final TreeView hierarchy = new TreeView();
        private final VBox inspector = new VBox();
        private final Label status = new Label("Ready");
        private String selectedKey = "root";
        private String pendingOverwriteTemplate = "";

        private EditorRoot(CompoundTag source) {
            this.tag = source.copy();
            viewport.addChild(backgroundFrame());
            VBox mainPanel = new VBox();
            viewport.addChild(mainPanel);

            StationStructureEditorStick.normalize(this.tag);
            StationEditorClientState.setEditorTag(this.tag);
            mainPanel.layout(style -> style.size(640.0f, 390.0f).padding(6.0f).gap(4.0f).align(Alignment.CENTER, Alignment.CENTER));

            Label title = new Label("Station Structure Editor");
            title.layout(style -> style.size(LayoutConstraints.AUTO, 22.0f).flexGrow(0).flexShrink(0.0f));
            mainPanel.addChild(backgroundFrame());
            mainPanel.addChild(title);

            HBox workspace = new HBox();
            workspace.layout(style -> style.size(LayoutConstraints.AUTO, 328.0f).gap(6.0f).flexGrow(1.0f).flexShrink(1.0f));
            workspace.addChild(buildHierarchyPane());
            workspace.addChild(buildInspectorPane());
            mainPanel.addChild(workspace);

            HBox footer = new HBox();
            footer.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            status.layout(style -> style.size(LayoutConstraints.AUTO, 22.0f).flexGrow(1.0f).flexShrink(1.0f));
            Button save = button("Save to wand");
            Button saveStructure = button("Save Structure");
            Button clearWand = button("Clear Wand");
            Button templates = button("Templates");
            save.onClick(event -> saveTag());
            saveStructure.onClick(event -> saveStructure());
            clearWand.onClick(event -> clearWandData());
            templates.onClick(event -> StationStructureNetwork.sendTemplateAction(new TemplateSelectionActionPacket("", "open")));
            footer.addChild(status);
            footer.addChild(save);
            footer.addChild(saveStructure);
            footer.addChild(clearWand);
            footer.addChild(templates);
            mainPanel.addChild(footer);

            rebuildHierarchy();
            String initialSelection = tag.getString(StationStructureEditorStick.KEY_SELECTED_NODE);
            select(initialSelection.startsWith("trigger:") ? initialSelection : "root");
        }

        private Widget root() {
            return overlayLayer;
        }

        private Widget buildHierarchyPane() {
            VBox pane = pane("Hierarchy");
            HBox toolbar = new HBox();
            toolbar.layout(style -> style.size(LayoutConstraints.AUTO, 26.0f).gap(4.0f).flexGrow(0).flexShrink(0.0f));
            Button addConnection = button("+ Connection");
            Button addTrigger = button("Create Trigger");
            addConnection.onClick(event -> addConnection());
            addTrigger.onClick(event -> addTrigger(StationEditorNodeType.TRIGGER));
            toolbar.addChild(addConnection);
            toolbar.addChild(addTrigger);

            hierarchy.layout(style -> style.size(230.0f, LayoutConstraints.AUTO).flexGrow(1.0f).flexShrink(1.0f));
            hierarchy.onSelectionChanged(event -> {
                TreeViewNode node = hierarchy.selectedNode();
                if (node != null) {
                    select(node.value());
                }
            });
            pane.addChild(toolbar);
            pane.addChild(hierarchy);
            return pane;
        }

        private Widget buildInspectorPane() {
            VBox pane = pane("Inspector");
            ScrollView scroll = new ScrollView(inspector);
            scroll.layout(style -> style.size(LayoutConstraints.AUTO, LayoutConstraints.AUTO).flexGrow(1.0f).flexShrink(1.0f));
            pane.addChild(scroll);
            return pane;
        }

        private VBox pane(String title) {
            VBox pane = new VBox();
            pane.layout(style -> style.size(LayoutConstraints.AUTO, LayoutConstraints.AUTO).padding(6.0f).gap(5.0f).flexGrow(1.0f).flexShrink(1.0f));
            Label header = new Label(title);
            header.layout(style -> style.size(LayoutConstraints.AUTO, 20.0f).flexGrow(0).flexShrink(0.0f));
            pane.addChild(header);
            return pane;
        }

        private void rebuildHierarchy() {
            hierarchy.clearRoots();
            BlockPos min = StationStructureEditorStick.structureMin(tag);
            BlockPos max = StationStructureEditorStick.structureMax(tag);
            TreeViewNode root = hierarchy.addRoot("Root Structure Zone " + min.toShortString() + " -> " + max.toShortString()).value("root").expanded(true);
            if (hasTriggerDraft()) {
                root.addChild("Trigger Draft").value("trigger_draft");
            }
            if (hasConnectionDraft()) {
                root.addChild("Connection Draft").value("connection_draft");
            }

            TreeViewNode connections = root.addChild("Connections").value("connections").expanded(true).selectable(false);
            ListTag connectorTags = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            for (int i = 0; i < connectorTags.size(); i++) {
                CompoundTag connector = connectorTags.getCompound(i);
                connections.addChild("Connection: " + connector.getString("name")).value("connection:" + i);
            }

            TreeViewNode triggers = root.addChild("Triggers").value("triggers").expanded(true).selectable(false);
            ListTag triggerTags = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            for (int i = 0; i < triggerTags.size(); i++) {
                CompoundTag trigger = triggerTags.getCompound(i);
                triggers.addChild(trigger.getString("nodeType") + ": " + trigger.getString("id")).value("trigger:" + i);
            }
            hierarchy.silentSelect(root);
        }

        private void select(String key) {
            selectedKey = key == null ? "root" : key;
            StationEditorClientState.selectedKey(selectedKey);
            inspector.clearChildren();
            if (selectedKey.equals("root")) {
                inspectRoot();
            } else if (selectedKey.equals("trigger_draft")) {
                inspectTriggerDraft();
            } else if (selectedKey.equals("connection_draft")) {
                inspectConnectionDraft();
            } else if (selectedKey.startsWith("connection:")) {
                inspectConnection(parseIndex(selectedKey));
            } else if (selectedKey.startsWith("trigger:")) {
                inspectTrigger(parseIndex(selectedKey));
            } else {
                inspector.addChild(label("Select Root, Connection or Trigger."));
            }
            StationStructureEditorStick.normalize(tag);
            StationEditorClientState.setEditorTag(tag);
        }

        private void inspectRoot() {
            inspector.addChild(label("Root defines the actual piece bounds. Children are clamped inside it."));
            inspector.addChild(label("Mode: " + tag.getString(StationStructureEditorStick.KEY_EDITOR_MODE)));
            TextField template = field(tag.getString(StationStructureToolItem.KEY_TEMPLATE), "stationarenear:stations/piece");
            TextField pool = field(tag.getString(StationStructureToolItem.KEY_POOL), "stationarenear:space_station");
            TextField weight = field(Integer.toString(tag.getInt(StationStructureToolItem.KEY_WEIGHT)), "1");
            TextField floors = field(Integer.toString(tag.getInt(StationStructureToolItem.KEY_FLOOR_SPAN)), "1");
            ToggleButton startPiece = new ToggleButton("Docking/start piece").silentChecked(tag.getBoolean(StationStructureToolItem.KEY_START_PIECE));
            ToggleButton showHandles = new ToggleButton("Render POS handles").silentChecked(!tag.contains(StationStructureEditorStick.KEY_SHOW_HANDLES) || tag.getBoolean(StationStructureEditorStick.KEY_SHOW_HANDLES));
            ToggleButton showRootText = new ToggleButton("Render Root label").silentChecked(!tag.contains(StationStructureEditorStick.KEY_SHOW_ROOT_TEXT) || tag.getBoolean(StationStructureEditorStick.KEY_SHOW_ROOT_TEXT));
            ToggleButton lockRootZone = new ToggleButton("Lock Root zone selection").silentChecked(tag.getBoolean(StationStructureEditorStick.KEY_LOCK_ROOT_ZONE));
            row("Template", template);
            row("Pool", pool);
            row("Weight", weight);
            row("Floors", floors);
            inspector.addChild(startPiece);
            inspector.addChild(showHandles);
            inspector.addChild(showRootText);
            inspector.addChild(lockRootZone);
            HBox clearActions = new HBox();
            clearActions.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Button clearTriggerDraft = button("Clear Trigger draft");
            Button clearConnectionDraft = button("Clear Connection draft");
            Button clearSelection = button("Clear Selection");
            clearTriggerDraft.onClick(event -> clearTriggerDraft());
            clearConnectionDraft.onClick(event -> clearConnectionDraft());
            clearSelection.onClick(event -> clearStructureSelection());
            clearActions.addChild(clearTriggerDraft);
            clearActions.addChild(clearConnectionDraft);
            clearActions.addChild(clearSelection);
            inspector.addChild(clearActions);
            bind(template, value -> tag.putString(StationStructureToolItem.KEY_TEMPLATE, value));
            bind(pool, value -> tag.putString(StationStructureToolItem.KEY_POOL, value));
            bind(weight, value -> tag.putInt(StationStructureToolItem.KEY_WEIGHT, Math.max(1, parseInt(value, 1))));
            bind(floors, value -> tag.putInt(StationStructureToolItem.KEY_FLOOR_SPAN, Math.max(1, parseInt(value, 1))));
            startPiece.onCheckedChanged(event -> { tag.putBoolean(StationStructureToolItem.KEY_START_PIECE, event.newValue()); syncEditorState(); });
            showHandles.onCheckedChanged(event -> { tag.putBoolean(StationStructureEditorStick.KEY_SHOW_HANDLES, event.newValue()); syncEditorState(); });
            showRootText.onCheckedChanged(event -> { tag.putBoolean(StationStructureEditorStick.KEY_SHOW_ROOT_TEXT, event.newValue()); syncEditorState(); });
            lockRootZone.onCheckedChanged(event -> { tag.putBoolean(StationStructureEditorStick.KEY_LOCK_ROOT_ZONE, event.newValue()); syncEditorState(); });
        }

        private void inspectTriggerDraft() {
            if (!hasTriggerDraft()) {
                inspector.addChild(label("No trigger draft. Use Trigger Manager Create mode and select two blocks inside Root."));
                return;
            }
            BlockPos draftMin = toLocal(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)));
            BlockPos draftMax = toLocal(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)));
            inspector.addChild(label("Draft local zone: " + draftMin.toShortString() + " -> " + draftMax.toShortString()));
            ComboBox nodeType = combo(triggerNodeTypes(), StationEditorNodeType.TRIGGER.name());
            TextField id = field("trigger_" + tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).size(), "trigger_id");
            row("Node type", nodeType);
            row("ID", id);
            HBox actions = new HBox();
            actions.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Button create = button("Create trigger");
            Button clear = button("Clear draft");
            create.onClick(event -> {
                StationEditorNodeType selectedType = StationEditorNodeType.valueOf(nodeType.selectedItem());
                createTriggerFromDraft(selectedType, id.text().trim(), defaultTriggerType(selectedType));
            });
            clear.onClick(event -> clearTriggerDraft());
            actions.addChild(create);
            actions.addChild(clear);
            inspector.addChild(actions);
        }

        private void inspectConnectionDraft() {
            if (!hasConnectionDraft()) {
                inspector.addChild(label("No connection draft. Use Connection Manager mode to place POS_1/POS_2 inside Root."));
                return;
            }
            BlockPos draftMin = connectionDraftMin();
            BlockPos draftMax = connectionDraftMax();
            Direction draftDirection = autoDirectionFromRootFace(draftMin, draftMax);
            int draftWidth = connectionWidth(draftMin, draftMax, draftDirection);
            int draftHeight = connectionHeight(draftMin, draftMax, draftDirection);
            inspector.addChild(label("Draft local connection: " + toLocal(draftMin).toShortString() + " -> " + toLocal(draftMax).toShortString()));
            TextField name = field("connection_" + tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND).size(), "main");
            ComboBox direction = combo(directionNames(), draftDirection.getSerializedName());
            TextField width = field(Integer.toString(draftWidth), "3");
            TextField height = field(Integer.toString(draftHeight), "3");
            TextField acceptedSizes = field(draftWidth + "x" + draftHeight, "3x3,2x1");
            TextField tags = field("corridor", "corridor,dock");
            TextField accepts = field("corridor,dock", "corridor,dock");
            TextField priority = field("0", "0");
            row("Name", name);
            row("Direction", direction);
            row("Size W", width);
            row("Size H", height);
            row("Accepted sizes", acceptedSizes);
            row("Tags", tags);
            row("Accepts", accepts);
            row("Priority", priority);
            HBox actions = new HBox();
            actions.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Button create = button("Create connection");
            Button clear = button("Clear draft");
            create.onClick(event -> createConnectionFromDraft(name.text().trim(), direction.selectedItem(), tags.text().trim(), accepts.text().trim(), parseInt(priority.text(), 0), Math.max(1, parseInt(width.text(), 3)), Math.max(1, parseInt(height.text(), 3)), acceptedSizes.text().trim()));
            clear.onClick(event -> clearConnectionDraft());
            actions.addChild(create);
            actions.addChild(clear);
            inspector.addChild(actions);
        }

        private void inspectConnection(int index) {
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            if (index < 0 || index >= connectors.size()) {
                inspector.addChild(label("Missing connection."));
                return;
            }
            CompoundTag connector = connectors.getCompound(index);
            inspector.addChild(label("Connection is a bounded Jigsaw-like socket. Bounds and anchor are clamped inside Root."));
            TextField name = field(connector.getString("name"), "main");
            TextField pos = field(localPosText(NbtPos.load(connector.getCompound("worldPosition"))), "local x y z");
            TextField min = field(localPosText(NbtPos.load(connector.getCompound("worldMin"))), "local x y z");
            TextField max = field(localPosText(NbtPos.load(connector.getCompound("worldMax"))), "local x y z");
            ComboBox direction = combo(directionNames(), connector.getString("direction"));
            TextField tags = field(connector.getString("tags"), "corridor,dock");
            TextField accepts = field(connector.getString("accepts"), "corridor,dock");
            TextField priority = field(Integer.toString(connector.getInt("priority")), "0");
            TextField width = field(Integer.toString(connector.getInt("width")), "3");
            TextField height = field(Integer.toString(connector.getInt("height")), "3");
            TextField acceptedSizes = field(connector.getString("acceptedSizes"), "3x3,2x1");
            row("Name", name);
            row("Anchor", pos);
            row("Min", min);
            row("Max", max);
            row("Direction", direction);
            row("Size W", width);
            row("Size H", height);
            row("Accepted sizes", acceptedSizes);
            row("Tags", tags);
            row("Accepts", accepts);
            row("Priority", priority);
            HBox editActions = new HBox();
            editActions.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Button editSize = button("Edit Size from draft");
            Button move = button("Move to draft POS_1");
            editSize.onClick(event -> resizeConnectionFromDraft(index));
            move.onClick(event -> moveConnectionToDraft(index));
            editActions.addChild(editSize);
            editActions.addChild(move);
            inspector.addChild(editActions);
            Button delete = button("Delete connection");
            delete.onClick(event -> { connectors.remove(index); tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors); rebuildHierarchy(); select("root"); });
            inspector.addChild(delete);
            bind(name, value -> updateConnector(index, connectorTag -> connectorTag.putString("name", value)));
            bind(pos, value -> updateConnector(index, connectorTag -> connectorTag.put("worldPosition", NbtPos.save(worldFromLocal(parsePos(value, toLocal(NbtPos.load(connectorTag.getCompound("worldPosition")))))))));
            bind(min, value -> updateConnector(index, connectorTag -> connectorTag.put("worldMin", NbtPos.save(worldFromLocal(parsePos(value, toLocal(NbtPos.load(connectorTag.getCompound("worldMin")))))))));
            bind(max, value -> updateConnector(index, connectorTag -> connectorTag.put("worldMax", NbtPos.save(worldFromLocal(parsePos(value, toLocal(NbtPos.load(connectorTag.getCompound("worldMax")))))))));
            direction.onSelectionChanged(event -> { updateConnector(index, connectorTag -> connectorTag.putString("direction", comboSelectedItem(direction, event))); syncEditorState(); });
            bind(tags, value -> updateConnector(index, connectorTag -> connectorTag.putString("tags", value)));
            bind(accepts, value -> updateConnector(index, connectorTag -> connectorTag.putString("accepts", value)));
            bind(priority, value -> updateConnector(index, connectorTag -> connectorTag.putInt("priority", parseInt(value, 0))));
            bind(width, value -> updateConnector(index, connectorTag -> connectorTag.putInt("width", Math.max(1, parseInt(value, 3)))));
            bind(height, value -> updateConnector(index, connectorTag -> connectorTag.putInt("height", Math.max(1, parseInt(value, 3)))));
            bind(acceptedSizes, value -> updateConnector(index, connectorTag -> connectorTag.putString("acceptedSizes", value)));
        }

        private void inspectTrigger(int index) {
            ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            if (index < 0 || index >= triggers.size()) {
                inspector.addChild(label("Missing trigger."));
                return;
            }
            CompoundTag trigger = triggers.getCompound(index);
            inspector.addChild(label("Trigger zone is rendered inside Root and cannot leave Root bounds."));
            ComboBox nodeType = combo(Arrays.stream(StationEditorNodeType.values()).filter(type -> type != StationEditorNodeType.STRUCTURE && type != StationEditorNodeType.CONNECTION).map(Enum::name).toArray(String[]::new), trigger.getString("nodeType"));
            TextField id = field(trigger.getString("id"), "trigger_id");
            TextField min = field(localPosText(NbtPos.load(trigger.getCompound("worldMin"))), "local x y z");
            TextField max = field(localPosText(NbtPos.load(trigger.getCompound("worldMax"))), "local x y z");
            row("Node type", nodeType);
            row("ID", id);
            row("Min", min);
            row("Max", max);
            inspectTriggerData(index, trigger);
            HBox editActions = new HBox();
            editActions.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Button editSize = button("Edit Size from draft");
            Button move = button("Move to draft POS_1");
            editSize.onClick(event -> resizeTriggerFromDraft(index));
            move.onClick(event -> moveTriggerToDraft(index));
            editActions.addChild(editSize);
            editActions.addChild(move);
            inspector.addChild(editActions);
            Button delete = button("Delete trigger");
            delete.onClick(event -> { triggers.remove(index); tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers); rebuildHierarchy(); select("root"); });
            inspector.addChild(delete);
            nodeType.onSelectionChanged(event -> { String selected = comboSelectedItem(nodeType, event); StationEditorNodeType selectedType = StationEditorNodeType.valueOf(selected); updateTrigger(index, triggerTag -> { triggerTag.putString("nodeType", selected); triggerTag.putString("type", defaultTriggerType(selectedType)); CompoundTag data = triggerTag.getCompound("data").copy(); mergeDefaultTriggerData(data, selectedType); triggerTag.put("data", data); }); rebuildHierarchy(); syncEditorState(); select("trigger:" + index); });
            bind(id, value -> updateTrigger(index, triggerTag -> triggerTag.putString("id", value)));
            bind(min, value -> updateTrigger(index, triggerTag -> triggerTag.put("worldMin", NbtPos.save(worldFromLocal(parsePos(value, toLocal(NbtPos.load(triggerTag.getCompound("worldMin")))))))));
            bind(max, value -> updateTrigger(index, triggerTag -> triggerTag.put("worldMax", NbtPos.save(worldFromLocal(parsePos(value, toLocal(NbtPos.load(triggerTag.getCompound("worldMax")))))))));
        }

        private void inspectTriggerData(int index, CompoundTag trigger) {
            StationEditorNodeType nodeType = parseEditorNodeType(trigger.getString("nodeType"));
            CompoundTag data = trigger.getCompound("data");
            if (nodeType == StationEditorNodeType.OBJECT_PLACER || nodeType == StationEditorNodeType.LOOT) {
                inspector.addChild(label("ObjectPlacer: places one random object template from pool inside this zone."));
                ToggleButton place = new ToggleButton("Place objects").silentChecked(!data.contains("place") || data.getBoolean("place"));
                ToggleButton ignoreChance = new ToggleButton("IgnoreChancePlace").silentChecked(data.getBoolean("ignoreChancePlace"));
                ToggleButton randomRotation = new ToggleButton("Random rotation").silentChecked(data.getBoolean("randomRotation"));
                ComboBox pool = combo(poolItems(data.getString("pool")), data.getString("pool"));
                TextField chance = field(Integer.toString(data.contains("placeChance") ? data.getInt("placeChance") : 50), "0-100");
                row("Object Pool", pool);
                row("Place Chance", chance);
                inspector.addChild(place);
                inspector.addChild(ignoreChance);
                inspector.addChild(randomRotation);
                pool.onSelectionChanged(event -> { updateTriggerData(index, dataTag -> dataTag.putString("pool", comboSelectedItem(pool, event))); syncEditorState(); });
                bind(chance, value -> updateTriggerData(index, dataTag -> dataTag.putInt("placeChance", Math.max(0, Math.min(100, parseInt(value, 50))))));
                place.onCheckedChanged(event -> { updateTriggerData(index, dataTag -> dataTag.putBoolean("place", event.newValue())); syncEditorState(); });
                ignoreChance.onCheckedChanged(event -> { updateTriggerData(index, dataTag -> dataTag.putBoolean("ignoreChancePlace", event.newValue())); syncEditorState(); });
                randomRotation.onCheckedChanged(event -> { updateTriggerData(index, dataTag -> dataTag.putBoolean("randomRotation", event.newValue())); syncEditorState(); });
            } else if (nodeType == StationEditorNodeType.MOB_SPAWN) {
                inspector.addChild(label("MobSpawn: default is danger-scaled zombies/skeletons; quest code may override event."));
                ToggleButton place = new ToggleButton("Place mobs").silentChecked(!data.contains("place") || data.getBoolean("place"));
                TextField mob = field(data.getString("mob"), "minecraft:zombie or empty");
                TextField count = field(Integer.toString(data.contains("count") ? data.getInt("count") : 0), "0 = danger default");
                row("Mob", mob);
                row("Count", count);
                inspector.addChild(place);
                bind(mob, value -> updateTriggerData(index, dataTag -> dataTag.putString("mob", value)));
                bind(count, value -> updateTriggerData(index, dataTag -> dataTag.putInt("count", Math.max(0, parseInt(value, 0)))));
                place.onCheckedChanged(event -> { updateTriggerData(index, dataTag -> dataTag.putBoolean("place", event.newValue())); syncEditorState(); });
            } else if (nodeType == StationEditorNodeType.TRIGGER_QUEST || nodeType == StationEditorNodeType.QUEST_TRIGGER) {
                inspector.addChild(label("QuestTrigger: event-only marker, no default placement logic."));
                TextField questId = field(data.getString("questId"), "quest/task id");
                TextField place = field(data.getString("place"), "optional place key");
                row("Quest ID", questId);
                row("Place", place);
                bind(questId, value -> updateTriggerData(index, dataTag -> dataTag.putString("questId", value)));
                bind(place, value -> updateTriggerData(index, dataTag -> dataTag.putString("place", value)));
            }
        }

        private void row(String name, Widget field) {
            HBox row = new HBox();
            row.layout(style -> style.size(LayoutConstraints.AUTO, 26.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
            Label label = new Label(name);
            label.layout(style -> style.size(105.0f, 20.0f).flexGrow(0).flexShrink(0.0f));
            if (field instanceof WidgetBase widgetBase) {
                widgetBase.layout(style -> style.size(LayoutConstraints.AUTO, 22.0f).flexGrow(1.0f).flexShrink(1.0f));
            }
            row.addChild(label);
            row.addChild(field);
            inspector.addChild(row);
        }

        private StationEditorNodeType parseEditorNodeType(String value) {
            try {
                return StationEditorNodeType.valueOf(value);
            } catch (IllegalArgumentException exception) {
                return StationEditorNodeType.TRIGGER;
            }
        }

        private String defaultTriggerType(StationEditorNodeType nodeType) {
            return switch (nodeType) {
                case OBJECT_PLACER, LOOT -> "object_placer";
                case MOB_SPAWN -> "mob_spawn";
                case TRIGGER_QUEST, QUEST_TRIGGER -> "quest";
                default -> nodeType.name().toLowerCase(Locale.ROOT);
            };
        }

        private CompoundTag defaultTriggerData(StationEditorNodeType nodeType) {
            CompoundTag data = new CompoundTag();
            mergeDefaultTriggerData(data, nodeType);
            return data;
        }

        private void mergeDefaultTriggerData(CompoundTag data, StationEditorNodeType nodeType) {
            if (nodeType == StationEditorNodeType.OBJECT_PLACER || nodeType == StationEditorNodeType.LOOT) {
                if (!data.contains("pool") || data.getString("pool").isBlank()) {
                    data.putString("pool", "stationarenear:objects/default");
                }
                if (!data.contains("placeChance")) {
                    data.putInt("placeChance", 50);
                }
                if (!data.contains("place")) {
                    data.putBoolean("place", true);
                }
                if (!data.contains("ignoreChancePlace")) {
                    data.putBoolean("ignoreChancePlace", false);
                }
                if (!data.contains("randomRotation")) {
                    data.putBoolean("randomRotation", true);
                }
            } else if (nodeType == StationEditorNodeType.MOB_SPAWN) {
                if (!data.contains("place")) {
                    data.putBoolean("place", true);
                }
                if (!data.contains("count")) {
                    data.putInt("count", 0);
                }
            } else if (nodeType == StationEditorNodeType.TRIGGER_QUEST || nodeType == StationEditorNodeType.QUEST_TRIGGER) {
                if (!data.contains("questId")) {
                    data.putString("questId", "");
                }
            }
        }

        private void addConnection() {
            if (!hasConnectionDraft()) {
                status.text("Use Connection Manager mode to place draft POS_1/POS_2 first.");
                select("root");
                return;
            }
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            BlockPos draftMin = connectionDraftMin();
            BlockPos draftMax = connectionDraftMax();
            Direction draftDirection = autoDirectionFromRootFace(draftMin, draftMax);
            int draftWidth = connectionWidth(draftMin, draftMax, draftDirection);
            int draftHeight = connectionHeight(draftMin, draftMax, draftDirection);
            createConnectionFromDraft("connection_" + connectors.size(), draftDirection.getSerializedName(), "corridor", "corridor,dock", 0, draftWidth, draftHeight, draftWidth + "x" + draftHeight);
        }

        private void createConnectionFromDraft(String name, String direction, String tags, String accepts, int priority, int width, int height, String acceptedSizes) {
            if (!hasConnectionDraft()) {
                status.text("No connection draft. Use Connection Manager mode first.");
                return;
            }
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            CompoundTag connector = new CompoundTag();
            BlockPos draftMin = connectionDraftMin();
            BlockPos draftMax = connectionDraftMax();
            connector.putString("nodeType", StationEditorNodeType.CONNECTION.name());
            connector.putString("name", name == null || name.isBlank() ? "connection_" + connectors.size() : name);
            connector.put("worldMin", NbtPos.save(draftMin));
            connector.put("worldMax", NbtPos.save(draftMax));
            connector.put("worldPosition", NbtPos.save(connectionDraftCenter(draftMin, draftMax)));
            Direction resolvedDirection = Direction.byName(direction == null ? "" : direction);
            connector.putString("direction", (resolvedDirection == null ? autoDirectionFromRootFace(draftMin, draftMax) : resolvedDirection).getSerializedName());
            connector.putString("tags", tags == null || tags.isBlank() ? "corridor" : tags);
            connector.putString("accepts", accepts == null || accepts.isBlank() ? "corridor,dock" : accepts);
            connector.putInt("priority", priority);
            connector.putInt("width", Math.max(1, width));
            connector.putInt("height", Math.max(1, height));
            connector.putString("acceptedSizes", acceptedSizes == null || acceptedSizes.isBlank() ? "3x3" : acceptedSizes);
            connectors.add(connector);
            tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors);
            clearConnectionDraftTags();
            rebuildHierarchy();
            select("connection:" + (connectors.size() - 1));
            status.text("Connection created from draft inside Root.");
        }

        private void addTrigger(StationEditorNodeType nodeType) {
            if (!hasTriggerDraft()) {
                status.text("Use Trigger Manager Create mode to place draft POS_1/POS_2 first.");
                select("root");
                return;
            }
            createTriggerFromDraft(nodeType, nodeType.name().toLowerCase(Locale.ROOT) + "_" + tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).size(), defaultTriggerType(nodeType));
        }

        private void createTriggerFromDraft(StationEditorNodeType nodeType, String id, String type) {
            ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            CompoundTag trigger = new CompoundTag();
            trigger.putString("nodeType", nodeType.name());
            trigger.putString("id", id == null || id.isBlank() ? nodeType.name().toLowerCase(Locale.ROOT) + "_" + triggers.size() : id);
            trigger.putString("type", type == null || type.isBlank() ? defaultTriggerType(nodeType) : type);
            BlockPos draftA = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)));
            BlockPos draftB = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)));
            trigger.put("worldMin", NbtPos.save(new BlockPos(Math.min(draftA.getX(), draftB.getX()), Math.min(draftA.getY(), draftB.getY()), Math.min(draftA.getZ(), draftB.getZ()))));
            trigger.put("worldMax", NbtPos.save(new BlockPos(Math.max(draftA.getX(), draftB.getX()), Math.max(draftA.getY(), draftB.getY()), Math.max(draftA.getZ(), draftB.getZ()))));
            trigger.put("data", defaultTriggerData(nodeType));
            triggers.add(trigger);
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
            clearDraftTags();
            rebuildHierarchy();
            select("trigger:" + (triggers.size() - 1));
            status.text(nodeType.name() + " created inside Root.");
        }

        private void clearTriggerDraft() {
            clearDraftTags();
            rebuildHierarchy();
            select("root");
            status.text("Trigger draft cleared.");
        }

        private void clearStructureSelection() {
            if (!tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND).isEmpty()
                    || !tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND).isEmpty()) {
                status.text("Cannot clear Structure selection while it has connections/triggers.");
                return;
            }
            tag.remove(StationStructureToolItem.KEY_POS_1);
            tag.remove(StationStructureToolItem.KEY_POS_2);
            clearDraftTags();
            clearConnectionDraftTags();
            rebuildHierarchy();
            select("root");
            status.text("Structure selection cleared.");
        }

        private void clearDraftTags() {
            tag.remove(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1);
            tag.remove(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2);
            syncEditorState();
        }

        private void clearConnectionDraft() {
            clearConnectionDraftTags();
            rebuildHierarchy();
            select("root");
            status.text("Connection draft cleared.");
        }

        private void clearConnectionDraftTags() {
            tag.remove(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1);
            tag.remove(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2);
            syncEditorState();
        }

        private void resizeTriggerFromDraft(int index) {
            if (!hasTriggerDraft()) {
                status.text("No draft zone. Use Trigger Manager Create mode to place two points first.");
                return;
            }
            ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            CompoundTag trigger = triggers.getCompound(index);
            BlockPos draftA = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)));
            BlockPos draftB = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2)));
            trigger.put("worldMin", NbtPos.save(new BlockPos(Math.min(draftA.getX(), draftB.getX()), Math.min(draftA.getY(), draftB.getY()), Math.min(draftA.getZ(), draftB.getZ()))));
            trigger.put("worldMax", NbtPos.save(new BlockPos(Math.max(draftA.getX(), draftB.getX()), Math.max(draftA.getY(), draftB.getY()), Math.max(draftA.getZ(), draftB.getZ()))));
            triggers.set(index, trigger);
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
            syncEditorState();
            select("trigger:" + index);
            status.text("Trigger size updated from draft.");
        }

        private void moveTriggerToDraft(int index) {
            if (!tag.contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)) {
                status.text("No draft POS_1. Use Trigger Manager Create mode to place target point first.");
                return;
            }
            ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            CompoundTag trigger = triggers.getCompound(index);
            BlockPos oldMin = NbtPos.load(trigger.getCompound("worldMin"));
            BlockPos oldMax = NbtPos.load(trigger.getCompound("worldMax"));
            BlockPos target = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)));
            int dx = target.getX() - oldMin.getX();
            int dy = target.getY() - oldMin.getY();
            int dz = target.getZ() - oldMin.getZ();
            trigger.put("worldMin", NbtPos.save(target));
            trigger.put("worldMax", NbtPos.save(clamp(oldMax.offset(dx, dy, dz))));
            triggers.set(index, trigger);
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
            syncEditorState();
            select("trigger:" + index);
            status.text("Trigger moved to draft POS_1.");
        }

        private void resizeConnectionFromDraft(int index) {
            if (!hasConnectionDraft()) {
                status.text("No connection draft. Use Connection Manager mode to place POS_1/POS_2 first.");
                return;
            }
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            CompoundTag connector = connectors.getCompound(index);
            BlockPos draftMin = connectionDraftMin();
            BlockPos draftMax = connectionDraftMax();
            connector.put("worldMin", NbtPos.save(draftMin));
            connector.put("worldMax", NbtPos.save(draftMax));
            connector.put("worldPosition", NbtPos.save(connectionDraftCenter(draftMin, draftMax)));
            connectors.set(index, connector);
            tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors);
            syncEditorState();
            select("connection:" + index);
            status.text("Connection size updated from draft.");
        }

        private void moveConnectionToDraft(int index) {
            if (!tag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)) {
                status.text("No connection draft POS_1. Use Connection Manager mode first.");
                return;
            }
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            CompoundTag connector = connectors.getCompound(index);
            BlockPos oldMin = NbtPos.load(connector.getCompound("worldMin"));
            BlockPos oldMax = NbtPos.load(connector.getCompound("worldMax"));
            BlockPos oldAnchor = NbtPos.load(connector.getCompound("worldPosition"));
            BlockPos target = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)));
            int dx = target.getX() - oldMin.getX();
            int dy = target.getY() - oldMin.getY();
            int dz = target.getZ() - oldMin.getZ();
            connector.put("worldMin", NbtPos.save(target));
            connector.put("worldMax", NbtPos.save(clamp(oldMax.offset(dx, dy, dz))));
            connector.put("worldPosition", NbtPos.save(clamp(oldAnchor.offset(dx, dy, dz))));
            connectors.set(index, connector);
            tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors);
            syncEditorState();
            select("connection:" + index);
            status.text("Connection moved to draft POS_1.");
        }

        private void saveTag() {
            StationStructureEditorStick.normalize(tag);
            StationEditorClientState.setEditorTag(tag);
            StationStructureNetwork.sendEditorSave(new SaveStationZoneEditorPacket(tag.copy()));
            status.text("Saved to server wand NBT.");
        }

        private void saveStructure() {
            StationStructureEditorStick.normalize(tag);
            String template = tag.getString(StationStructureToolItem.KEY_TEMPLATE);
            boolean overwrite = templateExists(template);
            if (overwrite && !template.equals(pendingOverwriteTemplate)) {
                pendingOverwriteTemplate = template;
                status.text("Template " + template + " already exists. Click Save Structure again to overwrite.");
                return;
            }

            StationEditorClientState.setEditorTag(tag);
            StationStructureNetwork.sendEditorSave(new SaveStationZoneEditorPacket(tag.copy(), true, overwrite, false));
            pendingOverwriteTemplate = "";
            status.text("Saving structure template. Wand data kept; use Clear Wand for a new piece.");
        }

        private void clearWandData() {
            StationStructureEditorStick.clearEditorData(tag);
            StationStructureEditorStick.normalize(tag);
            StationEditorClientState.setEditorTag(tag);
            StationStructureNetwork.sendEditorSave(new SaveStationZoneEditorPacket(new CompoundTag(), false, false, true));
            rebuildHierarchy();
            select("root");
            status.text("Wand data cleared. You can select a new Structure Zone.");
        }

        private boolean templateExists(String templateText) {
            ResourceLocation template = ResourceLocation.tryParse(templateText);
            return template != null && StationEditorClientState.templateSelections().stream().anyMatch(entry -> entry.template().equals(template));
        }

        private void bind(TextField field, java.util.function.Consumer<String> consumer) {
            field.onTextChanged(event -> {
                pendingOverwriteTemplate = "";
                consumer.accept(event.newText().trim());
                syncEditorState();
            });
        }

        private void updateConnector(int index, java.util.function.Consumer<CompoundTag> updater) {
            ListTag connectors = tag.getList(StationStructureToolItem.KEY_CONNECTORS, Tag.TAG_COMPOUND);
            if (index < 0 || index >= connectors.size()) {
                return;
            }
            CompoundTag connector = connectors.getCompound(index).copy();
            updater.accept(connector);
            connectors.set(index, connector);
            tag.put(StationStructureToolItem.KEY_CONNECTORS, connectors);
        }

        private void updateTrigger(int index, java.util.function.Consumer<CompoundTag> updater) {
            ListTag triggers = tag.getList(StationStructureToolItem.KEY_TRIGGER_ZONES, Tag.TAG_COMPOUND);
            if (index < 0 || index >= triggers.size()) {
                return;
            }
            CompoundTag trigger = triggers.getCompound(index).copy();
            updater.accept(trigger);
            triggers.set(index, trigger);
            tag.put(StationStructureToolItem.KEY_TRIGGER_ZONES, triggers);
        }

        private void updateTriggerData(int index, java.util.function.Consumer<CompoundTag> updater) {
            updateTrigger(index, triggerTag -> {
                CompoundTag data = triggerTag.getCompound("data").copy();
                updater.accept(data);
                triggerTag.put("data", data);
            });
        }

        private String comboSelectedItem(ComboBox comboBox, dev.sixik.unigui.api.event.SelectionChangedEvent event) {
            if (!event.newSelection().isEmpty()) {
                int selectedIndex = event.newSelection().get(0);
                if (selectedIndex >= 0 && selectedIndex < comboBox.itemCount()) {
                    return comboBox.items().get(selectedIndex);
                }
            }
            return comboBox.selectedItem();
        }

        private int connectionWidth(BlockPos min, BlockPos max, Direction direction) {
            return switch (direction.getAxis()) {
                case X -> Math.max(1, max.getZ() - min.getZ() + 1);
                case Y, Z -> Math.max(1, max.getX() - min.getX() + 1);
            };
        }

        private int connectionHeight(BlockPos min, BlockPos max, Direction direction) {
            return direction.getAxis().isVertical()
                    ? Math.max(1, max.getZ() - min.getZ() + 1)
                    : Math.max(1, max.getY() - min.getY() + 1);
        }

        private Direction autoDirectionFromRootFace(BlockPos min, BlockPos max) {
            BlockPos rootMin = StationStructureEditorStick.structureMin(tag);
            BlockPos rootMax = StationStructureEditorStick.structureMax(tag);
            Direction bestDirection = Direction.NORTH;
            int bestDistance = Integer.MAX_VALUE;
            int west = Math.abs(min.getX() - rootMin.getX());
            if (west < bestDistance) { bestDistance = west; bestDirection = Direction.WEST; }
            int east = Math.abs(rootMax.getX() - max.getX());
            if (east < bestDistance) { bestDistance = east; bestDirection = Direction.EAST; }
            int down = Math.abs(min.getY() - rootMin.getY());
            if (down < bestDistance) { bestDistance = down; bestDirection = Direction.DOWN; }
            int up = Math.abs(rootMax.getY() - max.getY());
            if (up < bestDistance) { bestDistance = up; bestDirection = Direction.UP; }
            int north = Math.abs(min.getZ() - rootMin.getZ());
            if (north < bestDistance) { bestDistance = north; bestDirection = Direction.NORTH; }
            int south = Math.abs(rootMax.getZ() - max.getZ());
            if (south < bestDistance) { bestDirection = Direction.SOUTH; }
            return bestDirection;
        }

        private void syncEditorState() {
            StationStructureEditorStick.normalize(tag);
            StationEditorClientState.setEditorTag(tag);
        }

        private boolean hasTriggerDraft() {
            return tag.contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_1)
                    && tag.contains(StationStructureEditorStick.KEY_TRIGGER_DRAFT_POS_2);
        }

        private boolean hasConnectionDraft() {
            return tag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1);
        }

        private BlockPos connectionDraftMin() {
            BlockPos draftA = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)));
            BlockPos draftB = tag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)
                    ? clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)))
                    : draftA;
            return new BlockPos(Math.min(draftA.getX(), draftB.getX()), Math.min(draftA.getY(), draftB.getY()), Math.min(draftA.getZ(), draftB.getZ()));
        }

        private BlockPos connectionDraftMax() {
            BlockPos draftA = clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_1)));
            BlockPos draftB = tag.contains(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)
                    ? clamp(NbtPos.load(tag.getCompound(StationStructureEditorStick.KEY_CONNECTION_DRAFT_POS_2)))
                    : draftA;
            return new BlockPos(Math.max(draftA.getX(), draftB.getX()), Math.max(draftA.getY(), draftB.getY()), Math.max(draftA.getZ(), draftB.getZ()));
        }

        private BlockPos connectionDraftCenter(BlockPos min, BlockPos max) {
            return new BlockPos(Math.floorDiv(min.getX() + max.getX(), 2), Math.floorDiv(min.getY() + max.getY(), 2), Math.floorDiv(min.getZ() + max.getZ(), 2));
        }

        private String[] triggerNodeTypes() {
            return Arrays.stream(StationEditorNodeType.values())
                    .filter(type -> type != StationEditorNodeType.STRUCTURE && type != StationEditorNodeType.CONNECTION)
                    .map(Enum::name)
                    .toArray(String[]::new);
        }

        private String[] poolItems(String selected) {
            java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
            if (selected != null && !selected.isBlank()) {
                ids.add(selected);
            }
            ids.add("stationarenear:objects/default");
            for (ResourceLocation poolId : StationEditorClientState.poolIds()) {
                ids.add(poolId.toString());
            }
            return ids.toArray(String[]::new);
        }

        private TextField field(String value, String placeholder) {
            return new TextField(value == null ? "" : value).placeholder(placeholder).maxLength(180);
        }

        private ComboBox combo(String[] items, String selected) {
            ComboBox comboBox = new ComboBox().items(String.join(";", items)).useOverlay(overlayLayer).dropDownSameWidth().maxVisibleOptions(Math.min(6, items.length));
            int selectedIndex = 0;
            for (int i = 0; i < items.length; i++) {
                if (items[i].equalsIgnoreCase(selected)) {
                    selectedIndex = i;
                    break;
                }
            }
            comboBox.silentSelectedIndex(selectedIndex);
            return comboBox;
        }

        private Button button(String text) {
            Button button = new Button(text);
            button.layout(style -> style.size(LayoutConstraints.AUTO, 24.0f).flexGrow(0).flexShrink(0.0f));
            return button;
        }

        private Label label(String text) {
            Label label = new Label(text);
            label.layout(style -> style.size(LayoutConstraints.AUTO, 20.0f).flexGrow(0).flexShrink(0.0f));
            return label;
        }

        private BlockPos clamp(BlockPos pos) {
            BlockPos min = StationStructureEditorStick.structureMin(tag);
            BlockPos max = StationStructureEditorStick.structureMax(tag);
            return new BlockPos(
                    net.minecraft.util.Mth.clamp(pos.getX(), min.getX(), max.getX()),
                    net.minecraft.util.Mth.clamp(pos.getY(), min.getY(), max.getY()),
                    net.minecraft.util.Mth.clamp(pos.getZ(), min.getZ(), max.getZ())
            );
        }

        private BlockPos toLocal(BlockPos worldPos) {
            return worldPos.subtract(StationStructureEditorStick.structureMin(tag));
        }

        private BlockPos worldFromLocal(BlockPos localPos) {
            return clamp(StationStructureEditorStick.structureMin(tag).offset(localPos));
        }

        private String localPosText(BlockPos worldPos) {
            return posText(toLocal(worldPos));
        }

        private String[] directionNames() {
            return new String[]{"north", "south", "east", "west", "up", "down"};
        }

        private int parseIndex(String key) {
            int colon = key.indexOf(':');
            if (colon < 0) {
                return -1;
            }
            return parseInt(key.substring(colon + 1), -1);
        }

        private int parseInt(String value, int fallback) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private BlockPos parsePos(String value, BlockPos fallback) {
            String[] parts = value.trim().split("[ ,;]+");
            if (parts.length != 3) {
                return fallback;
            }
            try {
                return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private String posText(BlockPos pos) {
            return pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private static Box backgroundFrame() {
            Box frame = panelBox(0.020f, 0.024f, 0.032f, 0.98f);
            frame.layout(style -> style.align(Alignment.STRETCH, Alignment.STRETCH).flexGrow(1).flexShrink(1.0f));
            return frame;
        }

        private static Box panelBox(float r, float g, float b, float a) {
            Box box = new Box();
            box.themeEnabled(false);
            box.backgroundVisible(true);
            box.borderVisible(true);
            box.radius(4.0f);
            box.background().set(r, g, b, a);
            box.borderColor().set(0.20f, 0.28f, 0.36f, 0.75f);
            return box;
        }
    }

}
