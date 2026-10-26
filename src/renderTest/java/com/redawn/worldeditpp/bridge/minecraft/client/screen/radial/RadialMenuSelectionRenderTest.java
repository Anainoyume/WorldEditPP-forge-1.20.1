package com.redawn.worldeditpp.bridge.minecraft.client.screen.radial;

import com.redawn.common.ui.radial.RadialMenuLayout;

import com.redawn.worldeditpp.bridge.minecraft.client.screen.radial.RadialMenuScreen;

import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RadialMenuSelectionRenderTest {
    @Test void initialHighlightFollowsTheSelectedValueRatherThanEntryOrder() {
        var entries = List.of(new RadialMenuScreen.Entry<>("walls", Component.literal("walls")),
                new RadialMenuScreen.Entry<>("set", Component.literal("set")));
        assertEquals(0, RadialMenuScreen.selectedIndex(entries, "walls"));
        assertEquals(1, RadialMenuScreen.selectedIndex(entries, "set"));
        assertEquals(-1, RadialMenuScreen.selectedIndex(entries, "unknown"));
        assertEquals(-1, new RadialMenuLayout(2, 32, 88, 9).hit(0, 0),
                "The initial highlight must not turn the neutral center into a hovered choice");
    }
}
