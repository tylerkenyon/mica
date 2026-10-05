package dev.technix.mica.internal;

import dev.technix.mica.api.MicaTexture;
import dev.technix.mica.api.TextureFilter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;


/**
 * Backend-independent bookkeeping behind {@link MicaTexture}.
 *
 * <p>One {@link Registration} exists per (texture key, filter); every
 * {@link #acquire(Object, TextureFilter)} hands out a ref-counted {@link MicaTexture} view of
 * it, so many callers share one backend registration (Vulkan descriptor sets are a scarce
 * pool). Each resolve on the render thread compares the host texture identity with the one
 * registered and re-registers when Minecraft has re-created the texture.
 *
 * <p>Releases are deferred by {@link #RELEASE_DELAY_FRAMES} frames so a frame still in
 * flight on the GPU never samples a freed registration, and any close from a non-render
 * thread is queued until the next {@link #tick()}.
 *
 * @param <K> texture key (a Minecraft {@code Identifier} in production)
 */
public final class TextureCache<K> {

    /** Frames a released registration stays alive; covers Minecraft's frames in flight. */
    public static final int RELEASE_DELAY_FRAMES = 3;

    /** How the cache reaches the active backend. All calls happen on the render thread. */
    public interface Source<K> {
        long hostHandle(@NotNull K key);

        long register(long hostHandle, @NotNull TextureFilter filter);

        void release(long imGuiTextureId);
    }

    private final Source<K> source;
    private final BooleanSupplier onRenderThread;

    private final List<Registration> registrations = new ArrayList<>();
    private final ConcurrentLinkedQueue<Handle> pendingCloses = new ConcurrentLinkedQueue<>();
    private final Deque<long[]> pendingReleases = new ArrayDeque<>();

    private volatile boolean available;
    private long frame;

    public TextureCache(@NotNull Source<K> source, @NotNull BooleanSupplier onRenderThread) {
        this.source = Objects.requireNonNull(source, "source");
        this.onRenderThread = Objects.requireNonNull(onRenderThread, "onRenderThread");
    }

    /** A new reference to the shared registration for {@code key} + {@code filter}. */
    @NotNull
    public MicaTexture acquire(@NotNull K key, @NotNull TextureFilter filter) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(filter, "filter");
        synchronized (registrations) {
            for (Registration registration : registrations) {
                if (registration.key.equals(key) && registration.filter == filter) {
                    registration.references++;
                    return new Handle(registration);
                }
            }
            Registration registration = new Registration(key, filter);
            registration.references = 1;
            registrations.add(registration);
            return new Handle(registration);
        }
    }

    /**
     * Whether the backend can register textures. Turning it off forgets every backend id
     * without releasing them: the backend destroys its own resources on shutdown.
     */
    public void setAvailable(boolean available) {
        this.available = available;
        if (!available) {
            synchronized (registrations) {
                for (Registration registration : registrations) {
                    registration.hostHandle = 0L;
                    registration.imGuiTextureId = 0L;
                }
            }
            pendingReleases.clear();
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Once per frame on the render thread: applies queued closes and due releases. */
    public void tick() {
        frame++;
        Handle closed;
        while ((closed = pendingCloses.poll()) != null) {
            closeNow(closed);
        }
        while (!pendingReleases.isEmpty() && pendingReleases.peekFirst()[0] <= frame) {
            long id = pendingReleases.pollFirst()[1];
            if (available) {
                source.release(id);
            }
        }
    }

    /** Live registrations, for tests and diagnostics. */
    public int registrationCount() {
        synchronized (registrations) {
            return registrations.size();
        }
    }

    private long resolve(Registration registration) {
        if (registration.closed) {
            return 0L;
        }
        if (!available || !onRenderThread.getAsBoolean()) {
            return available ? registration.imGuiTextureId : 0L;
        }
        long hostHandle = source.hostHandle(registration.key);
        if (hostHandle == registration.hostHandle && registration.imGuiTextureId != 0L) {
            return registration.imGuiTextureId;
        }
        scheduleRelease(registration);
        if (hostHandle == 0L) {
            return 0L;
        }
        long id = source.register(hostHandle, registration.filter);
        registration.imGuiTextureId = id;
        registration.hostHandle = id != 0L ? hostHandle : 0L;
        return id;
    }

    private void scheduleRelease(Registration registration) {
        if (registration.imGuiTextureId != 0L) {
            pendingReleases.addLast(new long[] {frame + RELEASE_DELAY_FRAMES,
                    registration.imGuiTextureId});
        }
        registration.imGuiTextureId = 0L;
        registration.hostHandle = 0L;
    }

    private void closeNow(Handle handle) {
        synchronized (registrations) {
            Registration registration = handle.registration;
            registration.references--;
            if (registration.references > 0 || registration.closed) {
                return;
            }
            registration.closed = true;
            registrations.remove(registration);
            if (available) {
                scheduleRelease(registration);
            }
        }
    }

    private final class Registration {
        final K key;
        final TextureFilter filter;
        int references;
        long hostHandle;
        volatile long imGuiTextureId;
        volatile boolean closed;

        Registration(K key, TextureFilter filter) {
            this.key = key;
            this.filter = filter;
        }
    }

    private final class Handle implements MicaTexture {
        private final Registration registration;
        private volatile boolean closed;

        Handle(Registration registration) {
            this.registration = registration;
        }

        @Override
        public long imGuiTextureId() {
            return closed ? 0L : resolve(registration);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (onRenderThread.getAsBoolean()) {
                closeNow(this);
            } else {
                pendingCloses.add(this);
            }
        }

        @Override
        public String toString() {
            return "MicaTexture[" + registration.key + ", " + registration.filter
                    + (closed ? ", closed]" : "]");
        }
    }
}
