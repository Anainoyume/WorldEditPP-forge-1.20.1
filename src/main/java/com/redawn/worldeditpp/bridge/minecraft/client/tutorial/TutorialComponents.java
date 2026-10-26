package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.redawn.common.ui.tutorial.TutorialMarkdown;
import com.redawn.common.ui.tutorial.TutorialImageOptions;
import com.redawn.common.ui.tutorial.TutorialSize;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Markdown blocks become measured article components. */
public final class TutorialComponents {
    public record Context(ResourceLocation page, ResourceManager resources, TutorialImageResources images) { }

    public static TutorialContent decode(TutorialMarkdown.Block block, Context context) {
        if (block instanceof TutorialMarkdown.Paragraph paragraph) return TutorialText.compile(paragraph.spans(), context.page());
        if (block instanceof TutorialMarkdown.Heading heading)
            return new TutorialAccent(TutorialText.heading(heading.spans(), context.page(), heading.level()), false);
        if (block instanceof TutorialMarkdown.Quote quote)
            return new TutorialAccent(TutorialText.compile(quote.spans(), context.page()), true);
        if (block instanceof TutorialMarkdown.ImageRow row)
            return TutorialImages.row(row, context.page(), context.images());
        return TutorialImages.image((TutorialMarkdown.Image) block, context.page(), context.images());
    }
    /** Compatibility accessor for dimension-only callers; captions are parsed by TutorialImageOptions. */
    public static Map<String, TutorialSize> imageOptions(String options) {
        return TutorialImageOptions.parse(options).sizes();
    }
    private TutorialComponents() { }
}
