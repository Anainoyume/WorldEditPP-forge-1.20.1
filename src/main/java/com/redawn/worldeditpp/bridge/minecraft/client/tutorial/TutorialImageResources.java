package com.redawn.worldeditpp.bridge.minecraft.client.tutorial;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.redawn.common.ui.tutorial.TutorialImageCache;
import com.redawn.common.ui.tutorial.TutorialSize;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Image IO stays on a worker; only owned GPU textures are uploaded/released on the UI thread. */
final class TutorialImageResources implements AutoCloseable {
    private static final AtomicLong IDS = new AtomicLong();
    private record Reference(ResourceLocation page, int line) { }
    private final Map<ResourceLocation, List<Reference>> references = new HashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tutorial-image-reader");
        thread.setDaemon(true); return thread;
    });
    final TutorialImageCache<ResourceLocation, NativeImage, ResourceLocation> cache;
    private volatile boolean invalidated;
    private final ResourceLocation reloadMarker;
    private boolean markerRegistered;

    TutorialImageResources(String namespace, ResourceManager resources, Consumer<TutorialLibrary.Failure> failures) {
        long owner = IDS.incrementAndGet();
        reloadMarker = new ResourceLocation(namespace, "_tutorial_cache/" + owner + "/reload");
        cache = new TutorialImageCache<>(new TutorialImageCache.Backend<>() {
            private long nextTexture;
            @Override public TutorialSize.Dimensions inspect(ResourceLocation key) throws IOException {
                try (var stream = resources.open(key)) {
                    return TutorialImageDecoder.inspect(stream);
                }
            }
            @Override public NativeImage decode(ResourceLocation key) throws IOException {
                try (var stream = resources.open(key)) {
                    byte[] encoded = stream.readNBytes(TutorialImageDecoder.MAX_FILE_BYTES + 1);
                    // The decoder validates these exact bytes before allocating pixel storage.
                    return TutorialImageDecoder.decode(encoded);
                }
            }
            @Override public ResourceLocation upload(ResourceLocation key, NativeImage pixels) {
                ResourceLocation id = new ResourceLocation(namespace,
                        "_tutorial_cache/" + owner + "/" + nextTexture++);
                var texture = new ImageTexture();
                try {
                    texture.upload(pixels);
                    Minecraft.getInstance().getTextureManager().register(id, texture);
                    return id;
                } catch (RuntimeException exception) { texture.releaseId(); throw exception; }
            }
            @Override public void release(ResourceLocation texture) {
                Minecraft.getInstance().getTextureManager().release(texture);
            }
            @Override public void failed(ResourceLocation key, Exception failure) {
                for (var reference : references.getOrDefault(key, List.of()))
                    failures.accept(new TutorialLibrary.Failure(reference.page(), reference.line(),
                            "Cannot read tutorial image " + key + ": " + failure));
            }
        }, worker, System::nanoTime, TimeUnit.SECONDS.toNanos(30), 64L * 1024 * 1024);
    }
    void reference(ResourceLocation image, ResourceLocation page, int line) {
        var locations = references.computeIfAbsent(image, ignored -> new ArrayList<>());
        var reference = new Reference(page, line);
        if (!locations.contains(reference)) locations.add(reference);
    }
    boolean invalidated() { return invalidated; }
    void beginFrame() {
        // A zero-allocation texture receives vanilla reload notifications even before any image is uploaded.
        if (!markerRegistered) {
            Minecraft.getInstance().getTextureManager().register(reloadMarker, new ImageTexture());
            markerRegistered = true;
        }
        cache.beginFrame();
    }
    final class ImageTexture extends AbstractTexture {
        void upload(NativeImage pixels) {
            int maximum = com.mojang.blaze3d.systems.RenderSystem.maxSupportedTextureSize();
            if (pixels.getWidth() > maximum || pixels.getHeight() > maximum)
                throw new IllegalArgumentException("Tutorial image exceeds GPU texture limit: " + maximum);
            TextureUtil.prepareImage(getId(), pixels.getWidth(), pixels.getHeight());
            bind();
            pixels.upload(0, 0, 0, false);
            setFilter(false, false);
        }
        @Override public void load(ResourceManager resources) { }
        @Override public void reset(TextureManager textures, ResourceManager resources,
                                    ResourceLocation location, java.util.concurrent.Executor executor) {
            // TextureManager invokes reset during resource reload. The screen rebuilds on its next frame.
            invalidated = true;
        }
        @Override public void close() { releaseId(); }
    }
    @Override public void close() {
        cache.close();
        worker.shutdownNow();
        if (markerRegistered) {
            Minecraft.getInstance().getTextureManager().release(reloadMarker);
            markerRegistered = false;
        }
    }
}
