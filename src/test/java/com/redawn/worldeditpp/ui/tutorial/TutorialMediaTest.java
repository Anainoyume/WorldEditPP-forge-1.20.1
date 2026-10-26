package com.redawn.common.ui.tutorial;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialMediaTest {
    private static TutorialMarkdown.ImageRow block(String source) {
        return assertInstanceOf(TutorialMarkdown.ImageRow.class, TutorialMarkdown.parse(source).blocks().get(0));
    }
    private static TutorialImageRow row(String source) { return TutorialImageRow.parse(block(source)); }

    @Test void quotesAndRowsInterruptParagraphsWithoutBlankLines() {
        var doc = TutorialMarkdown.parse("Before\n> **[theme:quote]** [go](page.md)\n> second\nAfter\n:::image height:64\n![alt](a.png \"title:Figure (1)\")\n::: \n# Heading\nEnd");
        assertEquals(7, doc.blocks().size());
        var quote = assertInstanceOf(TutorialMarkdown.Quote.class, doc.blocks().get(1));
        assertEquals(2, quote.line());
        assertTrue(quote.spans().get(0).format().bold());
        assertEquals(0xFFF0A0, quote.spans().get(0).format().color());
        assertEquals("page.md", quote.spans().get(quote.spans().size() - 1).target().path());
        assertInstanceOf(TutorialMarkdown.Quote.class, doc.blocks().get(2));
        var row = assertInstanceOf(TutorialMarkdown.ImageRow.class, doc.blocks().get(4));
        assertEquals(5, row.line()); assertTrue(row.closed());
        assertEquals(6, TutorialImageRow.parse(row).images().get(0).line());
        assertInstanceOf(TutorialMarkdown.Heading.class, doc.blocks().get(5));
    }

    @Test void captionsAcceptSpacesUnicodeAndBothOptionSeparators() {
        var options = TutorialImageOptions.parse("width=33.3% title:\u56FE(1) example height:auto");
        assertEquals("\u56FE(1) example", options.title());
        assertEquals(TutorialSize.parse("33.3%"), options.sizes().get("width"));
        assertEquals(TutorialSize.AUTO, options.sizes().get("height"));
        assertEquals("width: not an option", TutorialImageOptions.parse("title:'width: not an option' width 64").title());
        assertEquals("Figure 1", TutorialImageOptions.parse("height 64px title=Figure 1").title());
        assertEquals("", TutorialImageOptions.parse("title:''").title());
        assertTrue(TutorialImageOptions.parse("").sizes().isEmpty());
        for (String invalid : List.of("width=", "width=0", "height=NaN", "size=30", "title:'open", "title:'x'width:2"))
            assertThrows(IllegalArgumentException.class, () -> TutorialImageOptions.parse(invalid), invalid);
    }

    @Test void fourImagesHaveEqualCellsAndConnectorsAllowTwoUnicodeCodepoints() {
        var row = row(":::image height:80px\n![1](a.png)\n\u21E8\n![2](b.png \"width:5 height:7 title:Two\")\n![3](c.png)\n\uD83D\uDE00\u21E8\n![4](d.png)\n:::");
        assertEquals(80, row.height()); assertEquals(4, row.images().size());
        assertEquals(List.of("\u21E8", "", "\uD83D\uDE00\u21E8"), row.connectors());
        assertEquals(400, TutorialImageRow.width(500));
        var layout = row.layout(400, 24);
        assertEquals(82, layout.cellWidth()); assertEquals(24, layout.gap());
        assertEquals(400, layout.imageX(3) + layout.cellWidth());
        for (int available = 4; available < 500; available++) {
            var small = row.layout(available, 24);
            assertTrue(small.gap() >= 0 && small.cellWidth() > 0);
            assertTrue(small.imageX(3) + small.cellWidth() <= available);
        }
        assertEquals(2, row(":::image height:1.5\n![](a)\n:::").height());
    }

    @Test void malformedImageBlocksStayLocalAndReportInvalidSizesOrRelationships() {
        for (String height : List.of("", "height:autopx", "height:20%", "height:-1", "height:0", "height:NaN", "height:4097", "height:1 width:2"))
            assertThrows(IllegalArgumentException.class, () -> row(":::image " + height + "\n![](a)\n:::"), height);
        for (String body : List.of("", "->\n![](a)", "![](a)\n->", "![](a)\nabc\n![](b)",
                "![](a)\n->\n->\n![](b)", "![](a)\n".repeat(5)))
            assertThrows(IllegalArgumentException.class, () -> row(":::image height:64\n" + body + "\n:::"), body);
        assertThrows(IllegalArgumentException.class, () -> row(":::image height:64\n![](a)"));
        var doc = TutorialMarkdown.parse(":::image height:20%\n![](a)\n:::\nAfter");
        assertEquals(2, doc.blocks().size());
        assertInstanceOf(TutorialMarkdown.Paragraph.class, doc.blocks().get(doc.blocks().size() - 1));
    }

    @Test void autoHeightsFollowAllocatedWidthsAndKeepEachTextureAspectRatio() {
        var auto = row(":::image height:auto\n![](a)\n->\n![](b)\n![](c)\n:::");
        var textures = List.of(new TutorialSize.Dimensions(200, 100),
                new TutorialSize.Dimensions(100, 200), new TutorialSize.Dimensions(80, 80));
        var layout = auto.layout(TutorialImageRow.width(500), 20);
        assertEquals(120, layout.cellWidth());
        var heights = auto.imageHeights(layout.cellWidth(), textures);
        assertEquals(List.of(60, 240, 120), heights);
        assertEquals(240, heights.stream().mapToInt(Integer::intValue).max().orElseThrow());
        assertEquals(List.of(30, 120, 60), auto.imageHeights(auto.layout(220, 20).cellWidth(), textures));
        assertEquals(List.of(1, 2, 1), auto.imageHeights(1, textures));
        assertEquals(List.of(60, 60, 60), row(":::image height:60\n![](a)\n![](b)\n![](c)\n:::")
                .imageHeights(120, textures));
    }

    @Test void autoHeightIsBoundedAndRejectsInvalidTextureSizes() {
        var auto = row(":::image height:auto\n![](a)\n:::");
        assertEquals(List.of(4096), auto.imageHeights(500, List.of(new TutorialSize.Dimensions(1, Integer.MAX_VALUE))));
        assertEquals(List.of(34), auto.imageHeights(100, List.of(new TutorialSize.Dimensions(3, 1))));
        assertThrows(IllegalArgumentException.class, () -> auto.imageHeights(100, List.of()));
        assertThrows(IllegalArgumentException.class, () -> auto.imageHeights(100, List.of(new TutorialSize.Dimensions(0, 1))));
    }

    @Test void percentageShrinksEveryImageAfterAllocationAndKeepsTheGroupCentered() {
        String images = "\n![](a)\n->\n![](b)\n![](c)\n![](d)\n:::";
        var original = row(":::image height:auto" + images).layout(400, 24);
        assertEquals(original, row(":::image height:auto width:100%" + images).layout(400, 24));
        var half = row(":::image width:50% height:auto" + images);
        var layout = half.layout(400, 24);
        assertEquals(41, layout.cellWidth()); assertEquals(24, layout.gap());
        assertEquals(82, layout.left());
        assertEquals(layout.left(), 400 - layout.imageX(3) - layout.cellWidth());
        var textures = List.of(new TutorialSize.Dimensions(100, 200), new TutorialSize.Dimensions(100, 100),
                new TutorialSize.Dimensions(200, 100), new TutorialSize.Dimensions(100, 100));
        assertEquals(List.of(82, 41, 21, 41), half.imageHeights(layout.cellWidth(), textures));
        assertEquals(List.of(64, 64, 64, 64), row(":::image height:64 width:50%" + images)
                .imageHeights(layout.cellWidth(), textures));
        assertEquals(27, row(":::image height:auto width:33.3%" + images).layout(400, 24).cellWidth());
        assertEquals(1, row(":::image height:auto width:0.1%" + images).layout(400, 24).cellWidth());
        assertEquals(100, row(":::image width = 50% height : auto\n![](a)\n:::").layout(200, 24).cellWidth());
    }

    @Test void blockWidthsRejectValuesOutsideThePercentageRangeAndDuplicateOptions() {
        for (String width : List.of("0%", "-5%", "100.1%", "101%", "50", "50px", "auto", "NaN%", "Infinity%"))
            assertThrows(IllegalArgumentException.class, () -> row(":::image height:auto width:" + width + "\n![](a)\n:::"), width);
        for (String options : List.of("width:50%", "height:auto width:50% width:75%", "height:auto height:64", "height:auto unknown:1"))
            assertThrows(IllegalArgumentException.class, () -> row(":::image " + options + "\n![](a)\n:::"), options);
    }
}
