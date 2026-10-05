package dev.technix.mica.internal;

import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.MicaBackendException;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.SpriteBounds;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.internal.backend.RenderBackend;
import dev.technix.mica.internal.backend.RenderBackendRegistry;
import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.assertion.ImAssertCallback;
import imgui.type.ImInt;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the backend-independent core through real Dear ImGui (the native library) with a
 * fake {@link RenderBackend}, covering backend selection, lazy initialisation, frame pairing,
 * failure reporting and shutdown/re-initialisation without a GPU.
 */
class ImGuiRendererLifecycleTest {

    private static final List<String> IMGUI_ASSERTIONS = new ArrayList<>();

    @BeforeAll
    static void installAssertCallback() {
        ImGui.init();
        ImGui.setAssertCallback(new ImAssertCallback() {
            @Override
            public void imAssertCallback(String assertion, int line, String file) {
                IMGUI_ASSERTIONS.add(assertion + " (" + file + ":" + line + ")");
            }
        });
    }

    private FakeCompat compat;
    private List<FakeBackend> created;
    private RenderBackendRegistry<MinecraftCompat> registry;
    private ImGuiRenderer renderer;

    @BeforeEach
    void setUp() {
        IMGUI_ASSERTIONS.clear();
        compat = new FakeCompat();
        created = new ArrayList<>();
        registry = new RenderBackendRegistry<MinecraftCompat>()
                .register(RenderBackendType.VULKAN, c -> track(new FakeBackend(RenderBackendType.VULKAN)))
                .register(RenderBackendType.OPENGL, c -> track(new FakeBackend(RenderBackendType.OPENGL)));
        renderer = new ImGuiRenderer(compat, List.of(), registry);
    }

    @AfterEach
    void tearDown() {
        renderer.shutdown();
        assertTrue(IMGUI_ASSERTIONS.isEmpty(), "Dear ImGui assertions: " + IMGUI_ASSERTIONS);
    }

    private FakeBackend track(FakeBackend backend) {
        created.add(backend);
        return backend;
    }

    @Test
    void waitsForMinecraftsDeviceThenSelectsTheMatchingBackend() {
        compat.backend = Optional.empty();
        assertFalse(renderer.beginFrame());
        assertTrue(created.isEmpty());

        compat.backend = Optional.of(RenderBackendType.OPENGL);
        assertTrue(renderer.beginFrame());
        assertEquals(1, created.size());
        assertEquals(RenderBackendType.OPENGL, created.get(0).type());
        assertEquals(Optional.of(RenderBackendType.OPENGL), renderer.activeBackend());
        assertTrue(renderer.isEnabled());

        renderer.endFrame();
        assertEquals(1, created.get(0).renders);
        assertFalse(renderer.isFrameOpen());
    }

    @Test
    void sameCoreDrivesVulkanToo() {
        compat.backend = Optional.of(RenderBackendType.VULKAN);
        assertTrue(renderer.beginFrame());
        renderer.endFrame();
        assertEquals(RenderBackendType.VULKAN, created.get(0).type());
        assertEquals(1, created.get(0).prepares);
        assertEquals(1, created.get(0).renders);
    }

    @Test
    void retriesInitialisationUntilTheHostIsReady() {
        compat.backend = Optional.of(RenderBackendType.VULKAN);
        registry.register(RenderBackendType.VULKAN, c -> {
            FakeBackend backend = new FakeBackend(RenderBackendType.VULKAN);
            backend.hostReadyAfter = 2;
            return track(backend);
        });
        assertFalse(renderer.beginFrame());
        assertFalse(renderer.beginFrame());
        assertTrue(renderer.beginFrame());
        assertEquals(1, created.size(), "the backend is selected once");
        assertEquals(3, created.get(0).initializeCalls);
        renderer.endFrame();
    }

