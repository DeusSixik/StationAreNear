package dev.sixik.stationarenear.terminal.client;

import dev.sixik.unigui.api.core.UIScaleProvider;
import dev.sixik.unigui.api.core.UnityLikeUIScaleProvider;
import dev.sixik.unigui.api.debug.DebugFlags;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Alignment;
import dev.sixik.unigui.api.layout.EdgeInsets;
import dev.sixik.unigui.api.layout.LayoutConstraints;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.posteffect.UiPostEffectChain;
import dev.sixik.unigui.api.posteffect.UiPostEffectPass;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.UiRenderPolicy;
import dev.sixik.unigui.api.text.Fonts;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.text.TextOverflowMode;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.backend.minecraft.MinecraftClipboardService;
import dev.sixik.unigui.backend.minecraft.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft.MinecraftWidgetScreen;
import dev.sixik.unigui.impl.core.DefaultUIContext;
import dev.sixik.unigui.widgets.containers.Box;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.StackPanel;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Label;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.interaction.AdminCommandRegistry;
import dev.sixik.unigui.widgets.interaction.AdminConsole;
import dev.sixik.unigui.widgets.interaction.Button;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class RetroTerminalScreen {
private static final List<String> SAMPLE_PLAYERS = List.of("Steve", "Alex", "Sixik", "Engineer", "Navigator");
    private static final List<String> COORDINATES = List.of("0", "64", "128", "-128", "256", "~", "~-1", "~1");

    private RetroTerminalScreen() {
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        Screen previous = minecraft.screen;

        DefaultUIContext context = new DefaultUIContext(new MinecraftClipboardService());
        UnityLikeUIScaleProvider scaleProvider = new UnityLikeUIScaleProvider()
                .referenceResolution(1920.0f, 1080.0f)
                .matchBalanced()
                .scaleRange(0.75f, 12.0f)
                .userScale(3.0f);
        context.scaleProvider(scaleProvider);
//        context.debugFlags(DebugFlags.ALL);

        RetroConsole console = new RetroConsole();
        Widget root = root(console);
        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal("Station Terminal"), root, context) {
            @Override
            public void onClose() {
                Minecraft.getInstance().setScreen(previous);
            }
        };
        console.onCloseRequested(event -> screen.onClose());

        screen.useContextScale().scaleWithMinecraftGui(false);
        screen.useSdfDefaultFont();
        screen.renderPolicy(UiRenderPolicy.continuous());
        screen.postEffect(retroEffect(scaleProvider));
        minecraft.setScreen(screen);
    }

    private static Widget root(RetroConsole console) {
        StackPanel stack = new StackPanel();
        stack.layout(style -> style.sizePercent(100.0f, 100.0f).overflow(Overflow.HIDDEN));

        Box background = new Box()
                .themeEnabled(false)
                .backgroundVisible(true)
                .borderVisible(false)
                .background(MutableColor.rgba(0.004f, 0.017f, 0.006f, 1.0f));
        background.layout(style -> style.sizePercent(100.0f, 100.0f).align(Alignment.STRETCH, Alignment.STRETCH));

        Box vignette = new Box()
                .themeEnabled(false)
                .backgroundVisible(true)
                .borderVisible(true)
                .radius(0.0f)
                .background(MutableColor.rgba(0.020f, 0.055f, 0.020f, 0.42f))
                .border(MutableColor.rgba(0.52f, 0.72f, 0.38f, 0.35f));
        vignette.layout(style -> style.sizePercent(100.0f, 100.0f).margin(0.0f).align(Alignment.STRETCH, Alignment.STRETCH));

        console.layout(style -> style.sizePercent(100.0f, 100.0f)
                .margin(0.0f)
                .align(Alignment.STRETCH, Alignment.STRETCH)
                .padding(28.0f, 24.0f)
                .overflow(Overflow.VISIBLE));

        stack.addChild(background);
        stack.addChild(vignette);
        stack.addChild(console);
        return new OverlayLayer(stack);
    }

    private static UiPostEffectChain retroEffect(UIScaleProvider scaleProvider) {
        UiPostEffectPass terminal = UiPostEffectPass.shader("stationarenear:retro_terminal")
                .uniforms(uniforms -> uniforms
                        .vec3("fontColor", 0.78f, 1.00f, 0.43f)
                        .vec3("backgroundColor", 0.008f, 0.044f, 0.010f)
                        .floatValue("chromaColor", 0.42f)
                        .floatValue("staticNoise", 0.060f)
                        .floatValue("horizontalSyncStrength", 0.095f)
                        .floatValue("horizontalSyncFrequency", 0.070f)
                        .vec2("jitter", 0.0010f, 0.00028f)
                        .floatValue("glowingLine", 0.18f)
                        .floatValue("flickering", 0.060f)
                        .floatValue("ambientLight", 0.095f)
                        .floatValue("pixelHeight", 5.40f)
                        .boolValue("pixelization", false)
                        .floatValue("rbgSplit", 0.035f)
                        .floatValue("scanlineStrength", 0.78f)
                        .floatValue("phosphorGlow", 0.34f)
                        .floatValue("glitchStrength", 0.024f)
                        .floatValue("glitchFrequency", 1.15f)
                        .floatValue("glitchBandHeight", 0.038f)
                        .floatValue("rollingInterference", 0.075f)
                        .floatValue("noiseFrameRate", 24.0f)
                        .floatValue("glitchFrameRate", 18.0f)
                        .floatSupplier("UiScale", () -> UIScaleProvider.sanitize(
                                scaleProvider == null ? 1.0f : scaleProvider.scale())));

        UiPostEffectPass bloom = UiPostEffectPass.shader("stationarenear:retro_terminal_bloom")
                .uniforms(uniforms -> uniforms
                        .floatValue("bloomStrength", 1.12f)
                        .floatValue("bloomRadius", 2.75f)
                        .floatValue("threshold", 0.28f)
                        .vec3("bloomTint", 0.86f, 1.00f, 0.58f));

        UiPostEffectPass frame = UiPostEffectPass.shader("stationarenear:retro_terminal_frame")
                .uniforms(uniforms -> uniforms
                        .floatValue("screenCurvature", 0.145f)
                        .vec3("frameColor", 0.22f, 0.25f, 0.16f));

        return UiPostEffectChain.of(List.of(terminal, bloom, frame));
    }

    private static final class RetroConsole extends AdminConsole {
        private static final MutableColor GREEN = MutableColor.rgba(0.54f, 1.00f, 0.58f, 1.00f);
        private static final MutableColor DIM_GREEN = MutableColor.rgba(0.24f, 0.58f, 0.30f, 1.00f);
        private static final MutableColor PANEL = MutableColor.rgba(0.004f, 0.020f, 0.010f, 0.86f);
        private static final MutableColor PANEL_HOT = MutableColor.rgba(0.020f, 0.135f, 0.060f, 0.90f);
        private static final MutableColor BORDER = MutableColor.rgba(0.28f, 0.86f, 0.42f, 0.65f);
        private static final MutableColor ERROR = MutableColor.rgba(1.00f, 0.36f, 0.28f, 1.00f);
        private static final MutableColor WARNING = MutableColor.rgba(1.00f, 0.82f, 0.36f, 1.00f);

        private RetroConsole() {
            super();
            title("STATION TERMINAL / COMMAND CONSOLE");
            prompt("/");
            font(MinecraftFonts.defaultFace(), 18.0f);
            maxOutputLines(512);
            registerTerminalCommands();
            appendInfo("Type / or press Tab to show available commands.");
        }

        @Override
        public RetroConsole clearOutput() {
            super.clearOutput();
            syncOutputScrollMetrics();
            outputScroll.scrollTo(0.0f, 0.0f);
            pendingOutputScrollToEnd = false;
            return this;
        }

        @Override
        public void arrange(RectView bounds) {
            super.arrange(bounds);
            clampOutputScroll();
        }

        @Override
        protected void rebuildOutputRows() {
            super.rebuildOutputRows();
            syncOutputScrollMetrics();
        }

        private void syncOutputScrollMetrics() {
            outputScroll.contentHeight(Math.max(1.0f, realOutputContentHeight()));
        }

        private void clampOutputScroll() {
            syncOutputScrollMetrics();

            float maxScrollY = realOutputMaxScrollY();
            if (lines.isEmpty() || maxScrollY <= 0.0f) {
                outputScroll.scrollTo(0.0f, 0.0f);
                pendingOutputScrollToEnd = false;
                return;
            }

            if (outputScroll.scrollY() > maxScrollY) {
                outputScroll.scrollTo(0.0f, maxScrollY);
                pendingOutputScrollToEnd = false;
            }
        }

        private float realOutputMaxScrollY() {
            return Math.max(0.0f, realOutputContentHeight() - outputScroll.layoutBounds().height());
        }

        private float realOutputContentHeight() {
            if (lines.isEmpty()) {
                return 0.0f;
            }

            return lines.size() * lineHeight() + Math.max(0, lines.size() - 1) * outputList.spacing();
        }

        @Override
        protected void configureRoot() {
            themeEnabled(false);
            backgroundVisible(true);
            borderVisible(true);
            radius(0.0f);
            background().set(PANEL);
            borderColor().set(BORDER);
            borderWidth(1.0f);
            layout(style -> style.sizePercent(100.0f, 100.0f).padding(18.0f, 16.0f).overflow(Overflow.VISIBLE));
        }

        @Override
        protected void configureBody(VBox body) {
            body.spacing(8.0f);
            body.layout(style -> style.sizePercent(100.0f, 100.0f).flexGrow(1.0f).flexShrink(1.0f));
        }

        @Override
        protected void configureHeader(HBox header, Button closeButton) {
            header.spacing(10.0f);
            header.layout(style -> style.height(28.0f).flexGrow(0.0f).flexShrink(0.0f).alignItems(Align.CENTER));
            configureTitleLabel(titleLabel);
            configureCloseButton(closeButton);
            header.addChild(titleLabel);
            header.addChild(closeButton);
        }

        @Override
        protected void configureTitleLabel(Label titleLabel) {
            titleLabel.richText(titleRichText);
            titleLabel.font(font, fontSize + 1.0f);
            titleLabel.color(GREEN);
            titleLabel.noWrap();
            titleLabel.overflowMode(TextOverflowMode.CLIP);
            titleLabel.layout(style -> style.height(28.0f).flexGrow(1.0f).flexShrink(1.0f));
        }

        @Override
        protected void configureCloseButton(Button closeButton) {
            closeButton.text("EXIT");
            closeButton.themeEnabled(false);
            closeButton.background().set(0.010f, 0.045f, 0.020f, 0.95f);
            closeButton.borderColor().set(BORDER);
            closeButton.textColor().set(GREEN);
            closeButton.radius(2.0f);
            closeButton.textPadding(10.0f, 3.0f);
            closeButton.layout(style -> style.size(56.0f, 22.0f).flexGrow(0.0f).flexShrink(0.0f));
            closeButton.onClick(event -> requestClose());
        }

        @Override
        protected void configureOutputScroll(ScrollView outputScroll) {
            outputScroll.scrollStep(lineHeight());
            outputScroll.scrollbarGap(3.0f);
            outputScroll.scrollbarTrackColor().set(0.0f, 0.0f, 0.0f, 0.42f);
            outputScroll.scrollbarThumbColor().set(DIM_GREEN);
            outputScroll.layout(style -> style.size(LayoutConstraints.AUTO, LayoutConstraints.AUTO)
                    .overflowX(Overflow.HIDDEN)
                    .overflowY(Overflow.AUTO)
                    .flexGrow(1.0f)
                    .flexShrink(1.0f));
        }

        @Override
        protected void configureInputRow(HBox inputRow) {
            inputRow.spacing(8.0f);
            inputRow.layout(style -> style.height(30.0f).flexGrow(0.0f).flexShrink(0.0f).alignItems(Align.CENTER));
            configurePromptLabel(promptLabel);
            configureInputField(inputField);
            inputRow.addChild(promptLabel);
            inputRow.addChild(inputField);
        }

        @Override
        protected void configurePromptLabel(Label promptLabel) {
            promptLabel.richText(promptRichText);
            promptLabel.focusTarget(inputField);
            promptLabel.font(font, fontSize + 1.0f);
            promptLabel.color(GREEN);
            promptLabel.layout(style -> style.width(18.0f).height(30.0f).flexGrow(0.0f).flexShrink(0.0f));
        }

        @Override
        protected void configureInputField(ConsoleInputField inputField) {
            inputField.placeholder("enter command...");
            inputField.font(font, fontSize + 1.0f);
            inputField.visualOnlyTextChanges(true);
            inputField.themeEnabled(false);
            inputField.textColor().set(GREEN);
            inputField.placeholderColor().set(DIM_GREEN);
            inputField.caretColor().set(0.72f, 1.0f, 0.70f, 1.0f);
            inputField.background().set(0.000f, 0.014f, 0.006f, 0.95f);
            inputField.borderColor().set(BORDER);
            inputField.radius(2.0f);
            inputField.layout(style -> style.height(30.0f).flexGrow(1.0f).flexShrink(1.0f));
        }

        @Override
        protected void configureCompletionPanel(Box completionPanel) {
            super.configureCompletionPanel(completionPanel);
            completionPanel.themeEnabled(false);
            completionPanel.background().set(0.003f, 0.020f, 0.010f, 0.98f);
            completionPanel.borderColor().set(BORDER);
            completionPanel.radius(2.0f);
            completionPanel.layout(style -> style.padding(2.0f).overflow(Overflow.HIDDEN).flexGrow(0.0f).flexShrink(0.0f));
        }

        @Override
        protected void configureCompletionPopup(Popup completionPopup) {
            super.configureCompletionPopup(completionPopup);
            completionPopup.padding(EdgeInsets.ZERO);
            completionPopup.offset(0.0f, 6.0f);
        }

        @Override
        protected CompletionRow completionRow(int index, CompletionItem item) {
            return new RetroCompletionRow(this, index, item);
        }

        @Override
        protected void configureOutputLabel(Label label, ConsoleLine line) {
            super.configureOutputLabel(label, line);
            label.font(font, fontSize);
            label.color(colorFor(line.kind()));
            label.marqueeSpeed(30.0f);
        }

        @Override
        protected String initialOutputLine() {
            return "The service is provided by Helicorp";
        }

        private void registerTerminalCommands() {
            registerCommand("status", "Print ship and terminal status", (console, call) -> {
                console.appendInfo("Power: NOMINAL / Link: STABLE / Command bus: READY");
                console.appendOutput("CPU 18% | BUS 04 | HEAP OK | SIGNAL 91%", LineKind.OUTPUT);
            });
            registerCommand("scan", "Scan nearby objects", (console, call) -> {
                console.appendInfo("Scan result:");
                console.appendOutput("  STATION A-17     distance  4200m    dock: yes", LineKind.OUTPUT);
                console.appendOutput("  ASTEROID FIELD   distance  1730m    hazard: medium", LineKind.WARNING);
                console.appendOutput("  BEACON ECHO      distance  6200m    quest marker", LineKind.OUTPUT);
            });
            registerCommand("players", "List known player targets", (console, call) ->
                    console.appendOutput(String.join(", ", SAMPLE_PLAYERS), LineKind.OUTPUT));
            registerCommand(AdminCommandRegistry.command("teleport", "Teleport player to coordinates")
                    .argument("player", "Known player", () -> SAMPLE_PLAYERS)
                    .customArgument("x", "X coordinate", COORDINATES)
                    .customArgument("y", "Y coordinate", COORDINATES)
                    .customArgument("z", "Z coordinate", COORDINATES)
                    .executor((console, call) -> {
                        List<String> args = call.arguments();
                        if (args.size() < 4) {
                            console.appendError("Usage: teleport <player> <x> <y> <z>");
                            return;
                        }
                        console.appendInfo("Teleport queued: " + args.get(0) + " -> "
                                + args.get(1) + " " + args.get(2) + " " + args.get(3));
                    })
                    .build());
        }

        private MutableColor colorFor(LineKind kind) {
            return switch (kind) {
                case COMMAND -> MutableColor.rgba(0.72f, 1.00f, 0.72f, 1.00f);
                case INFO -> GREEN;
                case WARNING -> WARNING;
                case ERROR -> ERROR;
                case OUTPUT -> MutableColor.rgba(0.42f, 0.90f, 0.48f, 1.00f);
            };
        }

        private static final class RetroCompletionRow extends CompletionRow {
            private RetroCompletionRow(AdminConsole owner, int index, CompletionItem item) {
                super(owner, index, item);
                themeEnabled(false);
                radius(1.0f);
                borderVisible(false);
                layout(style -> style.height(COMPLETION_ROW_HEIGHT).padding(8.0f, 2.0f).flexGrow(0.0f).flexShrink(0.0f));
                displayLabel.color(GREEN);
                descriptionLabel.color(DIM_GREEN);
            }

            @Override
            protected void updateVisualState() {
                boolean active = selected || rowHovered || hovered();
                if (pressed) background().set(PANEL_HOT);
                else if (selected) background().set(0.050f, 0.260f, 0.105f, 0.94f);
                else if (active) background().set(0.024f, 0.145f, 0.062f, 0.92f);
                else background().set(0.003f, 0.026f, 0.012f, 0.88f);

                displayLabel.color(active ? MutableColor.rgba(0.82f, 1.00f, 0.78f, 1.00f) : GREEN);
                descriptionLabel.color(active ? MutableColor.rgba(0.78f, 1.00f, 0.72f, 1.00f) : DIM_GREEN);
                descriptionLabel.marqueeActive(active);
            }
        }
    }
}

