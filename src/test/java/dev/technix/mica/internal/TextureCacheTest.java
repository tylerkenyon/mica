package dev.technix.mica.internal;

import dev.technix.mica.api.MicaTexture;
import dev.technix.mica.api.TextureFilter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureCacheTest {

    /** Stands in for a backend: host handles per key, ids handed out sequentially. */
    private static final class FakeSource implements TextureCache.Source<String> {
        final Map<String, Long> hostHandles = new HashMap<>();
        final List<Long> released = new ArrayList<>();
        int registrations;
        long nextId = 100;

        @Override
        public long hostHandle(@NotNull String key) {
            return hostHandles.getOrDefault(key, 0L);
        }

        @Override
        public long register(long hostHandle, @NotNull TextureFilter filter) {
            registrations++;
            return nextId++;
        }

        @Override
        public void release(long imGuiTextureId) {
            released.add(imGuiTextureId);
        }
    }

    private FakeSource source;
    private boolean onRenderThread;
    private TextureCache<String> cache;

    @BeforeEach
    void setUp() {
        source = new FakeSource();
        onRenderThread = true;
        cache = new TextureCache<>(source, () -> onRenderThread);
        source.hostHandles.put("items", 7L);
    }

    @Test
    void idIsZeroUntilTheBackendIsAvailable() {
        MicaTexture texture = cache.acquire("items", TextureFilter.LINEAR);
        assertEquals(0L, texture.imGuiTextureId());
        assertFalse(texture.isValid());

        cache.setAvailable(true);
        assertNotEquals(0L, texture.imGuiTextureId());
        assertTrue(texture.isValid());
    }

    @Test
    void handlesForTheSameTextureShareOneRegistration() {
        cache.setAvailable(true);
        MicaTexture first = cache.acquire("items", TextureFilter.LINEAR);
        MicaTexture second = cache.acquire("items", TextureFilter.LINEAR);
        MicaTexture nearest = cache.acquire("items", TextureFilter.NEAREST);

        assertEquals(first.imGuiTextureId(), second.imGuiTextureId());
        assertNotEquals(first.imGuiTextureId(), nearest.imGuiTextureId());
        assertEquals(2, source.registrations);
        assertEquals(2, cache.registrationCount());
    }

    @Test
    void reRegistersWhenMinecraftRecreatesTheTextureAndDefersTheRelease() {
        cache.setAvailable(true);
        MicaTexture texture = cache.acquire("items", TextureFilter.LINEAR);
        long before = texture.imGuiTextureId();

        source.hostHandles.put("items", 8L);
        long after = texture.imGuiTextureId();

        assertNotEquals(before, after);
        assertTrue(source.released.isEmpty(), "release must wait for frames in flight");
        for (int frame = 0; frame < TextureCache.RELEASE_DELAY_FRAMES; frame++) {
            cache.tick();
        }
        assertEquals(List.of(before), source.released);
    }

    @Test
    void missingHostTextureYieldsZero() {
        cache.setAvailable(true);
        MicaTexture texture = cache.acquire("missing", TextureFilter.LINEAR);
        assertEquals(0L, texture.imGuiTextureId());
        assertEquals(0, source.registrations);
    }

    @Test
    void closingTheLastReferenceReleasesTheRegistration() {
        cache.setAvailable(true);
        MicaTexture first = cache.acquire("items", TextureFilter.LINEAR);
        MicaTexture second = cache.acquire("items", TextureFilter.LINEAR);
        long id = first.imGuiTextureId();

        first.close();
        assertEquals(0L, first.imGuiTextureId());
        assertEquals(id, second.imGuiTextureId());

        second.close();
        second.close();
        assertEquals(0, cache.registrationCount());
        for (int frame = 0; frame < TextureCache.RELEASE_DELAY_FRAMES; frame++) {
            cache.tick();
        }
        assertEquals(List.of(id), source.released);
    }

    @Test
    void closeFromAnotherThreadIsAppliedOnTheNextRenderTick() {
        cache.setAvailable(true);
        MicaTexture texture = cache.acquire("items", TextureFilter.LINEAR);
        texture.imGuiTextureId();

        onRenderThread = false;
        texture.close();
        assertEquals(1, cache.registrationCount());

        onRenderThread = true;
        cache.tick();
        assertEquals(0, cache.registrationCount());
    }

    @Test
    void offRenderThreadReadsNeverTouchTheBackend() {
        cache.setAvailable(true);
        MicaTexture texture = cache.acquire("items", TextureFilter.LINEAR);
        onRenderThread = false;
        assertEquals(0L, texture.imGuiTextureId());
        assertEquals(0, source.registrations);

        onRenderThread = true;
        long id = texture.imGuiTextureId();
        onRenderThread = false;
        assertEquals(id, texture.imGuiTextureId());
    }

    @Test
    void handlesSurviveABackendRestart() {
        cache.setAvailable(true);
        MicaTexture texture = cache.acquire("items", TextureFilter.LINEAR);
        long before = texture.imGuiTextureId();

        cache.setAvailable(false);
        assertEquals(0L, texture.imGuiTextureId());
        assertTrue(source.released.isEmpty(), "the backend frees its own resources on shutdown");

        cache.setAvailable(true);
        long after = texture.imGuiTextureId();
        assertNotEquals(0L, after);
        assertNotEquals(before, after);
    }
}
