package com.redawn.worldeditpp.bridge.forge.client;

import com.redawn.worldeditpp.bridge.forge.ForgeConfiguration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Forge 1.20.1 replacement for NeoForge's generated common-config screen. */
final class ForgeConfigurationScreen extends Screen {
    private static final int PAGE_SIZE = 4;
    private final Screen parent;
    private final List<ForgeConfigSpec.ConfigValue<?>> values = List.of(
            ForgeConfiguration.ENABLE_DEBUG_LOG);
    private final List<EditBox> inputs = new ArrayList<>();
    private final List<String> drafts = new ArrayList<>();
    private int page;
    private boolean invalid;

    ForgeConfigurationScreen(Screen parent) {
        super(Component.literal("WorldEdit++"));
        this.parent = parent;
        values.forEach(value -> drafts.add(value.get().toString()));
    }

    @Override protected void init() {
        inputs.clear();
        int left = Math.max(10, width / 2 - 145);
        for (int row = 0; row < PAGE_SIZE; row++) {
            int index = page * PAGE_SIZE + row;
            if (index >= values.size()) break;
            var field = new EditBox(font, left, 53 + row * 37, Math.min(290, width - 20), 18,
                    Component.literal(String.join(".", values.get(index).getPath())));
            field.setMaxLength(32);
            field.setValue(drafts.get(index));
            field.setResponder(text -> { drafts.set(index, text); invalid = false; });
            inputs.add(addRenderableWidget(field));
        }
        int pages = (values.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        addRenderableWidget(Button.builder(Component.literal((page + 1) + " / " + pages), button -> {
            page = (page + 1) % pages;
            rebuildWidgets();
        }).bounds(width / 2 - 145, height - 29, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(width / 2 - 45, height - 29, 90, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> save())
                .bounds(width / 2 + 55, height - 29, 90, 20).build());
    }

    private void save() {
        List<Object> parsed = new ArrayList<>();
        try {
            for (int index = 0; index < values.size(); index++) {
                var value = values.get(index);
                String text = drafts.get(index).trim();
                Object result;
                if (value instanceof ForgeConfigSpec.BooleanValue) {
                    if (!text.equals("true") && !text.equals("false")) throw new IllegalArgumentException();
                    result = Boolean.parseBoolean(text);
                } else {
                    double number = Double.parseDouble(text);
                    if (!Double.isFinite(number) || number < 0 || number > 64) throw new IllegalArgumentException();
                    result = number;
                }
                parsed.add(result);
            }
        } catch (IllegalArgumentException exception) {
            invalid = true;
            return;
        }
        for (int i = 0; i < values.size(); i++) setValue(values.get(i), parsed.get(i));
        ForgeConfiguration.save();
        onClose();
    }

    @SuppressWarnings("unchecked")
    private static <T> void setValue(ForgeConfigSpec.ConfigValue<T> value, Object parsed) { value.set((T) parsed); }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
        for (int row = 0; row < inputs.size(); row++) {
            String path = String.join(".", values.get(page * PAGE_SIZE + row).getPath());
            graphics.drawString(font, path, inputs.get(row).getX(), 42 + row * 37, 0xDDDDDD);
        }
        if (invalid) graphics.drawCenteredString(font, Component.translatable("worldeditpp.config.invalid"), width / 2, height - 43, 0xFF6666);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
