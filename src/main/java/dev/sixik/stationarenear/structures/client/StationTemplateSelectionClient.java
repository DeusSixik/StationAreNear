package dev.sixik.stationarenear.structures.client;

import dev.sixik.stationarenear.structures.data.TemplateSelectionEntry;
import dev.sixik.stationarenear.structures.network.StationStructureNetwork;
import dev.sixik.stationarenear.structures.network.packet.TemplateSelectionActionPacket;
import dev.sixik.unigui.api.layout.Alignment;
import dev.sixik.unigui.api.layout.LayoutConstraints;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.widgets.containers.Box;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.StackPanel;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Label;
import dev.sixik.unigui.widgets.interaction.Button;
import dev.sixik.unigui.widgets.interaction.Checkbox;
import dev.sixik.unigui.widgets.navigation.TabControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class StationTemplateSelectionClient {

    private StationTemplateSelectionClient() {
    }

    public static void open(List<TemplateSelectionEntry> entries) {
        StationEditorClientState.setTemplateSelections(entries);
        Minecraft minecraft = Minecraft.getInstance();
        Widget root = new TemplateMenuRoot(entries).root();
        try {
            // Ебаные маппинги. Я их рот шатал.
            Object screen = Class.forName("dev.sixik.unigui.backend.minecraft_impl.MinecraftWidgetScreen")
                    .getConstructor(Component.class, Widget.class)
                    .newInstance(Component.translatable("screen.stationarenear.template_menu.window_title"), root);
            minecraft.setScreen((Screen) screen);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            minecraft.setScreen(null);
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.literal("Unable to open template menu: " + exception.getClass().getSimpleName()), false);
            }
        }
    }

    private static final class TemplateMenuRoot {
        private final StackPanel viewport = new StackPanel();
        private final List<TemplateSelectionEntry> entries;
        private final Label status = new Label(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.status"));
        private ResourceLocation pendingDelete;

        private TemplateMenuRoot(List<TemplateSelectionEntry> entries) {
            this.entries = entries;
            viewport.addChild(backgroundFrame());
            VBox panel = new VBox();
            panel.layout(style -> style.size(620.0f, 360.0f).padding(8.0f).gap(6.0f).align(Alignment.CENTER, Alignment.CENTER));
            viewport.addChild(panel);

            Label title = new Label(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.title"));
            title.layout(style -> style.size(LayoutConstraints.AUTO, 22.0f).flexGrow(0).flexShrink(0.0f));
            status.layout(style -> style.size(LayoutConstraints.AUTO, 20.0f).flexGrow(0).flexShrink(0.0f));
            panel.addChild(title);
            panel.addChild(status);

            TabControl tabs = new TabControl();
            tabs.layout(style -> style.size(LayoutConstraints.AUTO, LayoutConstraints.AUTO).flexGrow(1.0f).flexShrink(1.0f));
            tabs.addTab(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.tab_templates"), new ScrollView(listPane()));
            tabs.silentSelectTab(0);
            panel.addChild(tabs);
        }

        private Widget root() {
            return viewport;
        }

        private VBox listPane() {
            VBox list = new VBox();
            list.layout(style -> style.size(LayoutConstraints.AUTO, LayoutConstraints.AUTO).padding(4.0f).gap(4.0f).flexGrow(1.0f).flexShrink(1.0f));
            if (entries.isEmpty()) {
                list.addChild(label(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.empty")));
                return list;
            }
            for (TemplateSelectionEntry entry : entries) {
                HBox row = new HBox();
                row.layout(style -> style.size(LayoutConstraints.AUTO, 28.0f).gap(6.0f).flexGrow(0).flexShrink(0.0f));
                Checkbox visible = new Checkbox(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.show"));
                visible.silentChecked(StationEditorClientState.templateSelectionVisible(entry.template()));
                visible.layout(style -> style.size(70.0f, 22.0f).flexGrow(0).flexShrink(0.0f));
                visible.onCheckedChanged(event -> StationEditorClientState.templateSelectionVisible(entry.template(), event.newValue()));

                Label name = label(entry.template().toString() + " [" + entry.source() + (entry.hasBounds() ? "]" : ", not spawned]"));
                name.layout(style -> style.size(LayoutConstraints.AUTO, 22.0f).flexGrow(1.0f).flexShrink(1.0f));
                Button edit = button(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.edit"));
                edit.onClick(event -> StationStructureNetwork.sendTemplateAction(new TemplateSelectionActionPacket(entry.template().toString(), "edit")));
                Button delete = button(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.delete"));
                delete.onClick(event -> {
                    if (entry.template().equals(pendingDelete)) {
                        StationStructureNetwork.sendTemplateAction(new TemplateSelectionActionPacket(entry.template().toString(), "delete"));
                        status.text("Deleting " + entry.template() + "...");
                    } else {
                        pendingDelete = entry.template();
                        delete.text(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.confirm_delete_btn"));
                        status.text(net.minecraft.client.resources.language.I18n.get("screen.stationarenear.template_menu.confirm_delete", entry.template()));
                    }
                });

                row.addChild(visible);
                row.addChild(name);
                row.addChild(edit);
                row.addChild(delete);
                list.addChild(row);
            }
            return list;
        }

        private Button button(String text) {
            Button button = new Button(text);
            button.layout(style -> style.size(104.0f, 24.0f).flexGrow(0).flexShrink(0.0f));
            return button;
        }

        private Label label(String text) {
            Label label = new Label(text);
            label.layout(style -> style.size(LayoutConstraints.AUTO, 20.0f).flexGrow(0).flexShrink(0.0f));
            return label;
        }

        private static Box backgroundFrame() {
            Box frame = new Box();
            frame.themeEnabled(false);
            frame.backgroundVisible(true);
            frame.borderVisible(true);
            frame.radius(4.0f);
            frame.background().set(0.020f, 0.024f, 0.032f, 0.98f);
            frame.borderColor().set(0.20f, 0.28f, 0.36f, 0.75f);
            frame.layout(style -> style.align(Alignment.STRETCH, Alignment.STRETCH).flexGrow(1).flexShrink(1.0f));
            return frame;
        }
    }
}
