package com.redawn.worldeditpp.bridge.minecraft.client.screen;

import com.redawn.worldeditpp.bridge.minecraft.client.ui.MinecraftUiCanvas;
import com.redawn.common.ui.UiColor;
import com.redawn.common.ui.UiText;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.AccessibilityClientState;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.ClientEditModeController;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import com.redawn.worldeditpp.bridge.minecraft.mode.accessibility.BlockReachAccessibilityFeature;
import com.redawn.worldeditpp.bridge.minecraft.network.AccessibilityPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;

import java.util.List;
import java.util.Locale;

public final class AccessibilityScreen extends Screen {
    private static final double DEFAULT_BLOCK_REACH_DISTANCE = 4.5;
    private static final double MIN_FLIGHT_SPEED = 0.02;
    private static final double MAX_FLIGHT_SPEED = 0.60;
    private static final int ROW_HEIGHT = 48;
    private static final int ROW_GAP = 8;

    private final List<AccessibilityFeature> features = List.of(
            new GammaFeature(),
            new FlightSpeedFeature(),
            new BlockReachDistanceFeature()
    );

    private double blockReachDistance = DEFAULT_BLOCK_REACH_DISTANCE;

    public AccessibilityScreen() {
        super(Component.translatable("screen.worldeditpp.accessibility.title"));
    }

    @Override
    protected void init() {
        if (!ClientEditModeController.isEditing()) {
            this.onClose();
            return;
        }

        this.blockReachDistance = AccessibilityClientState.blockReachDistance;

        int contentWidth = Math.min(this.width - 40, 420);
        int left = (this.width - contentWidth) / 2;
        int top = 48;
        int controlWidth = 172;

        Component tutorial = Component.translatable("tutorial.worldeditpp.open");
        int buttonWidth = this.font.width(tutorial) + 18;
        int buttonX = Math.min(this.width - buttonWidth - 10,
                this.width / 2 + this.font.width(this.title) / 2 + 12);
        addRenderableWidget(new com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialButton(
                buttonX, 12, buttonWidth, 21, tutorial,
                () -> com.redawn.worldeditpp.bridge.minecraft.client.tutorial.WorldEditPlusPlusTutorial.open(this)));

        for (int i = 0; i < this.features.size(); i++) {
            AccessibilityFeature feature = this.features.get(i);
            int rowTop = top + i * (ROW_HEIGHT + ROW_GAP);
            feature.init(this, left, rowTop, contentWidth, controlWidth);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!ClientEditModeController.isEditing()) {
            this.onClose();
            return;
        }

        MinecraftUiCanvas canvas = new MinecraftUiCanvas(guiGraphics, this.font, this.width, this.height);
        long now = System.currentTimeMillis();
        guiGraphics.fill(0, 0, this.width, this.height, 0x1A101214);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);

        int contentWidth = Math.min(this.width - 40, 420);
        int left = (this.width - contentWidth) / 2;
        int top = 48;
        int labelWidth = Math.max(48, contentWidth - 202);

        for (int i = 0; i < this.features.size(); i++) {
            AccessibilityFeature feature = this.features.get(i);
            int rowTop = top + i * (ROW_HEIGHT + ROW_GAP);
            guiGraphics.fill(left, rowTop, left + contentWidth, rowTop + ROW_HEIGHT, 0xAA000000);
            guiGraphics.fill(left, rowTop, left + 3, rowTop + ROW_HEIGHT,
                    com.redawn.common.ui.tutorial.TutorialStyle.THEME);
            canvas.textScrolling(UiText.literal(feature.name().getString()),
                    left + 10, rowTop + 8, labelWidth,
                    new UiColor(0xFFFFFFFF), false, now);
            canvas.textScrolling(UiText.literal(feature.description().getString()),
                    left + 10, rowTop + 23, labelWidth,
                    new UiColor(0xFFAAAAAA), false, now);
        }

        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyMapping accessibilityKey = WorldEditPlusPlusKeyMappings.ACCESSIBILITY.get();
        if (accessibilityKey.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addFeatureWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
        this.addRenderableWidget(widget);
    }

    private interface AccessibilityFeature {
        Component name();

        Component description();

        void init(AccessibilityScreen screen, int left, int top, int width, int controlWidth);
    }

    private static final class GammaFeature implements AccessibilityFeature {
        @Override
        public Component name() {
            return Component.translatable("screen.worldeditpp.accessibility.gamma.name");
        }

        @Override
        public Component description() {
            return Component.translatable("screen.worldeditpp.accessibility.gamma.description");
        }

        @Override
        public void init(AccessibilityScreen screen, int left, int top, int width, int controlWidth) {
            screen.addFeatureWidget(new GammaSlider(
                    left + width - controlWidth - 10,
                    top + 12,
                    controlWidth,
                    20,
                    AccessibilityClientState.gamma
            ));
        }
    }

    private static final class GammaSlider extends AbstractSliderButton {
        private double lastSentGamma;

        private GammaSlider(int x, int y, int width, int height, double gamma) {
            super(x, y, width, height, Component.empty(), normalize(gamma));
            this.lastSentGamma = gammaFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(
                    "screen.worldeditpp.accessibility.gamma.value",
                    String.format(Locale.ROOT, "%.2f", gammaFromValue(this.value))
            ));
        }