    @Test
    void unknownBackendFailsWithDiagnosticsAndDoesNotRetry() {
        compat.backend = Optional.of(RenderBackendType.UNKNOWN);
        assertFalse(renderer.beginFrame());
        MicaBackendException failure = renderer.failure().orElseThrow();
        assertEquals(RenderBackendType.UNKNOWN, failure.backend());
        assertTrue(failure.getMessage().contains("Minecraft 26.2-test"), failure.getMessage());
        assertTrue(failure.getMessage().contains("rendering backend Unknown"), failure.getMessage());
        assertTrue(failure.getMessage().contains("Mica "), failure.getMessage());

        compat.backend = Optional.of(RenderBackendType.OPENGL);
        assertFalse(renderer.beginFrame(), "a failed renderer stays off for the session");
        assertTrue(created.isEmpty());
    }

    @Test
    void backendInitialisationErrorsAreReportedNotThrown() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        IllegalStateException cause = new IllegalStateException("OpenGL 3.2 core is required");
        registry.register(RenderBackendType.OPENGL, c -> {
            FakeBackend backend = new FakeBackend(RenderBackendType.OPENGL);
            backend.initializeFailure = cause;
            return track(backend);
        });
        assertFalse(renderer.beginFrame());
        MicaBackendException failure = renderer.failure().orElseThrow();
        assertSame(cause, failure.getCause());
        assertTrue(failure.getMessage().contains("OpenGL 3.2 core is required"));
        assertEquals(RenderBackendType.OPENGL, failure.backend());
    }

    @Test
    void anUnfinishedFrameIsClosedBeforeTheNextOne() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        assertTrue(renderer.beginFrame());
        assertTrue(renderer.beginFrame());
        renderer.endFrame();
        assertEquals(1, created.get(0).renders);
    }

    @Test
    void framesAreRefusedOffTheRenderThread() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        compat.onRenderThread = false;
        assertFalse(renderer.beginFrame());
        assertTrue(created.isEmpty());
        assertFalse(renderer.failure().isPresent());
    }

    @Test
    void drawDataWaitsForTheFontAtlas() {
        compat.backend = Optional.of(RenderBackendType.VULKAN);
        registry.register(RenderBackendType.VULKAN, c -> {
            FakeBackend backend = new FakeBackend(RenderBackendType.VULKAN);
            backend.readyToRender = false;
            return track(backend);
        });
        assertTrue(renderer.beginFrame());
        renderer.endFrame();
        assertEquals(0, created.get(0).renders);
    }

    @Test
    void backdropFailureFallsBackToPlainRendering() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        registry.register(RenderBackendType.OPENGL, c -> {
            FakeBackend backend = new FakeBackend(RenderBackendType.OPENGL);
            backend.backdropFailure = new IllegalStateException("blur failed");
            return track(backend);
        });
        renderer.configureFrostedGlass(true, FrostedGlassStyle.DEFAULT);
        assertTrue(renderer.beginFrame());
        assertEquals(0L, renderer.recordBackdrop());
        renderer.endFrame();
        FakeBackend backend = created.get(0);
        assertEquals(1, backend.renders);
        assertFalse(backend.glassEnabled, "glass is switched off after a failure");

        assertTrue(renderer.beginFrame());
        assertEquals(0L, renderer.recordBackdrop());
        assertEquals(1, backend.backdropCalls, "a failed blur is not retried every frame");
        renderer.endFrame();
    }

    @Test
    void backdropIdIsPassedThrough() {
        compat.backend = Optional.of(RenderBackendType.VULKAN);
        renderer.configureFrostedGlass(true, FrostedGlassStyle.DEFAULT);
        assertTrue(renderer.beginFrame());
        assertEquals(42L, renderer.recordBackdrop());
        renderer.endFrame();

        renderer.configureFrostedGlass(false, FrostedGlassStyle.DEFAULT);
        assertTrue(renderer.beginFrame());
        assertEquals(0L, renderer.recordBackdrop());
        renderer.endFrame();
    }

    @Test
    void shutdownReleasesEverythingAndTheRendererCanStartAgain() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        assertTrue(renderer.beginFrame());
        renderer.endFrame();
        FakeBackend first = created.get(0);

        renderer.shutdown();
        assertEquals(1, first.shutdowns);
        assertFalse(renderer.isEnabled());

        assertTrue(renderer.beginFrame());
        renderer.endFrame();
        assertEquals(2, created.size());
        assertTrue(created.get(1).isInitialized());
        assertEquals(1, created.get(1).renders);
    }

    @Test
    void texturesRegisterThroughTheActiveBackendAndSurviveRestarts() {
        compat.backend = Optional.of(RenderBackendType.OPENGL);
        Identifier atlas = Identifier.fromNamespaceAndPath("minecraft", "textures/atlas/items.png");
        var texture = renderer.texture(atlas, TextureFilter.NEAREST);
        assertEquals(0L, texture.imGuiTextureId(), "nothing to register before the backend exists");

        assertTrue(renderer.beginFrame());
        long id = texture.imGuiTextureId();
        assertTrue(id != 0L);
        assertEquals(TextureFilter.NEAREST, created.get(0).lastFilter);
        renderer.endFrame();

        renderer.shutdown();
        assertEquals(0L, texture.imGuiTextureId());
        assertTrue(renderer.beginFrame());
        assertTrue(texture.imGuiTextureId() != 0L);
        renderer.endFrame();
        texture.close();
        assertEquals(0L, texture.imGuiTextureId());
    }

    private static final class FakeCompat implements MinecraftCompat {
        Optional<RenderBackendType> backend = Optional.empty();
        boolean onRenderThread = true;

        @Override
        public @NotNull Optional<RenderBackendType> renderBackend() {
            return backend;
        }

        @Override
        public @NotNull String minecraftVersion() {
            return "26.2-test";
        }

        @Override
        public boolean isOnRenderThread() {
            return onRenderThread;
        }

        @Override
        public @NotNull Optional<SpriteBounds> locateSprite(@NotNull Identifier atlasId,
                                                            @NotNull Identifier spriteId) {
            return Optional.empty();
        }
    }

    private static final class FakeBackend implements RenderBackend {
        private final RenderBackendType type;
        int hostReadyAfter;
        int initializeCalls;
        RuntimeException initializeFailure;
        RuntimeException backdropFailure;
        boolean readyToRender = true;
        boolean initialized;
        boolean glassEnabled;
        int prepares;
        int renders;
        int backdropCalls;
        int shutdowns;
        long nextTexture = 1000;
        TextureFilter lastFilter;

        FakeBackend(RenderBackendType type) {
            this.type = type;
        }

        @Override
        public @NotNull RenderBackendType type() {
            return type;
        }

        @Override
        public boolean initialize() {
            initializeCalls++;
            if (initializeFailure != null) {
                throw initializeFailure;
            }
            if (initializeCalls <= hostReadyAfter) {
                return false;
            }
            ImFontAtlas fonts = ImGui.getIO().getFonts();
            fonts.getTexDataAsRGBA32(new ImInt(), new ImInt());
            fonts.setTexID(1L);
            initialized = true;
            return true;
        }

        @Override
        public boolean isInitialized() {
            return initialized;
        }

        @Override
        public @Nullable Viewport beginFrame() {
            return new Viewport(1280, 720);
        }

        @Override
        public void prepareFrame() {
            prepares++;
        }

        @Override
        public long recordBackdrop() {
            backdropCalls++;
            if (backdropFailure != null) {
                throw backdropFailure;
            }
            return 42L;
        }

        @Override
        public boolean isReadyToRender() {
            return readyToRender;
        }

        @Override
        public void render(@NotNull ImDrawData drawData) {
            assertTrue(drawData.getValid());
            renders++;
        }

        @Override
        public long hostTextureHandle(@NotNull Identifier textureId) {
            return 7L;
        }

        @Override
        public long registerTexture(long hostTextureHandle, @NotNull TextureFilter filter) {
            lastFilter = filter;
            return nextTexture++;
        }

        @Override
        public void releaseTexture(long imGuiTextureId) {
        }

        @Override
        public void configureFrostedGlass(boolean enabled, @NotNull FrostedGlassStyle style) {
            glassEnabled = enabled;
        }

        @Override
        public boolean supportsFrostedGlass() {
            return true;
        }

        @Override
        public void shutdown() {
            shutdowns++;
            initialized = false;
        }
    }
}
