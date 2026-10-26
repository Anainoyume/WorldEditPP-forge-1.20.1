package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TutorialImageDecodeRenderTest {
    @Test void backgroundDecoderHandlesImagesLargerThanTheLwjglThreadStack() throws Exception {
        byte[] encoded;
        try (var source = new NativeImage(512, 512, false)) {
            var random = new Random(17);
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                source.setPixelRGBA(x, y, random.nextInt() | 0xFF000000);
            encoded = source.asByteArray();
        }
        assertTrue(encoded.length > 512 * 1024, "Fixture must exceed normal thread-stack capacity");
        var worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (var decoded = TutorialImageDecoder.decode(encoded)) {
                    assertEquals(512, decoded.getWidth());
                    assertEquals(512, decoded.getHeight());
                    assertEquals(new Random(17).nextInt() | 0xFF000000, decoded.getPixelRGBA(0, 0));
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }
    @Test void shippedTutorialScreenshotsDecodeOnWorkerWithoutAnOpenGlContext() throws Exception {
        Path directory = Path.of("../src/main/resources/assets/worldeditpp/textures/tutorial");
        var worker = Executors.newSingleThreadExecutor();
        try (var files = Files.list(directory)) {
            var images = files.filter(path -> path.toString().matches(".*\\.(png|jpe?g)$")).toList();
            assertFalse(images.isEmpty());
            worker.submit(() -> {
                for (Path image : images) {
                    byte[] encoded = Files.readAllBytes(image);
                    var size = TutorialImageDecoder.inspect(new ByteArrayInputStream(encoded));
                    try (var pixels = TutorialImageDecoder.decode(encoded)) {
                        assertTrue(pixels.getWidth() > 0 && pixels.getHeight() > 0, image.toString());
                        assertEquals(size.width(), pixels.getWidth(), image.toString());
                        assertEquals(size.height(), pixels.getHeight(), image.toString());
                    }
                }
                return null;
            }).get(20, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    @Test void jpegKeepsRgbChannelsAndOpaqueAlphaOnWorker() throws Exception {
        byte[] encoded = jpegFixture();
        var worker = Executors.newSingleThreadExecutor();
        try {
            worker.submit(() -> {
                try (var pixels = TutorialImageDecoder.decode(encoded)) {
                    assertEquals(48, pixels.getWidth());
                    assertEquals(16, pixels.getHeight());
                    int red = pixels.getPixelRGBA(8, 8);
                    int green = pixels.getPixelRGBA(24, 8);
                    int blue = pixels.getPixelRGBA(40, 8);
                    assertTrue((red & 255) > 240 && (red >>> 16 & 255) < 15);
                    assertTrue((green >>> 8 & 255) > 240);
                    assertTrue((blue >>> 16 & 255) > 240 && (blue & 255) < 15);
                    assertEquals(255, red >>> 24);
                    assertEquals(255, green >>> 24);
                    assertEquals(255, blue >>> 24);
                }
                return null;
            }).get(10, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
    }

    @Test void pngStillPreservesTransparency() throws Exception {
        byte[] encoded;
        try (var source = new NativeImage(2, 1, false)) {
            source.setPixelRGBA(0, 0, 0x00112233);
            source.setPixelRGBA(1, 0, 0x80402010);
            encoded = source.asByteArray();
        }
        try (var decoded = TutorialImageDecoder.decode(encoded)) {
            assertEquals(0x00112233, decoded.getPixelRGBA(0, 0));
            assertEquals(0x80402010, decoded.getPixelRGBA(1, 0));
        }
    }

    @Test void metadataReadsOnlyHeaderAndRejectsExcessiveDimensionsBeforeDecoding() throws Exception {
        byte[] encoded = java.util.Arrays.copyOf(jpegFixture(), 1024 * 1024);
        var input = new ByteArrayInputStream(encoded);
        assertEquals(48, TutorialImageDecoder.inspect(input).width());
        assertTrue(input.available() > encoded.length / 2, "Inspect must not read the whole JPEG");
        for (int[] dimensions : new int[][] {{8193, 16}, {4097, 4097}}) {
            byte[] oversized = jpegFixture();
            boolean patched = false;
            for (int i = 0; i + 8 < oversized.length; i++) {
                if ((oversized[i] & 255) == 255 && (oversized[i + 1] & 255) == 192) {
                    oversized[i + 5] = (byte) (dimensions[1] >>> 8);
                    oversized[i + 6] = (byte) dimensions[1];
                    oversized[i + 7] = (byte) (dimensions[0] >>> 8);
                    oversized[i + 8] = (byte) dimensions[0];
                    patched = true;
                    break;
                }
            }
            assertTrue(patched, "Fixture needs a baseline JPEG frame header");
            assertThrows(IOException.class, () -> TutorialImageDecoder.inspect(new ByteArrayInputStream(oversized)));
            assertThrows(IOException.class, () -> TutorialImageDecoder.decode(oversized));
        }
        assertThrows(IOException.class, () -> TutorialImageDecoder.decode(new byte[] {(byte) 255, (byte) 216, (byte) 255}));
        assertThrows(IOException.class, () -> TutorialImageDecoder.decode(new byte[] {1, 2, 3}));
    }

    private static byte[] jpegFixture() throws IOException {
        var picture = new BufferedImage(48, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 48; x++)
            picture.setRGB(x, y, x < 16 ? 0xFF0000 : x < 32 ? 0x00FF00 : 0x0000FF);
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(picture, "JPEG", bytes));
        return bytes.toByteArray();
    }
}
