package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.worldeditpp.bridge.minecraft.api.PlatformBridge;
import com.redawn.worldeditpp.bridge.minecraft.client.mode.WorldEditPlusPlusKeyMappings;
import com.redawn.worldeditpp.bridge.minecraft.network.DismissWelcomePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/** Builds the welcome message locally and handles its two private chat links. */
public final class WelcomeMessageController {
    public static final String OPEN_COMMAND = "/worldeditpp:welcome/open_quick_start";
    public static final String DISMISS_COMMAND = "/worldeditpp:welcome/dismiss";
    private static final TextColor THEME = TextColor.fromRgb(0xFFF0A0);

    public static void show() {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        Component message = Component.translatable("message.worldeditpp.welcome",
                        WorldEditPlusPlusKeyMappings.CHANGE_MODE.get().getTranslatedKeyMessage(),
                        WorldEditPlusPlusKeyMappings.ACCESSIBILITY.get().getTranslatedKeyMessage())
                .append(quickStartLink())
                .append(Component.literal(" "))
                .append(Component.translatable("message.worldeditpp.welcome.dismiss").withStyle(style -> style
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DISMISS_COMMAND))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.worldeditpp.welcome.dismiss.hover")))));
        player.displayClientMessage(message, false);
    }

    /** Clickable quick-start link shared by the join welcome and first edit-mode guide. */
    public static Component quickStartLink() {
        return Component.translatable("message.worldeditpp.welcome.quick_start").withStyle(style -> style
                .withColor(THEME).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, OPEN_COMMAND))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("message.worldeditpp.welcome.quick_start.hover"))));
    }

    public static boolean handle(Style style) {
        ClickEvent event = style == null ? null : style.getClickEvent();
        if (event == null || event.getAction() != ClickEvent.Action.RUN_COMMAND) return false;
        if (OPEN_COMMAND.equals(event.getValue())) {
            WorldEditPlusPlusTutorial.open(null, "01_start/02_quick_start.md");
            return true;
        }
        if (DISMISS_COMMAND.equals(event.getValue())) {
            PlatformBridge.sendToServer(new DismissWelcomePayload());
            var player = Minecraft.getInstance().player;
            if (player != null) player.displayClientMessage(
                    Component.translatable("message.worldeditpp.welcome.dismissed"), false);
            return true;
        }
        return false;
    }

    private WelcomeMessageController() { }
}
