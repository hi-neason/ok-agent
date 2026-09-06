package io.okagent.module.agentruntime.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Bounded runtime cache: acquisition, replacement and eviction share one lifecycle lock. */
final class SessionPool<T extends AutoCloseable> implements AutoCloseable {
    private final int capacity;
    private final Map<String, Entry<T>> entries = new LinkedHashMap<>(16, 0.75f, true);
    private boolean closed;
    SessionPool(int capacity) { this.capacity = capacity; }

    synchronized Lease<T> acquire(String key, String version, Supplier<T> create) {
        if (closed) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Runtime is stopping");
        Entry<T> entry = entries.get(key);
        if (entry != null && entry.busy) throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is already processing a request");
        if (entry != null && !entry.version.equals(version)) {
            entries.remove(key);
            closeValue(entry.value);
            entry = null;
        }
        if (entry == null) {
            if (entries.size() >= capacity) {
                var iterator = entries.entrySet().iterator();
                boolean removed = false;
                while (iterator.hasNext()) {
                    var candidate = iterator.next().getValue();
                    if (!candidate.busy) {
                        iterator.remove();
                        closeValue(candidate.value);
                        removed = true;
                        break;
                    }
                }
                if (!removed) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "All runtime sessions are busy");
            }
            entry = new Entry<>(version, create.get());
            entries.put(key, entry);
        }
        entry.busy = true;
        Entry<T> acquired = entry;
        return new Lease<>(entry.value, () -> release(acquired));
    }

    private synchronized void release(Entry<T> entry) {
        entry.busy = false;
        if (closed) closeValue(entry.value);
    }

    @Override public synchronized void close() {
        closed = true;
        entries.values().stream().filter(entry -> !entry.busy).forEach(entry -> closeValue(entry.value));
        entries.clear();
    }

    private void closeValue(T value) {
        try { value.close(); }
        catch (Exception exception) { org.slf4j.LoggerFactory.getLogger(SessionPool.class).warn("Runtime session close failed", exception); }
    }

    private static final class Entry<T> {
        final String version;
        final T value;
        boolean busy;
        Entry(String version, T value) { this.version = version; this.value = value; }
    }

    static final class Lease<T> implements AutoCloseable {
        private final T value;
        private Runnable release;
        Lease(T value, Runnable release) { this.value = value; this.release = release; }
        T value() { return value; }
        @Override public void close() { if (release != null) { release.run(); release = null; } }
    }
}
