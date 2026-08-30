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
import dev.sixik.unigui.api.render.UiRenderPolicy;
import dev.sixik.unigui.api.text.Fonts;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.text.TextOverflowMode;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftClipboardService;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftWidgetScreen;
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
import dev.sixik.stationarenear.navigation.data.SolarNavigationStationInfo;
import dev.sixik.stationarenear.ship.data.ShipSystemModule;
import dev.sixik.stationarenear.terminal.data.ShipTerminalSnapshot;
import dev.sixik.stationarenear.terminal.data.TerminalCommandCatalog;
import dev.sixik.stationarenear.terminal.data.TerminalCommandDefinition;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryKind;
import dev.sixik.stationarenear.terminal.data.TerminalHistoryLine;
import dev.sixik.stationarenear.terminal.network.TerminalNetwork;
import dev.sixik.stationarenear.terminal.shop.ShopCatalog;
import dev.sixik.stationarenear.terminal.shop.ShopItemInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

public final class RetroTerminalScreen {
    private static RetroConsole currentConsole;
    private static final double BLOCK_UI_MAX_DISTANCE_SQ = 16.0D;

    private RetroTerminalScreen() {
    }

    public static void open() {
        open(BlockPos.ZERO, ShipTerminalSnapshot.EMPTY, List.of());
    }

