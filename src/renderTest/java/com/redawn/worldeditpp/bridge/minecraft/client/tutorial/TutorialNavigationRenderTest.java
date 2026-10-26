package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialMarkdown;

import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialLibrary;
import com.redawn.worldeditpp.bridge.minecraft.client.tutorial.TutorialText;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialNavigationRenderTest {
    private static final ResourceLocation SOURCE = new ResourceLocation("worldeditpp:__tutorial/zh_cn/start/intro.md");

    @Test void compiledStylesNestAndDoNotLeakIntoFollowingText() {
        var text = TutorialText.parse("**__[theme:a]__**b ~~c~~ --d-- `e`", SOURCE);
        List<Style> styles = new ArrayList<>();
        text.component().getVisualOrderText().accept((index, style, codepoint) -> { styles.add(style); return true; });
        assertEquals("ab c d e", text.searchText());
        assertTrue(styles.get(0).isBold() && styles.get(0).isItalic());
        assertEquals(0xFFF0A0, styles.get(0).getColor().getValue());
        assertFalse(styles.get(1).isBold()); assertNull(styles.get(1).getColor());
        assertTrue(styles.get(3).isStrikethrough());
        assertTrue(styles.get(5).isUnderlined());
        assertEquals("tutorial:inline_code", styles.get(7).getInsertion());
    }

    @Test void wrappingPreservesLinkGlyphsAndOrdinaryTextNeverInheritsClicks() {
        var text = TutorialText.parse("[**abcdef**](start/intro.md \"[red:tip]\")plain", SOURCE);
        var splitter = new StringSplitter((cp, style) -> 1.0F);
        var lines = splitter.splitLines(text.component(), 3, Style.EMPTY);
        assertTrue(lines.size() >= 3);
        var first = splitter.componentStyleAtWidth(lines.get(0), 0);
        var second = splitter.componentStyleAtWidth(lines.get(1), 0);
        assertNotNull(first.getClickEvent());
        assertEquals(first.getClickEvent(), second.getClickEvent());
        assertTrue(first.isBold() && first.isUnderlined());
        var last = splitter.componentStyleAtWidth(lines.get(lines.size() - 1), 0);
        assertNull(last.getClickEvent()); assertFalse(last.isUnderlined());
        assertEquals("abcdefplain", text.searchText());
    }

    @Test void headingsAreBoldAndScaleTheirMeasuredHeightByLevel() {
        var spans = TutorialMarkdown.inline("[theme:Heading]");
        var first = TutorialText.heading(spans, SOURCE, 1);
        var sixth = TutorialText.heading(spans, SOURCE, 6);
        assertTrue(first.component().getSiblings().get(0).getStyle().isBold());
        assertTrue(sixth.component().getSiblings().get(0).getStyle().isBold());
        assertEquals(1.6F, first.textScale());
        assertEquals(1.0F, sixth.textScale());
    }

    @Test void targetsResolveInTheLoadedLanguageAndMissingOrInvalidTargetsDoNothing() {
        var page = new TutorialLibrary.Page(SOURCE, Component.literal("Intro"), Component.empty(), List.of());
        var library = new TutorialLibrary(List.of(page), Map.of(), "zh_cn", List.of());
        assertSame(page, library.resolvePage(SOURCE, "start/intro"));
        assertSame(page, library.resolvePage(SOURCE, "start/intro.md"));
        assertSame(page, library.resolvePage(SOURCE, SOURCE.toString()));
        for (String invalid : List.of("start/missing.md", "", "../en_us/start/intro.md", "https://example.com",
                "worldeditpp:__tutorial/en_us/start/intro.md", "start/intro.json", "other:__tutorial/zh_cn/start/intro.md"))
            assertNull(library.resolvePage(SOURCE, invalid), invalid);
    }
}