        @Override
        protected void applyValue() {
            double gamma = gammaFromValue(this.value);
            AccessibilityClientState.setGamma(gamma);

            if (Math.abs(gamma - this.lastSentGamma) > 1.0E-6) {
                this.lastSentGamma = gamma;
                PlatformBridge.sendToServer(new AccessibilityPayload("gamma", gamma));
            }
        }

        private static double normalize(double gamma) {
            double clamped = Math.max(AccessibilityClientState.MIN_GAMMA,
                    Math.min(AccessibilityClientState.MAX_GAMMA, gamma));
            return (clamped - AccessibilityClientState.MIN_GAMMA)
                    / (AccessibilityClientState.MAX_GAMMA - AccessibilityClientState.MIN_GAMMA);
        }

        private static double gammaFromValue(double value) {
            return AccessibilityClientState.MIN_GAMMA
                    + value * (AccessibilityClientState.MAX_GAMMA - AccessibilityClientState.MIN_GAMMA);
        }
    }

    private static final class FlightSpeedFeature implements AccessibilityFeature {
        @Override
        public Component name() {
            return Component.translatable("screen.worldeditpp.accessibility.flight_speed.name");
        }

        @Override
        public Component description() {
            return Component.translatable("screen.worldeditpp.accessibility.flight_speed.description");
        }

        @Override
        public void init(AccessibilityScreen screen, int left, int top, int width, int controlWidth) {
            screen.addFeatureWidget(new FlightSpeedSlider(
                    left + width - controlWidth - 10,
                    top + 12,
                    controlWidth,
                    20,
                    AccessibilityClientState.flightSpeed
            ));
        }
    }

    private static final class FlightSpeedSlider extends AbstractSliderButton {
        private double lastSentSpeed;

        private FlightSpeedSlider(int x, int y, int width, int height, double speed) {
            super(x, y, width, height, Component.empty(), normalize(speed));
            this.lastSentSpeed = speedFromValue(this.value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(
                    "screen.worldeditpp.accessibility.flight_speed.value",
                    String.format(Locale.ROOT, "%.2f", speedFromValue(this.value))
            ));
        }

        @Override
        protected void applyValue() {
            double speed = speedFromValue(this.value);
            AccessibilityClientState.flightSpeed = speed;

            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.getAbilities().setFlyingSpeed((float) speed);
                player.onUpdateAbilities();
            }

            if (Math.abs(speed - this.lastSentSpeed) > 1.0E-6) {
                this.lastSentSpeed = speed;
                PlatformBridge.sendToServer(new AccessibilityPayload("flight_speed", speed));
            }
        }

        private static double normalize(double speed) {
            double clamped = Math.max(MIN_FLIGHT_SPEED, Math.min(MAX_FLIGHT_SPEED, speed));
            return (clamped - MIN_FLIGHT_SPEED) / (MAX_FLIGHT_SPEED - MIN_FLIGHT_SPEED);
        }

        private static double speedFromValue(double value) {
            return MIN_FLIGHT_SPEED + value * (MAX_FLIGHT_SPEED - MIN_FLIGHT_SPEED);
        }
    }

    private static final class BlockReachDistanceFeature implements AccessibilityFeature {
        private boolean enabled;

        @Override
        public Component name() {
            return Component.translatable("screen.worldeditpp.accessibility.block_reach.name");
        }

        @Override
        public Component description() {
            return Component.translatable("screen.worldeditpp.accessibility.block_reach.description");
        }

        @Override
        public void init(AccessibilityScreen screen, int left, int top, int width, int controlWidth) {
            this.enabled = AccessibilityClientState.blockReachEnabled;
            Button button = Button.builder(buttonText(), ignored -> {
                this.enabled = !this.enabled;
                AccessibilityClientState.blockReachEnabled = this.enabled;
                PlatformBridge.sendToServer(new AccessibilityPayload(BlockReachAccessibilityFeature.ENABLED_ID, this.enabled ? 1.0 : 0.0));
                ignored.setMessage(buttonText());
            }).bounds(left + width - controlWidth - 10, top + 12, 64, 20).build();
            EditBox editBox = new EditBox(Minecraft.getInstance().font, left + width - 96, top + 12, 86, 20, name());
            editBox.setMaxLength(5);
            editBox.setValue(Double.toString(screen.blockReachDistance));
            editBox.setFilter(value -> value.isEmpty() || value.matches("[0-9]{0,2}(\\.[0-9]{0,2})?"));
            editBox.setResponder(value -> {
                if (!value.isEmpty() && !value.endsWith(".")) {
                    double distance = parseReachDistance(value);
                    AccessibilityClientState.blockReachDistance = distance;
                    PlatformBridge.sendToServer(new AccessibilityPayload(BlockReachAccessibilityFeature.ID, distance));
                }
            });
            screen.addFeatureWidget(button);
            screen.addFeatureWidget(editBox);
        }

        private Component buttonText() {
            return Component.translatable(this.enabled ? "screen.worldeditpp.accessibility.enabled" : "screen.worldeditpp.accessibility.disabled");
        }

        private static double parseReachDistance(String value) {
            try {
                return Math.max(0.0, Math.min(64.0, Double.parseDouble(value)));
            } catch (NumberFormatException ignored) {
                return DEFAULT_BLOCK_REACH_DISTANCE;
            }
        }
    }
}
