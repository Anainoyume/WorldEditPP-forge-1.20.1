package com.redawn.common.ui.tutorial;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TutorialImageCacheTest {
    private static final class Pixels implements AutoCloseable {
        int closed;
        @Override public void close() { assertEquals(0, closed++); }
    }
    private static class Backend implements TutorialImageCache.Backend<String, Pixels, String> {
        final List<String> inspected = new ArrayList<>(), decoded = new ArrayList<>(), uploaded = new ArrayList<>();
        final List<String> released = new ArrayList<>(), failures = new ArrayList<>();
        final List<Pixels> pixels = new ArrayList<>();
        boolean failUpload;
        @Override public TutorialSize.Dimensions inspect(String key) throws Exception {
            inspected.add(key);
            if (key.equals("missing")) throw new IOException("missing PNG");
            return new TutorialSize.Dimensions(4, 4);
        }
        @Override public Pixels decode(String key) {
            decoded.add(key); var value = new Pixels(); pixels.add(value); return value;
        }
        @Override public String upload(String key, Pixels value) throws Exception {
            assertEquals(0, value.closed);
            if (failUpload) throw new IOException("upload failed");
            uploaded.add(key); return key;
        }
        @Override public void release(String texture) { released.add(texture); }
        @Override public void failed(String key, Exception failure) { failures.add(key); }
    }
    private static final class Fixture implements AutoCloseable {
        final Backend backend = new Backend();
        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        long now;
        final TutorialImageCache<String, Pixels, String> cache;
        Fixture(long budget) { cache = new TutorialImageCache<>(backend, tasks::add, () -> now, 100, budget); }
        void frame(Map<String, Integer> requests) {
            cache.beginFrame(); requests.forEach(cache::request); cache.endFrame();
        }
        void visible(String key) { frame(Map.of(key, TutorialImageCache.VISIBLE)); }
        void runWorker() { assertFalse(tasks.isEmpty()); tasks.remove().run(); }
        void load(String key) { visible(key); runWorker(); visible(key); }
        @Override public void close() { cache.close(); }
    }
    @Test void indexingAndMeasuringDoNotReadImagesAndOnlyCurrentPageIsRequested() {
        try (var f = new Fixture(128)) {
            assertEquals(new TutorialSize.Dimensions(16, 9), f.cache.dimensions("unopened"));
            assertNull(f.cache.texture("unopened"));
            assertTrue(f.backend.inspected.isEmpty()); assertTrue(f.tasks.isEmpty());
            f.load("selected");
            assertEquals(List.of("selected"), f.backend.inspected);
            assertEquals(List.of("selected"), f.backend.decoded);
            assertEquals(List.of("selected"), f.backend.uploaded);
        }
    }
    @Test void offscreenHeadersDetermineLayoutWithoutDecodingOrUploading() {
        try (var f = new Fixture(128)) {
            f.frame(Map.of("far", TutorialImageCache.METADATA)); f.runWorker();
            f.frame(Map.of("far", TutorialImageCache.METADATA));
            assertEquals(new TutorialSize.Dimensions(4, 4), f.cache.dimensions("far"));
            assertEquals(1, f.cache.revision());
            assertTrue(f.backend.decoded.isEmpty()); assertTrue(f.tasks.isEmpty());
            f.load("far");
            assertEquals(List.of("far"), f.backend.inspected);
            assertEquals(1, f.backend.pixels.get(0).closed);
        }
    }
    @Test void visibleImagesTakePriorityAndUploadsAreSpreadAcrossFrames() {
        try (var f = new Fixture(256)) {
            f.cache.beginFrame();
            f.cache.request("far", TutorialImageCache.METADATA);
            f.cache.request("near", TutorialImageCache.NEARBY);
            f.cache.request("visible", TutorialImageCache.VISIBLE);
            f.cache.request("visible", TutorialImageCache.METADATA);
            f.cache.endFrame();
            assertEquals(1, f.tasks.size());
            f.runWorker(); assertTrue(f.backend.uploaded.isEmpty());
            f.frame(Map.of("visible", 0, "next", 0));
            assertEquals(List.of("visible"), f.backend.uploaded);
            assertEquals(1, f.tasks.size());
            f.runWorker();
            assertEquals(1, f.backend.uploaded.size());
            f.frame(Map.of("visible", 0, "next", 0));
            assertEquals(List.of("visible", "next"), f.backend.uploaded);
        }
    }
    @Test void sharedImageReferencesReuseOneTextureUntilIdleExpiry() {
        try (var f = new Fixture(128)) {
            f.load("shared"); f.visible("shared");
            f.now = 99; f.frame(Map.of());
            assertEquals("shared", f.cache.texture("shared"));
            f.visible("shared"); // Revisit resets expiry without IO.
            f.now = 198; f.frame(Map.of());
            assertEquals("shared", f.cache.texture("shared"));
            f.now = 199; f.frame(Map.of());
            assertNull(f.cache.texture("shared"));
            assertEquals(0, f.cache.residentBytes());
            assertEquals(List.of("shared"), f.backend.released);
            f.load("shared");
            assertEquals(2, f.backend.decoded.size());
            assertEquals(1, f.backend.inspected.size());
        }
    }
    @Test void budgetEvictsOldTexturesButNeverThrashesTheVisibleWorkingSet() {
        try (var f = new Fixture(64)) {
            f.load("a"); f.now++;
            f.load("b");
            assertEquals(List.of("a"), f.backend.released);
            assertEquals(64, f.cache.residentBytes());
            f.frame(Map.of("a", 0, "b", 0)); f.runWorker();
            f.frame(Map.of("a", 0, "b", 0));
            assertEquals(128, f.cache.residentBytes());
            assertEquals("a", f.cache.texture("a")); assertEquals("b", f.cache.texture("b"));
            f.frame(Map.of("a", 0, "b", 0, "near", 1)); f.runWorker();
            f.frame(Map.of("a", 0, "b", 0, "near", 1));
            assertFalse(f.backend.decoded.contains("near"));
            assertTrue(f.tasks.isEmpty());
        }
    }
    @Test void rapidPageSwitchDiscardsOldPixelsAndDoesNotUploadTheWrongPage() {
        try (var f = new Fixture(128)) {
            f.visible("old"); f.runWorker(); f.visible("new");
            assertEquals(1, f.backend.pixels.get(0).closed);
            assertTrue(f.backend.uploaded.isEmpty());
            f.runWorker(); f.visible("new");
            assertEquals(List.of("new"), f.backend.uploaded);
        }
    }
    @Test void failuresAreReportedOnceAndDoNotBlockOtherImages() {
        try (var f = new Fixture(128)) {
            f.load("missing"); f.visible("missing"); f.visible("missing");
            assertEquals(List.of("missing"), f.backend.failures);
            assertNotNull(f.cache.failure("missing")); assertTrue(f.tasks.isEmpty());
            f.load("good"); assertEquals("good", f.cache.texture("good"));
        }
    }
    @Test void uploadFailureClosesDecodedPixelsAndDoesNotChargeTheCache() {
        try (var f = new Fixture(128)) {
            f.backend.failUpload = true; f.load("bad");
            assertEquals(1, f.backend.pixels.get(0).closed);
            assertEquals(0, f.cache.residentBytes());
            assertEquals(List.of("bad"), f.backend.failures);
        }
    }
    @Test void nativeWorkerErrorsInHeadersOrPixelsCannotPermanentlyBlockTheQueue() {
        for (boolean duringHeader : List.of(false, true)) {
            var backend = new Backend() {
                @Override public TutorialSize.Dimensions inspect(String key) throws Exception {
                    if (duringHeader && key.equals("bad")) throw new OutOfMemoryError("Out of stack space.");
                    return super.inspect(key);
                }
                @Override public Pixels decode(String key) {
                    if (key.equals("bad")) throw new OutOfMemoryError("Out of stack space.");
                    return super.decode(key);
                }
            };
            var tasks = new ArrayDeque<Runnable>();
            try (var cache = new TutorialImageCache<>(backend, tasks::add, () -> 0, 100, 128)) {
                cache.beginFrame(); cache.request("bad", 0); cache.endFrame();
                assertDoesNotThrow(tasks.remove()::run);
                cache.beginFrame(); cache.request("bad", 0); cache.request("good", 0); cache.endFrame();
                assertEquals(List.of("bad"), backend.failures);
                assertTrue(cache.failure("bad").contains("Out of stack space"));
                assertEquals(1, tasks.size(), "The next image must still be scheduled");
                tasks.remove().run();
                cache.beginFrame(); cache.request("good", 0); cache.endFrame();
                assertEquals("good", cache.texture("good"));
                assertEquals(1, backend.pixels.get(0).closed);
                assertEquals(List.of("bad"), backend.failures);
            }
        }
    }
    @Test void closingDisposesBothResidentTexturesAndCompletedPixelsExactlyOnce() {
        var f = new Fixture(128);
        f.load("resident"); f.visible("pending"); f.runWorker();
        f.close(); f.close();
        assertEquals(List.of("resident"), f.backend.released);
        assertTrue(f.backend.pixels.stream().allMatch(value -> value.closed == 1));
        f.visible("closed"); assertTrue(f.tasks.isEmpty());
    }
    @Test void closeBeforeWorkerStartsPreventsAllResourceReads() {
        var f = new Fixture(128);
        f.visible("pending"); f.close(); f.runWorker();
        assertTrue(f.backend.inspected.isEmpty()); assertTrue(f.backend.decoded.isEmpty());
    }
    @Test void closingDuringDecodeDisposesLatePixelsWithoutAnyMainThreadCallback() throws Exception {
        var entered = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        var ended = new CountDownLatch(1);
        var backend = new Backend() {
            @Override public Pixels decode(String key) {
                entered.countDown();
                try { assertTrue(proceed.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new AssertionError(exception); }
                return super.decode(key);
            }
        };
        var cache = new TutorialImageCache<>(backend, task -> new Thread(() -> {
            try { task.run(); } finally { ended.countDown(); }
        }).start(), System::nanoTime, 100, 128);
        try {
            cache.beginFrame(); cache.request("late", 0); cache.endFrame();
            assertTrue(entered.await(5, TimeUnit.SECONDS)); cache.close();
        } finally { proceed.countDown(); }
        assertTrue(ended.await(5, TimeUnit.SECONDS));
        assertEquals(1, backend.pixels.get(0).closed);
        assertTrue(backend.uploaded.isEmpty()); assertTrue(backend.failures.isEmpty());
    }
}