    public static void open(BlockPos terminalPos, ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> history) {
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

        RetroConsole console = new RetroConsole(terminalPos, snapshot, history);
        currentConsole = console;
        Widget root = root(console);
        MinecraftWidgetScreen screen = new MinecraftWidgetScreen(Component.literal("Station Terminal"), root, context) {
            @Override
            public void tick() {
                super.tick();
                if (shouldCloseBecauseTooFar(terminalPos)) {
                    onClose();
                }
            }

            @Override
            public void onClose() {
                if (currentConsole == console) {
                    currentConsole = null;
                }
                Minecraft.getInstance().setScreen(previous);
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if(keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    onClose();
                    return true;
                }

                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        console.onCloseRequested(event -> screen.onClose());

        screen.useContextScale().scaleWithMinecraftGui(false);
        screen.useSdfDefaultFont();
        screen.renderPolicy(UiRenderPolicy.continuous());
        screen.postEffect(retroEffect(scaleProvider));
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

        if (minecraft.player.getMainHandItem().is(dev.sixik.stationarenear.terminal.registry.TerminalItems.HAND_TERMINAL.get())
                || minecraft.player.getOffhandItem().is(dev.sixik.stationarenear.terminal.registry.TerminalItems.HAND_TERMINAL.get())) {
            return false;
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
                            || state.is(dev.sixik.stationarenear.terminal.registry.TerminalBlocks.TERMINAL.get())) {
                        if (minecraft.player.distanceToSqr(Vec3.atCenterOf(mutable)) <= BLOCK_UI_MAX_DISTANCE_SQ) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    public static void syncHistory(BlockPos terminalPos, List<TerminalHistoryLine> history) {
        if (currentConsole != null && currentConsole.terminalPos.equals(terminalPos)) {
            currentConsole.applyServerHistory(history);
        }
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

    static UiPostEffectChain retroEffect(UIScaleProvider scaleProvider) {
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

        private final BlockPos terminalPos;
        private final ShipTerminalSnapshot snapshot;

        private RetroConsole(BlockPos terminalPos, ShipTerminalSnapshot snapshot, List<TerminalHistoryLine> history) {
            super();
            this.terminalPos = terminalPos;
            this.snapshot = snapshot == null ? ShipTerminalSnapshot.EMPTY : snapshot;
            title("STATION TERMINAL / COMMAND CONSOLE");
            prompt("/");
            font(MinecraftFonts.defaultFace(), 18.0f);
            maxOutputLines(512);
            registerTerminalCommands();
            appendInfo("Type / or press Tab to show commands: " + TerminalCommandCatalog.summary() + ".");
            applyServerHistory(history);
        }

        @Override
        protected boolean registerBuiltInCommandsByDefault() {
            return false;
        }

        @Override
        protected void executeInput() {
            String command = inputText().trim();
            if (command.isBlank()) {
                return;
            }
            while (command.startsWith("/")) {
                command = command.substring(1).trim();
            }
            if (command.isBlank()) {
                inputText("");
                return;
            }

            if (history.isEmpty() || !history.get(history.size() - 1).equals(command)) {
                history.add(command);
            }
            historyIndex = history.size();
            inputText("");
            clearCompletions();
            TerminalNetwork.sendCommand(terminalPos, command);
        }

        private void applyServerHistory(List<TerminalHistoryLine> serverHistory) {
            super.clearOutput();
            for (TerminalHistoryLine line : serverHistory) {
                appendOutput(line.text(), toConsoleKind(line.kind()));
            }
            syncOutputScrollMetrics();
            outputScroll.scrollTo(0.0f, realOutputMaxScrollY());
            pendingOutputScrollToEnd = false;
        }

        private LineKind toConsoleKind(TerminalHistoryKind kind) {
            return switch (kind) {
                case COMMAND -> LineKind.COMMAND;
                case INFO -> LineKind.INFO;
                case WARNING -> LineKind.WARNING;
                case ERROR -> LineKind.ERROR;
                case OUTPUT -> LineKind.OUTPUT;
            };
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
            return "The service is provided by HELICORP";
        }

        private void registerTerminalCommands() {
            this.registerCommand("help", "List registered commands", (console, invocation) -> {
                console.appendInfo("Registered commands:");

                for(AdminCommandRegistry.CommandDefinition command : this.commandRegistry.commands()) {
                    console.appendOutput("  " + command.signatureWithDescription(), AdminConsole.LineKind.OUTPUT);
                }

            });

            this.registerCommand("clear", "Clear console output", (console, invocation) -> console.clearOutput());
            this.completionProvider(this::terminalCompletions);

            for (TerminalCommandDefinition command : TerminalCommandCatalog.COMMANDS) {
                registerCommand(command.command(), command.description(), switch (command.command()) {
                    case "help" -> (console, call) -> appendHelp(console);
                    case "status" -> (console, call) -> appendStatus(console);
                    case "modules" -> (console, call) -> console.appendInfo("Submit /modules to list upgrade requirements, or /modules buy <name>.");
                    case "door" -> (console, call) -> console.appendInfo("Submit /door open, /door close, or /door open <id> for station doors.");
                    case "tv" -> (console, call) -> console.appendInfo("Submit /tv text <message>, /tv ship_status, /tv ship_scan or /tv clear.");
                    case "tv_clear" -> (console, call) -> console.appendInfo("Submit /tv_clear to clear manual TV text.");
                    case "tv_pos" -> (console, call) -> console.appendInfo("Submit /tv_pos CENTER, /tv_pos TOP or /tv_pos DOWN to move manual TV text.");
                    case "tv_scale" -> (console, call) -> console.appendInfo("Submit /tv_scale <0.35-3.0> to resize manual TV text.");
                    case "objectives" -> (console, call) -> console.appendInfo("Submit /objectives to refresh current mission objectives.");
                    case "map" -> (console, call) -> console.appendInfo("Submit /map to open the docked station level map.");
                    case "stations", "scan" -> (console, call) -> appendStations(console);
                    case "store" -> (console, call) -> console.appendInfo("Submit /store to browse items. Usage: /store <index> <count> to buy.");
                    case "balance" -> (console, call) -> console.appendInfo("Submit /balance to see your current credit balance.");
                    case "clear", "cls" -> (console, call) -> clearOutput();
                    default -> (console, call) -> console.appendError("Unknown command: " + command.command());
                });
            }
        }

        private List<CompletionItem> terminalCompletions(AdminConsole console, String inputText) {
            String input = inputText == null ? "" : inputText;
            String normalized = input.startsWith("/") ? input.substring(1) : input;
            int commandStart = input.startsWith("/") ? 1 : 0;
            String lower = normalized.toLowerCase(Locale.ROOT);

            // --- door subcommands ---
            if (lower.startsWith("door ") || lower.equals("door")) {
                int subcommandStart = commandStart + 5;
                String typed = normalized.length() <= 5 ? "" : normalized.substring(5).trim().toLowerCase(Locale.ROOT);
                List<CompletionItem> items = new ArrayList<>();
                addTvCompletion(items, typed, "open", "door open", "open pressure door", subcommandStart, input.length());
                addTvCompletion(items, typed, "close", "door close", "close pressure door", subcommandStart, input.length());
                return items;
            }

            // --- tv subcommands ---
            if (lower.startsWith("tv ") || lower.equals("tv")) {
                int subcommandStart = commandStart + 3;
                String typed = normalized.length() <= 3 ? "" : normalized.substring(3).trim().toLowerCase(Locale.ROOT);
                List<CompletionItem> items = new ArrayList<>();
                addTvCompletion(items, typed, "text ", "tv text <message>", "show manual TV message", subcommandStart, input.length());
                addTvCompletion(items, typed, "ship_status", "tv ship_status", "auto-refresh ship status", subcommandStart, input.length());
                addTvCompletion(items, typed, "ship_scan", "tv ship_scan", "auto-refresh nearby station scan", subcommandStart, input.length());
                addTvCompletion(items, typed, "clear", "tv clear", "clear manual TV text", subcommandStart, input.length());
                return items;
            }

            // --- tv_pos subcommands ---
            if (lower.startsWith("tv_pos ") || lower.equals("tv_pos")) {
                int valueStart = commandStart + 7;
                String typed = normalized.length() <= 7 ? "" : normalized.substring(7).trim().toLowerCase(Locale.ROOT);
                List<CompletionItem> items = new ArrayList<>();
                addTvCompletion(items, typed, "CENTER", "tv_pos CENTER", "center manual TV text", valueStart, input.length());
                addTvCompletion(items, typed, "TOP", "tv_pos TOP", "top-align manual TV text", valueStart, input.length());
                addTvCompletion(items, typed, "DOWN", "tv_pos DOWN", "bottom-align manual TV text", valueStart, input.length());
                return items;
            }

            // --- tv_scale subcommands ---
            if (lower.startsWith("tv_scale ") || lower.equals("tv_scale")) {
                int valueStart = commandStart + 9;
                String typed = normalized.length() <= 9 ? "" : normalized.substring(9).trim().toLowerCase(Locale.ROOT);
                List<CompletionItem> items = new ArrayList<>();
                addTvCompletion(items, typed, "0.75", "tv_scale 0.75", "smaller TV text", valueStart, input.length());
                addTvCompletion(items, typed, "1.0", "tv_scale 1.0", "default TV text", valueStart, input.length());
                addTvCompletion(items, typed, "1.5", "tv_scale 1.5", "larger TV text", valueStart, input.length());
                return items;
            }

            if (lower.startsWith("modules ") || lower.equals("modules") || lower.startsWith("module ") || lower.equals("module")) {
                int prefixLen = lower.startsWith("modules") ? 7 : 6;
                String after = normalized.length() <= prefixLen ? "" : normalized.substring(prefixLen);
                String trimmedAfter = after.trim();
                String[] parts = trimmedAfter.isEmpty() ? new String[0] : trimmedAfter.split("\\s+", 2);
                String sub = parts.length > 0 ? parts[0].toLowerCase(Locale.ROOT) : "";
                int subStart = commandStart + prefixLen + (after.startsWith(" ") ? 1 : 0);
                List<CompletionItem> items = new ArrayList<>();

                if (parts.length <= 1 && !trimmedAfter.startsWith("buy ") && !trimmedAfter.startsWith("install ")) {
                    addTvCompletion(items, sub, "buy ", "modules buy <Module Name>", "purchase ship upgrade", subStart, input.length());
                }

                if (sub.equals("buy") || sub.equals("install") || trimmedAfter.startsWith("buy ") || trimmedAfter.startsWith("install ")) {
                    String moduleTyped = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "";
                    int moduleStart = subStart + sub.length() + 1;
                    for (dev.sixik.stationarenear.ship.data.ShipSystemType type : dev.sixik.stationarenear.ship.data.ShipSystemType.values()) {
                        if (type.isUpgrade()) {
                            addTvCompletion(items, moduleTyped, type.id(), "modules buy " + type.id(), type.displayName(), moduleStart, input.length());
                        }
                    }
                }
                return items;
            }

            // --- store completions ---
            if (lower.startsWith("store ") || lower.equals("store")) {
                return storeCompletions(normalized, commandStart, input);
            }

            return defaultCompletions(console, inputText);
        }

        private List<CompletionItem> storeCompletions(String normalized, int commandStart, String rawInput) {
            List<ShopItemInfo> catalog = ShopCatalog.ENTRIES;
            List<CompletionItem> items = new ArrayList<>();

            String afterStore = normalized.length() <= 6 ? "" : normalized.substring(6);
            String[] parts = afterStore.trim().split("\\s+", 2);

            boolean hasCount = parts.length >= 2 && !parts[1].isBlank();

            if (hasCount) {
                int indexArg;
                try {
                    indexArg = Integer.parseInt(parts[0].trim());
                } catch (NumberFormatException ex) {
                    return items;
                }
                if (indexArg < 0 || indexArg >= catalog.size()) {
                    return items;
                }
                ShopItemInfo entry = catalog.get(indexArg);
                int countStart = commandStart + 6 + parts[0].length() + 1;
                String typedCount = parts[1].trim().toLowerCase(Locale.ROOT);
                for (String qty : new String[]{"1", "10", "16", "32", "64"}) {
                    if (typedCount.isBlank() || qty.startsWith(typedCount)) {
                        String full = "store " + indexArg + " " + qty;
                        String desc = String.format(Locale.ROOT, "%dx %s = %.2f credits",
                                Integer.parseInt(qty), resolveDisplayName(entry.itemId()), entry.price() * Integer.parseInt(qty));
                        items.add(CompletionItem.replace(qty, full, desc, countStart, rawInput.length()));
                    }
                }
                return items;
            }

            String typedIndex = parts[0].trim().toLowerCase(Locale.ROOT);
            int itemStart = commandStart + 6;
            for (ShopItemInfo entry : catalog) {
                String idxStr = String.valueOf(entry.index());
                if (typedIndex.isBlank() || idxStr.startsWith(typedIndex)) {
                    String full = "store " + entry.index();
                    String desc = String.format(Locale.ROOT, "%s — %.2f credits",
                            resolveDisplayName(entry.itemId()), entry.price());
                    items.add(CompletionItem.replace(idxStr, full, desc, itemStart, rawInput.length()));
                }
            }
            return items;
        }

        private static String resolveDisplayName(String itemId) {
            ResourceLocation loc = new ResourceLocation(itemId);
            if (ForgeRegistries.ITEMS.containsKey(loc)) {
                return new ItemStack(ForgeRegistries.ITEMS.getValue(loc)).getHoverName().getString();
            }
            return itemId;
        }

        private static void addTvCompletion(List<CompletionItem> items, String typed, String insert, String display, String description, int start, int end) {
            if (typed.isBlank() || insert.toLowerCase(Locale.ROOT).startsWith(typed)) {
                items.add(CompletionItem.replace(insert, display, description, start, end));
            }
        }


        private void appendHelp(AdminConsole console) {
            console.appendInfo("Available terminal commands:");
            for (TerminalCommandDefinition command : TerminalCommandCatalog.COMMANDS) {
                console.appendOutput(String.format(Locale.ROOT, "  %-8s - %s", command.command(), command.description()), LineKind.OUTPUT);
            }
        }

        private void appendStatus(AdminConsole console) {
            float hp = snapshot.shipState().hp();
            float maxHp = snapshot.shipState().maxHp();
            console.appendOutput("Hull HP: " + formatNumber(hp) + " / " + formatNumber(maxHp) + " (" + formatPercent(snapshot.shipState().hpPercent()) + ")",
                    hp <= maxHp * 0.25F ? LineKind.ERROR : hp <= maxHp * 0.55F ? LineKind.WARNING : LineKind.OUTPUT);
            console.appendOutput("Integrity: " + integrityText(), snapshot.shipState().decompressed() ? LineKind.ERROR : LineKind.OUTPUT);
            console.appendOutput("Ship Mode: " + (snapshot.shipState().isDocking() ? "DOCKED" : "FLIGHT"), snapshot.shipState().isDocking() ? LineKind.INFO : LineKind.OUTPUT);
            console.appendOutput("Docking: " + (snapshot.docked() ? "DOCKED" : "IN SPACE")
                    + " / Door: " + (snapshot.doorOpen() ? "OPEN" : "SEALED")
                    + " / Hull breach: " + (snapshot.hullBreach() ? "YES" : "NO"),
                    snapshot.hullBreach() || snapshot.doorOpen() && !snapshot.docked() ? LineKind.WARNING : LineKind.OUTPUT);
            console.appendOutput("Solar speed: " + formatNumber(speed()), LineKind.OUTPUT);
        }

        private void appendModules(AdminConsole console) {
            console.appendInfo("Ship modules:");
            for (ShipSystemModule module : snapshot.shipState().modules()) {
                float durabilityRatio = module.maxDurability() <= 0.0F ? 0.0F : module.durability() / module.maxDurability();
                LineKind kind = durabilityRatio <= 0.25F ? LineKind.ERROR : durabilityRatio <= 0.55F ? LineKind.WARNING : LineKind.OUTPUT;
                console.appendOutput("  " + module.type().displayName()
                        + " | Lv." + module.level()
                        + " | DUR " + formatNumber(module.durability()) + "/" + formatNumber(module.maxDurability())
                        + " (" + formatPercent(durabilityRatio) + ")", kind);
            }
        }

        private void appendStations(AdminConsole console) {
            if (snapshot.nearbyStations().isEmpty()) {
                console.appendInfo("No known stations near current solar position.");
                return;
            }

            console.appendInfo("Nearby station IDs around solar position:");
            for (SolarNavigationStationInfo station : snapshot.nearbyStations()) {
                console.appendOutput("  " + station.code()
                        + (station.quest() ? " | QUEST" : "")
                        + " | distance " + formatNumber(station.distance()),
                        station.quest() ? LineKind.WARNING : LineKind.OUTPUT);
            }
        }

        private String integrityText() {
            if (!snapshot.boundToShip()) {
                return "SHIP NOT BOUND";
            }
            if (snapshot.shipState().decompressed()) {
                return "DECOMPRESSED / " + snapshot.shipState().decompressionReason();
            }
            return "SEALED";
        }

        private float speed() {
            float x = snapshot.navigationState().velocityX();
            float y = snapshot.navigationState().velocityY();
            return (float) Math.sqrt(x * x + y * y);
        }

        private static String formatNumber(float value) {
            return String.format(Locale.ROOT, "%.1f", value);
        }

        private static String formatPercent(float value) {
            return String.format(Locale.ROOT, "%.0f%%", Math.max(0.0F, Math.min(1.0F, value)) * 100.0F);
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
