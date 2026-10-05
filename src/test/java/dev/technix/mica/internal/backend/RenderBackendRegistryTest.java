package dev.technix.mica.internal.backend;

import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.MicaBackendException;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.TextureFilter;
import imgui.ImDrawData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderBackendRegistryTest {

    private static final String DIAGNOSTICS =
            BackendDiagnostics.prefix("26.2", RenderBackendType.OPENGL, "0.1");

    @Test
    void createsTheBackendMatchingTheDetectedType() {
        FakeBackend vulkan = new FakeBackend(RenderBackendType.VULKAN);
        FakeBackend opengl = new FakeBackend(RenderBackendType.OPENGL);
        RenderBackendRegistry<String> registry = new RenderBackendRegistry<String>()
                .register(RenderBackendType.VULKAN, context -> vulkan)
                .register(RenderBackendType.OPENGL, context -> opengl);

        assertSame(vulkan, registry.create(RenderBackendType.VULKAN, "ctx", DIAGNOSTICS));
        assertSame(opengl, registry.create(RenderBackendType.OPENGL, "ctx", DIAGNOSTICS));
    }

    @Test
    void neverFallsBackToAnotherBackend() {
        RenderBackendRegistry<String> registry = new RenderBackendRegistry<String>()
                .register(RenderBackendType.OPENGL, context -> new FakeBackend(RenderBackendType.OPENGL));

        MicaBackendException exception = assertThrows(MicaBackendException.class,
                () -> registry.create(RenderBackendType.VULKAN, "ctx", DIAGNOSTICS));
        assertEquals(RenderBackendType.VULKAN, exception.backend());
        assertTrue(exception.getMessage().startsWith(DIAGNOSTICS));
        assertTrue(exception.getMessage().contains("no renderer for the Vulkan backend"));
    }

    @Test
    void unknownBackendGivesAClearDiagnostic() {
        RenderBackendRegistry<String> registry = new RenderBackendRegistry<String>()
                .register(RenderBackendType.VULKAN, context -> new FakeBackend(RenderBackendType.VULKAN));

        MicaBackendException exception = assertThrows(MicaBackendException.class,
                () -> registry.create(RenderBackendType.UNKNOWN, "ctx", DIAGNOSTICS));
        assertTrue(exception.getMessage().contains("Minecraft 26.2"));
        assertTrue(exception.getMessage().contains("Mica 0.1"));
        assertTrue(exception.getMessage().contains("Unknown"));
    }

    @Test
    void factoryFailuresAreWrappedWithDiagnostics() {
        IllegalStateException cause = new IllegalStateException("no device");
        RenderBackendRegistry<String> registry = new RenderBackendRegistry<String>()
                .register(RenderBackendType.OPENGL, context -> {
                    throw cause;
                });

        MicaBackendException exception = assertThrows(MicaBackendException.class,
                () -> registry.create(RenderBackendType.OPENGL, "ctx", DIAGNOSTICS));
        assertSame(cause, exception.getCause());
        assertTrue(exception.getMessage().contains("no device"));
    }

    @Test
    void unknownCannotBeRegistered() {
        assertThrows(IllegalArgumentException.class, () -> new RenderBackendRegistry<String>()
                .register(RenderBackendType.UNKNOWN, context -> new FakeBackend(RenderBackendType.UNKNOWN)));
    }

    @Test
    void defaultDiagnosticsNameEveryVersion() {
        String prefix = BackendDiagnostics.prefix("26.2", RenderBackendType.VULKAN, "1.2.3");
        assertTrue(prefix.contains("Minecraft 26.2"));
        assertTrue(prefix.contains("rendering backend Vulkan"));
        assertTrue(prefix.contains("Mica 1.2.3"));
    }

    static final class FakeBackend implements RenderBackend {
        private final RenderBackendType type;

        FakeBackend(RenderBackendType type) {
            this.type = type;
        }

        @Override public @NotNull RenderBackendType type() { return type; }
        @Override public boolean initialize() { return true; }
        @Override public boolean isInitialized() { return true; }
        @Override public @Nullable Viewport beginFrame() { return new Viewport(1, 1); }
        @Override public void prepareFrame() { }
        @Override public long recordBackdrop() { return 0L; }
        @Override public boolean isReadyToRender() { return true; }
        @Override public void render(@NotNull ImDrawData drawData) { }
        @Override public long hostTextureHandle(@NotNull Identifier textureId) { return 0L; }
        @Override public long registerTexture(long hostTextureHandle, @NotNull TextureFilter filter) { return 0L; }
        @Override public void releaseTexture(long imGuiTextureId) { }
        @Override public void configureFrostedGlass(boolean enabled, @NotNull FrostedGlassStyle style) { }
        @Override public boolean supportsFrostedGlass() { return false; }
        @Override public void shutdown() { }
    }
}
