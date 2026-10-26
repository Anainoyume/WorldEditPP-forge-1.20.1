package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.google.common.io.ByteStreams;
import com.mojang.blaze3d.platform.NativeImage;
import com.redawn.common.ui.tutorial.TutorialSize;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;


/** Bounded, worker-safe PNG/JPEG decoding without GL calls or temporary disk files. */
final class TutorialImageDecoder {
    static final int MAX_FILE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_EDGE = 8192;
    private static final long MAX_PIXELS = 16_777_216;

    static TutorialSize.Dimensions inspect(InputStream stream) throws IOException {
        var input = new PushbackInputStream(ByteStreams.limit(stream, MAX_FILE_BYTES), 3);
        byte[] signature = input.readNBytes(3);
        input.unread(signature);
        if (isJpeg(signature)) {
            try (var data = new MemoryCacheImageInputStream(input)) {
                var reader = jpegReader();
                try {
                    reader.setInput(data, true, true);
                    return dimensions(reader.getWidth(0), reader.getHeight(0));
                } finally { reader.dispose(); }
            }
        }
        return inspectPng(input);
    }

    static NativeImage decode(byte[] encoded) throws IOException {
        if (encoded.length > MAX_FILE_BYTES) throw new IOException("Tutorial image exceeds 64 MiB");
        if (isJpeg(encoded)) return decodeJpeg(encoded);
        inspectPng(new ByteArrayInputStream(encoded));
        // NativeImage.read(byte[]) puts the entire PNG on LWJGL's small thread stack.
        var buffer = org.lwjgl.system.MemoryUtil.memAlloc(encoded.length);
        try {
            buffer.put(encoded).flip();
            return NativeImage.read(buffer);
        } finally { org.lwjgl.system.MemoryUtil.memFree(buffer); }
    }

    private static NativeImage decodeJpeg(byte[] encoded) throws IOException {
        // NativeImage.read validates a PNG signature even though its STB backend supports JPEG.
        try (var data = new MemoryCacheImageInputStream(new ByteArrayInputStream(encoded))) {
            var reader = jpegReader();
            try {
                reader.setInput(data, true, true);
                var size = dimensions(reader.getWidth(0), reader.getHeight(0));
                var image = reader.read(0);
                if (image.getWidth() != size.width() || image.getHeight() != size.height())
                    throw new IOException("JPEG dimensions changed during decoding");
                var pixels = new NativeImage(size.width(), size.height(), false);
                try {
                    int[] row = new int[size.width()];
                    for (int y = 0; y < size.height(); y++) {
                        image.getRGB(0, y, size.width(), 1, row, 0, size.width());
                        for (int x = 0; x < size.width(); x++) {
                            int argb = row[x];
                            // NativeImage 1.20.1 stores packed ABGR; Java images expose ARGB.
                            int abgr = 0xFF000000 | (argb & 0xFF) << 16 | (argb & 0xFF00) | (argb >>> 16 & 0xFF);
                            pixels.setPixelRGBA(x, y, abgr);
                        }
                    }
                    return pixels;
                } catch (Throwable failure) {
                    pixels.close();
                    throw failure;
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }

    private static TutorialSize.Dimensions inspectPng(InputStream input) throws IOException {
        try (var data = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReadersByFormatName("PNG");
            if (!readers.hasNext()) throw new IOException("No PNG decoder available");
            var reader = readers.next();
            try {
                reader.setInput(data, true, true);
                return dimensions(reader.getWidth(0), reader.getHeight(0));
            } finally { reader.dispose(); }
        }
    }

    private static ImageReader jpegReader() throws IOException {
        var readers = ImageIO.getImageReadersByFormatName("JPEG");
        if (!readers.hasNext()) throw new IOException("No JPEG decoder available");
        return readers.next();
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
    }

    private static TutorialSize.Dimensions dimensions(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE || (long) width * height > MAX_PIXELS)
            throw new IOException("Tutorial image exceeds supported dimensions: " + width + "x" + height);
        return new TutorialSize.Dimensions(width, height);
    }

    private TutorialImageDecoder() { }
}
