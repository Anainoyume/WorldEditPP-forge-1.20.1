package com.redawn.worldeditpp.bridge.minecraft.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

/** Wooden-axe wand with a continuously animated, localized rainbow display name. */
public final class RainbowWandItem extends AxeItem {
    private static final long COLOR_CYCLE_MILLIS = 3_000L;
    private static final float CHARACTER_HUE_STEP = 0.12F;
    private volatile RainbowNameComponent rainbowName;

    public RainbowWandItem(Tier tier, Properties properties) {
        super(tier, 6.0F, -3.2F, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        String descriptionId = getDescriptionId(stack);
        RainbowNameComponent current = rainbowName;
        if (current == null || !current.descriptionId.equals(descriptionId)) {
            synchronized (this) {
                current = rainbowName;
                if (current == null || !current.descriptionId.equals(descriptionId)) {
                    current = new RainbowNameComponent(descriptionId);
                    rainbowName = current;
                }
            }
        }
        return current;
    }

    private static final class RainbowNameComponent implements Component {
        private final String descriptionId;
        private final Component translatedName;

        private RainbowNameComponent(String descriptionId) {
            this.descriptionId = descriptionId;
            this.translatedName = Component.translatable(descriptionId);
        }

        @Override
        public Style getStyle() {
            return Style.EMPTY;
        }

        @Override
        public ComponentContents getContents() {
            return translatedName.getContents();
        }

        @Override
        public List<Component> getSiblings() {
            return List.of();
        }

        @Override
        public <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) {
            return translatedName.visit(consumer);
        }

        @Override
        public <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style inheritedStyle) {
            String localizedName = translatedName.getString();
            float timeHue = (Util.getMillis() % COLOR_CYCLE_MILLIS) / (float) COLOR_CYCLE_MILLIS;
            int characterIndex = 0;
            for (int offset = 0; offset < localizedName.length(); ) {
                int codePoint = localizedName.codePointAt(offset);
                float hue = (timeHue - characterIndex * CHARACTER_HUE_STEP) % 1.0F;
                if (hue < 0.0F) hue += 1.0F;
                int color = Mth.hsvToRgb(hue, 0.72F, 1.0F);
                Optional<T> result = consumer.accept(
                        inheritedStyle.withColor(TextColor.fromRgb(color)),
                        new String(Character.toChars(codePoint))
                );
                if (result.isPresent()) return result;
                offset += Character.charCount(codePoint);
                characterIndex++;
            }
            return Optional.empty();
        }

        @Override
        public FormattedCharSequence getVisualOrderText() {
            // Do not cache this sequence: its per-character colors animate continuously.
            return Language.getInstance().getVisualOrder(this);
        }
    }
}
