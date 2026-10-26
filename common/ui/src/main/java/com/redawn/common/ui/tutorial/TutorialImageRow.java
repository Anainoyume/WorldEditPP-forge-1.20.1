package com.redawn.common.ui.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Divides row width first, then resolves automatic heights from each texture's aspect ratio. */
public record TutorialImageRow(int height, double widthPercent, List<TutorialMarkdown.Image> images, List<String> connectors) {
    // Zero represents auto; explicit pixel heights remain positive.
    private static final Pattern HEIGHT = Pattern.compile("height\\s*[:=]\\s*(auto|[0-9]+(?:\\.[0-9]+)?(?:px)?)");
    private static final Pattern OPTION = Pattern.compile("(height|width)\\s*[:=]\\s*(\\S+)");
    private static final Pattern PERCENT = Pattern.compile("[0-9]+(?:\\.[0-9]+)?%");
    public TutorialImageRow { images = List.copyOf(images); connectors = List.copyOf(connectors); }

    public static TutorialImageRow parse(TutorialMarkdown.ImageRow row) {
        if (!row.closed()) throw new IllegalArgumentException("Unclosed image block at line " + row.line());
        String heightOption = null, widthOption = null;
        String options = row.options();
        for (int cursor = 0; cursor < options.length();) {
            if (Character.isWhitespace(options.charAt(cursor))) { cursor++; continue; }
            var option = OPTION.matcher(options).region(cursor, options.length());
            if (!option.lookingAt()) throw new IllegalArgumentException("Invalid image block options: " + options);
            if (option.group(1).equals("height")) {
                if (heightOption != null) throw new IllegalArgumentException("Duplicate image block height");
                heightOption = option.group(2);
            } else {
                if (widthOption != null) throw new IllegalArgumentException("Duplicate image block width");
                widthOption = option.group(2);
            }
            cursor = option.end();
        }
        var height = HEIGHT.matcher("height:" + heightOption);
        if (!height.matches()) throw new IllegalArgumentException("Image block requires height:<pixels> or height:auto");
        boolean automatic = height.group(1).equals("auto");
        double pixels = automatic ? 0 : Double.parseDouble(height.group(1).replace("px", ""));
        if (!automatic && (pixels < 1 || pixels > 4096))
            throw new IllegalArgumentException("Image block height must be between 1 and 4096 pixels");
        double percent = 100;
        if (widthOption != null) {
            if (!PERCENT.matcher(widthOption).matches())
                throw new IllegalArgumentException("Image block width must be a percentage");
            percent = Double.parseDouble(widthOption.substring(0, widthOption.length() - 1));
            if (!(percent > 0 && percent <= 100))
                throw new IllegalArgumentException("Image block width must be greater than 0% and at most 100%");
        }
        List<TutorialMarkdown.Image> images = new ArrayList<>();
        List<String> connectors = new ArrayList<>();
        String pending = null;
        for (int i = 0; i < row.body().size(); i++) {
            String line = row.body().get(i).strip();
            if (line.isEmpty()) continue;
            var image = TutorialMarkdown.image(line, row.line() + i + 1);
            if (image != null) {
                if (images.size() == 4) throw new IllegalArgumentException("Image block supports at most four images");
                if (!images.isEmpty()) connectors.add(pending == null ? "" : pending);
                pending = null;
                images.add(image);
            } else {
                if (images.isEmpty() || pending != null || line.codePointCount(0, line.length()) > 2)
                    throw new IllegalArgumentException("Expected an image or a connector of at most two characters at line " + (row.line() + i + 1));
                pending = line;
            }
        }
        if (images.isEmpty() || pending != null) throw new IllegalArgumentException("Image block must start and end with an image");
        return new TutorialImageRow((int) Math.round(pixels), percent, images, connectors);
    }

    public static int width(int articleWidth) { return Math.max(1, (int) Math.floor(articleWidth * 0.8)); }

    public List<Integer> imageHeights(int cellWidth, List<TutorialSize.Dimensions> textures) {
        if (textures.size() != images.size() || cellWidth < 1)
            throw new IllegalArgumentException("Image layout requires one texture size per image and a positive width");
        return textures.stream().map(size -> {
            if (size.width() < 1 || size.height() < 1) throw new IllegalArgumentException("Invalid texture dimensions");
            return height > 0 ? height : (int) Math.max(1, Math.min(4096,
                    Math.ceil((double) cellWidth * size.height() / size.width())));
        }).toList();
    }

    public record Layout(int cellWidth, int gap, int left, int count) {
        public int imageX(int index) { return left + index * (cellWidth + gap); }
    }
    public Layout layout(int width, int preferredGap) {
        int count = images.size();
        int gap = count == 1 ? 0 : Math.min(Math.max(0, preferredGap), Math.max(0, (width - count) / (count - 1)));
        int cell = Math.max(1, (width - gap * (count - 1)) / count);
        cell = Math.max(1, (int) Math.floor(cell * widthPercent / 100));
        int used = cell * count + gap * (count - 1);
        return new Layout(cell, gap, Math.max(0, (width - used) / 2), count);
    }
}
