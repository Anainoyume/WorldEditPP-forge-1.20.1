package com.redawn.common.ui.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TutorialMarkdownTest {
    private static String plain(List<TutorialMarkdown.Span> spans) {
        return String.join("", spans.stream().map(TutorialMarkdown.Span::text).toList());
    }
    @Test void metadataIsOnlyReadAtTheBeginningAndIndexesIgnoreAllOtherFields() {
        String source = "\uFEFF@title **Title**\r\n@subtitle Sub\r\n@icon minecraft:stone\r\n@expanded false\r\n\r\nBody\r\n@title literal\r\n";
        var doc = TutorialMarkdown.parse(source);
        assertEquals("**Title**", doc.metadata().get("title"));
        assertEquals("Sub", doc.metadata().get("subtitle"));
        var paragraph = (TutorialMarkdown.Paragraph) doc.blocks().get(0);
        assertEquals(6, paragraph.line());
        assertEquals("Body\n@title literal", plain(paragraph.spans()));
        assertEquals(Map.of("title", "**Title**", "icon", "minecraft:stone", "expanded", "false"), TutorialMarkdown.index(source));
        assertTrue(TutorialMarkdown.index("body\n@title no").isEmpty());
    }
    @Test void boldWorksInsideAndOutsideColor() {
        for (String text : List.of("**[theme:hello]**", "[theme:**hello**]")) {
            var spans = TutorialMarkdown.inline(text);
            assertEquals("hello", plain(spans));
            assertTrue(spans.get(0).format().bold());
            assertEquals(0xFFF0A0, spans.get(0).format().color());
        }
    }
    @Test void everyPermutationOfFourStylesNestsWithColorAtAnyDepth() {
        var orders = new ArrayList<List<String>>();
        permutations(List.of("**", "__", "~~", "--"), List.of(), orders);
        assertEquals(24, orders.size());
        for (var order : orders) for (int colorDepth = 0; colorDepth <= order.size(); colorDepth++) {
            String input = "hello";
            for (int depth = order.size(); depth >= 0; depth--) {
                if (depth == colorDepth) input = "[theme:" + input + "]";
                if (depth > 0) input = order.get(depth - 1) + input + order.get(depth - 1);
            }
            var spans = TutorialMarkdown.inline(input);
            assertEquals("hello", plain(spans), input);
            var style = spans.get(0).format();
            assertTrue(style.bold() && style.italic() && style.strike() && style.underline(), input);
            assertEquals(0xFFF0A0, style.color(), input);
        }
    }
    private static void permutations(List<String> remaining, List<String> prefix, List<List<String>> result) {
        if (remaining.isEmpty()) { result.add(prefix); return; }
        for (String next : remaining) {
            var rest = new ArrayList<>(remaining); rest.remove(next);
            var head = new ArrayList<>(prefix); head.add(next);
            permutations(rest, head, result);
        }
    }
    @Test void innerColorOverridesOuterColorWithoutLeakingOtherStyles() {
        var spans = TutorialMarkdown.inline("**[red:a[#aBcDeF:__b__]c]**d");
        assertEquals("abcd", plain(spans));
        assertEquals(0xFF5555, spans.get(0).format().color());
        assertEquals(0xABCDEF, spans.get(1).format().color());
        assertTrue(spans.get(1).format().bold() && spans.get(1).format().italic());
        assertEquals(0xFF5555, spans.get(2).format().color());
        assertEquals(TutorialMarkdown.Format.PLAIN, spans.get(3).format());
    }
    @Test void inlineCodeIsLiteralAndBothBacktickDelimitersAreAccepted() {
        for (String delimiter : List.of("`", "``")) {
            var spans = TutorialMarkdown.inline(delimiter + "[red:**raw**]" + delimiter + " plain");
            assertEquals("[red:**raw**] plain", plain(spans));
            assertTrue(spans.get(0).format().code());
            assertFalse(spans.get(0).format().bold());
            assertNull(spans.get(0).format().color());
            assertFalse(spans.get(spans.size() - 1).format().code());
        }
        assertEquals("a`b", plain(TutorialMarkdown.inline("``a`b``")));
    }
    @Test void escapedAndUnmatchedSyntaxIsLiteralIncludingUnknownColors() {
        assertEquals("**literal** [red:x]", plain(TutorialMarkdown.inline("\\*\\*literal\\*\\* \\[red:x\\]")));
        for (String raw : List.of("**open", "__open", "[unknown:hello]", "[link](target", "a-b", "-x, +x", "0-12"))
            assertEquals(raw, plain(TutorialMarkdown.inline(raw)), raw);
        var text = TutorialMarkdown.inline("**[theme:__inner__]**");
        assertEquals("inner", plain(text));
        assertTrue(text.get(0).format().italic());
    }
    @Test void linksPreserveAdjacentTextAndParseStyledTipsWithoutExpandingNestedLinks() {
        var spans = TutorialMarkdown.inline("before[**[theme:go]**](start/intro.md \"__[red:tip]__ with `code`\")after");
        assertEquals("beforegoafter", plain(spans));
        var link = spans.get(1);
        assertEquals("start/intro.md", link.target().path());
        assertTrue(link.format().bold() && link.format().underline());
        assertEquals(0xFFF0A0, link.format().color());
        assertEquals("tip with code", plain(link.target().tip()));
        assertTrue(link.target().tip().get(0).format().italic());
        assertTrue(link.target().tip().get(link.target().tip().size() - 1).format().code());
        assertNull(spans.get(0).target()); assertNull(spans.get(spans.size() - 1).target());
        assertEquals("say \"hello\"\nnext", plain(TutorialMarkdown.inline("[go](intro \"say \\\"hello\\\"\\nnext\")").get(0).target().tip()));
    }
    @Test void imageBlocksRemainSeparateAndReportSourceLines() {
        var doc = TutorialMarkdown.parse("@title Media\n\nText\n\n![](pack:textures/a.png \"width=33.3% height=auto\")\n\nEnd");
        assertEquals(3, doc.blocks().size());
        var image = (TutorialMarkdown.Image) doc.blocks().get(1);
        assertEquals(5, image.line());
        assertEquals("pack:textures/a.png", image.path());
        assertEquals("width=33.3% height=auto", image.options());
        assertEquals("End", plain(((TutorialMarkdown.Paragraph) doc.blocks().get(doc.blocks().size() - 1)).spans()));
    }
    @Test void hashHeadingsUseOneThroughSixLevelsAndRemainSeparateFromParagraphs() {
        var doc = TutorialMarkdown.parse("Before\n# [theme:H1]\n## **H2**\n###### H6\n####### ordinary");
        assertEquals(5, doc.blocks().size());
        assertInstanceOf(TutorialMarkdown.Paragraph.class, doc.blocks().get(0));
        var first = assertInstanceOf(TutorialMarkdown.Heading.class, doc.blocks().get(1));
        var second = assertInstanceOf(TutorialMarkdown.Heading.class, doc.blocks().get(2));
        var sixth = assertInstanceOf(TutorialMarkdown.Heading.class, doc.blocks().get(3));
        assertEquals(1, first.level()); assertEquals(2, first.line()); assertEquals("H1", plain(first.spans()));
        assertEquals(2, second.level()); assertTrue(second.spans().get(0).format().bold());
        assertEquals(6, sixth.level());
        assertEquals("####### ordinary", plain(((TutorialMarkdown.Paragraph) doc.blocks().get(4)).spans()));
        assertEquals("#nospace", plain(((TutorialMarkdown.Paragraph) TutorialMarkdown.parse("#nospace").blocks().get(0)).spans()));
    }
    @Test void indexBodiesAreNeverParsed() {
        assertEquals(Map.of("title", "Folder"), TutorialMarkdown.index("@title Folder\n\nbody with malformed **syntax"));
    }
    @Test void keybindTokensRemainDynamicAndCanInheritStyles() {
        var span = TutorialMarkdown.inline("**[theme:[key:key.worldeditpp.change_mode]]**").get(0);
        assertTrue(span.keybind()); assertEquals("key.worldeditpp.change_mode", span.text());
        assertTrue(span.format().bold()); assertEquals(0xFFF0A0, span.format().color());
    }
    @Test void excessiveNestingIsBoundedAndUnicodeSurvives() {
        String deep = "[red:".repeat(80) + "hello\uD83D\uDE00" + "]".repeat(80);
        assertTrue(plain(TutorialMarkdown.inline(deep)).contains("hello\uD83D\uDE00"));
        assertEquals("\u4F60\u597D", plain(TutorialMarkdown.inline("**\u4F60\u597D**")));
    }
    @Test void underlineUsesDoubleHyphensAndSingleStarsRemainLiteral() {
        var spans = TutorialMarkdown.inline("\u524D--\u4E2D--\u540E");
        assertEquals("\u524D\u4E2D\u540E", plain(spans));
        assertFalse(spans.get(0).format().underline());
        assertTrue(spans.get(1).format().underline());
        assertFalse(spans.get(spans.size() - 1).format().underline());
        assertEquals("oak-log and -x to -z", plain(TutorialMarkdown.inline("oak-log and -x to -z")));
        var literalStar = TutorialMarkdown.inline("*[theme:hello]*");
        assertEquals("*hello*", plain(literalStar));
        assertFalse(literalStar.stream().anyMatch(span -> span.format().bold()));
    }
}
