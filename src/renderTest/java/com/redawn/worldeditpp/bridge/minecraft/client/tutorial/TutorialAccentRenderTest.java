package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialAccentRenderTest {
    @Test void quotePaddingIsSharedByWrappingAndLinkHitTests() {
        var text = new MeasuredText();
        var quote = new TutorialAccent(text, true);
        assertEquals(38, quote.height(114, 200));
        assertEquals(100, text.width);
        assertSame(text.link, quote.navigationAt(8, 6, 114));
        assertEquals(0, text.x); assertEquals(0, text.y); assertEquals(100, text.width);
        assertNull(quote.navigationAt(7, 6, 114));
        assertNull(quote.navigationAt(8, 5, 114));
        assertNull(quote.navigationAt(108, 6, 114));
        assertEquals(64, quote.height(64, 200));
        assertEquals("text", quote.searchText());
    }

    @Test void headingBarReservesHorizontalSpaceWithoutAddingVerticalPadding() {
        var text = new MeasuredText();
        var heading = new TutorialAccent(text, false);
        assertEquals(26, heading.height(108, 200));
        assertEquals(100, text.width);
        assertSame(text.link, heading.navigationAt(8, 0, 108));
        assertNull(heading.navigationAt(0, 0, 108));
    }

    private static final class MeasuredText implements TutorialContent {
        int width;
        double x, y;
        final TutorialNavigation.Link link = new TutorialNavigation.Link(new ResourceLocation("test:page.md"), "next.md", null);
        @Override public int height(int width, int viewport) {
            this.width = width;
            return 13 * ((200 + width - 1) / width);
        }
        @Override public void render(GuiGraphics g, int x, int y, int width, int height, int mx, int my, float tick) { }
        @Override public TutorialNavigation.Link navigationAt(double x, double y, int width) {
            this.x = x; this.y = y; this.width = width;
            return x >= 0 && x < width && y >= 0 && y < height(width, 200) ? link : null;
        }
        @Override public String searchText() { return "text"; }
    }
}
