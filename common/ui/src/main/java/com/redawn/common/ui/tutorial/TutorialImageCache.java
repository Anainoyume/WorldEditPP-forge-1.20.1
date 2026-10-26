package com.redawn.common.ui.tutorial;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;

/** Main-thread cache policy with one bounded worker result and at most one upload per frame. */
public final class TutorialImageCache<K, D extends AutoCloseable, T> implements AutoCloseable {
    public static final int VISIBLE = 0, NEARBY = 1, METADATA = 2;
    public interface Backend<K, D, T> {
        TutorialSize.Dimensions inspect(K key) throws Exception;
        D decode(K key) throws Exception;
        // Upload borrows pixels. The cache always closes them after this call.
        T upload(K key, D pixels) throws Exception;
        void release(T texture);
        void failed(K key, Exception failure);
    }
    private final class Entry {
        final K key;
        TutorialSize.Dimensions size = new TutorialSize.Dimensions(16, 9);
        boolean known;
        String failure;
        T texture;
        long requested = -1, lastUsed;
        int priority = METADATA;
        Entry(K key) { this.key = key; }
        long bytes() { return (long) size.width() * size.height() * 4; }
    }
    private final class Result {
        final Entry entry;
        TutorialSize.Dimensions size;
        D pixels;
        Exception failure;
        Result(Entry entry) { this.entry = entry; }
    }
    private final Map<K, Entry> entries = new LinkedHashMap<>();
    private final Backend<K, D, T> backend;
    private final Executor executor;
    private final LongSupplier clock;
    private final long idleNanos, budget;
    private final Object handoff = new Object();
    private Result completed;
    private boolean busy;
    private volatile boolean closed;
    private long frame, revision, residentBytes;

    public TutorialImageCache(Backend<K, D, T> backend, Executor executor, LongSupplier clock,
                              long idleNanos, long budget) {
        this.backend = backend; this.executor = executor; this.clock = clock;
        this.idleNanos = idleNanos; this.budget = budget;
    }
    private Entry entry(K key) { return entries.computeIfAbsent(key, Entry::new); }
    public TutorialSize.Dimensions dimensions(K key) { return entry(key).size; }
    public T texture(K key) { return entry(key).texture; }
    public String failure(K key) { return entry(key).failure; }
    public long revision() { return revision; }
    public long residentBytes() { return residentBytes; }
    public void beginFrame() { frame++; }
    public void request(K key, int priority) {
        if (closed) return;
        Entry entry = entry(key);
        entry.priority = entry.requested == frame ? Math.min(entry.priority, priority) : priority;
        entry.requested = frame;
        if (priority < METADATA) entry.lastUsed = clock.getAsLong();
    }
    public void endFrame() {
        if (closed) return;
        Result result;
        synchronized (handoff) { result = completed; completed = null; }
        if (result != null) {
            busy = false;
            Entry entry = result.entry;
            if (result.size != null) {
                if (!entry.size.equals(result.size)) revision++;
                entry.size = result.size; entry.known = true;
            }
            try {
                if (result.failure != null) throw result.failure;
                // A switched page must never upload its obsolete worker result.
                if (result.pixels != null && entry.requested == frame && entry.priority < METADATA) {
                    entry.texture = backend.upload(entry.key, result.pixels);
                    residentBytes += entry.bytes();
                }
            } catch (Exception failure) {
                entry.failure = failure.toString();
                backend.failed(entry.key, failure);
            } finally { dispose(result.pixels); }
        }
        evict();
        if (busy) return;
        Entry next = null;
        for (Entry entry : entries.values()) {
            if (entry.requested != frame || entry.failure != null
                    || entry.known && (entry.priority == METADATA || entry.texture != null)) continue;
            if (entry.known && entry.priority == NEARBY && residentBytes + entry.bytes() > budget) continue;
            if (next == null || entry.priority < next.priority) next = entry;
        }
        if (next == null) return;
        busy = true;
        Entry selected = next;
        boolean pixels = selected.priority == VISIBLE
                || selected.priority == NEARBY && selected.known && residentBytes + selected.bytes() <= budget;
        try { executor.execute(() -> load(selected, pixels)); }
        catch (RuntimeException failure) {
            busy = false; selected.failure = failure.toString(); backend.failed(selected.key, failure);
        }
    }
    private void load(Entry entry, boolean pixels) {
        Result result = new Result(entry);
        try {
            if (!closed) {
                result.size = entry.known ? entry.size : backend.inspect(entry.key);
                if (pixels && !closed) result.pixels = backend.decode(entry.key);
            }
        } catch (Throwable failure) {
            // Native decoders can throw Error (for example LWJGL stack exhaustion).
            // Always complete the handoff so one failed image cannot leave busy stuck forever.
            result.failure = failure instanceof Exception exception ? exception
                    : new IllegalStateException("Image worker failed: " + failure, failure);
        } finally {
            synchronized (handoff) {
                if (closed) dispose(result.pixels);
                else completed = result;
            }
        }
    }
    private void evict() {
        long now = clock.getAsLong();
        for (Entry entry : entries.values()) {
            if (entry.texture != null && !(entry.requested == frame && entry.priority < METADATA)
                    && now - entry.lastUsed >= idleNanos) release(entry);
        }
        while (residentBytes > budget) {
            Entry oldest = null;
            for (Entry entry : entries.values()) {
                // Visible working sets may exceed the soft budget; never thrash a visible image.
                if (entry.texture == null || entry.requested == frame && entry.priority == VISIBLE) continue;
                if (oldest == null || entry.lastUsed < oldest.lastUsed) oldest = entry;
            }
            if (oldest == null) break;
            release(oldest);
        }
    }
    private void release(Entry entry) {
        backend.release(entry.texture); entry.texture = null; residentBytes -= entry.bytes();
    }
    private static void dispose(AutoCloseable value) {
        if (value != null) try { value.close(); } catch (Exception ignored) { }
    }
    @Override public void close() {
        synchronized (handoff) {
            if (closed) return;
            closed = true;
            if (completed != null) { dispose(completed.pixels); completed = null; }
        }
        for (Entry entry : entries.values()) if (entry.texture != null) release(entry);
        entries.clear();
    }
}
