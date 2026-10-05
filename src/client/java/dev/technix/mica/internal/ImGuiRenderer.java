package dev.technix.mica.internal;

import dev.technix.mica.api.FontRegistry;
import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.MicaBackendException;
import dev.technix.mica.api.MicaTexture;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.internal.backend.BackendDiagnostics;
import dev.technix.mica.internal.backend.RenderBackend;
import dev.technix.mica.internal.backend.RenderBackendRegistry;
import dev.technix.mica.internal.backend.RenderBackends;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiIO;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;


/**
 * Mica's backend-independent core: owns the Dear ImGui context, the font atlas, the frame
 * lifecycle and texture bookkeeping, and drives whichever {@link RenderBackend} matches the
 * backend Minecraft is running. It never touches Vulkan or OpenGL itself.
 *
 * <p>Every method that issues GPU work runs on Minecraft's render thread; calls from any
 * other thread are refused.
 */
public final class ImGuiRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private static final boolean SKIP_BLUR = Boolean.getBoolean("imgui.debug.skipBlur");

    private final MinecraftCompat compat;
    private final RenderBackendRegistry<MinecraftCompat> registry;
    private final List<FontRegistry> fontRegistries;
    private final TextureCache<Identifier> textures;

    private RenderBackend backend;
    private boolean contextCreated;
    private boolean frameOpen;
    private boolean glassEnabled;
    private FrostedGlassStyle glassStyle = FrostedGlassStyle.DEFAULT;

    private MicaBackendException failure;
    private boolean offThreadWarningLogged;

    private RenderBackend.Viewport viewport;
    private long lastFrameNanos;
    private boolean textInputActive;

    public ImGuiRenderer(@NotNull MinecraftCompat compat) {
        this(compat, List.of(), RenderBackends.defaultRegistry());
    }

    public ImGuiRenderer(@NotNull MinecraftCompat compat, @NotNull List<FontRegistry> fontRegistries,
                         @NotNull RenderBackendRegistry<MinecraftCompat> registry) {
        this.compat = Objects.requireNonNull(compat, "compat");
        this.fontRegistries = List.copyOf(fontRegistries);
        this.registry = Objects.requireNonNull(registry, "registry");
        this.textures = new TextureCache<>(new TextureCache.Source<>() {
            @Override
            public long hostHandle(@NotNull Identifier key) {
                return backend != null ? backend.hostTextureHandle(key) : 0L;
            }

            @Override
            public long register(long hostHandle, @NotNull TextureFilter filter) {
                return backend != null ? backend.registerTexture(hostHandle, filter) : 0L;
            }

            @Override
            public void release(long imGuiTextureId) {
                if (backend != null) {
                    backend.releaseTexture(imGuiTextureId);
                }
            }
        }, compat::isOnRenderThread);
    }

    /** {@code true} once the context and the backend are live; input is routed only then. */
    public boolean isEnabled() {
        return contextCreated && backend != null && backend.isInitialized();
    }

    public boolean wantsToCaptureMouse() {
        return isEnabled() && ImGui.getIO().getWantCaptureMouse();
    }

    public boolean wantsToCaptureKeyboard() {
        return isEnabled() && ImGui.getIO().getWantCaptureKeyboard();
    }

    /** The backend Minecraft is running, once detected. */
    @NotNull
    public Optional<RenderBackendType> activeBackend() {
        return backend != null ? Optional.of(backend.type()) : compat.renderBackend();
    }

    /** The reason the renderer gave up, if it did. */
    @NotNull
    public Optional<MicaBackendException> failure() {
        return Optional.ofNullable(failure);
    }

    public void configureFrostedGlass(boolean enabled, @NotNull FrostedGlassStyle style) {
        glassEnabled = enabled;
        glassStyle = Objects.requireNonNull(style, "style");
        if (backend != null) {
            backend.configureFrostedGlass(enabled, style);
        }
    }

    // ---- frame lifecycle -------------------------------------------------------------

    /**
     * Starts an ImGui frame (calls {@code ImGui.newFrame()}). Selects and initialises the
     * backend on first use. Returns {@code false} when no frame was started; overlays are
     * skipped for that frame.
     */
    public boolean beginFrame() {
        if (failure != null) {
            return false;
        }
        if (!compat.isOnRenderThread()) {
            if (!offThreadWarningLogged) {
                offThreadWarningLogged = true;
                LOGGER.warn("Mica frame requested off the render thread ({}); ignored.",
                        Thread.currentThread().getName(), new IllegalStateException());
            }
            return false;
        }
        try {
            if (frameOpen) {
                ImGui.endFrame();
                frameOpen = false;
            }
            if (!ensureBackend()) {
                return false;
            }
            textures.tick();
            RenderBackend.Viewport next = backend.beginFrame();
            if (next == null) {
                return false;
            }
            viewport = next;

            long now = System.nanoTime();
            float deltaTime = lastFrameNanos == 0L
                    ? 1.0f / 60.0f
                    : Math.min((now - lastFrameNanos) / 1_000_000_000.0f, 0.1f);
            lastFrameNanos = now;
            double[] cursor = compat.cursorPosition();
            if (cursor != null) {
                ImGuiInputRouter.onMouseMove(this, cursor[0], cursor[1]);
            }
            imGuiFrame(next.width(), next.height(), deltaTime);
            frameOpen = true;

            backend.prepareFrame();
            return true;
        } catch (MicaBackendException exception) {
            fail(exception);
            return false;
        } catch (RuntimeException exception) {
            fail(new MicaBackendException(diagnostics() + " Reason: " + exception, currentType(),
                    exception));
            return false;
        }
    }

    /** {@code true} between a successful {@link #beginFrame()} and {@link #endFrame()}. */
    public boolean isFrameOpen() {
        return frameOpen;
    }

    /**
     * Produces this frame's frosted-glass backdrop. Returns its ImGui texture id, or
     * {@code 0} when glass is off, unsupported or failed (panels then draw without blur).
     */
    public long recordBackdrop() {
        if (!frameOpen || !glassEnabled || SKIP_BLUR || !backend.supportsFrostedGlass()) {
            return 0L;
        }
        try {
            return backend.recordBackdrop();
        } catch (RuntimeException exception) {
            LOGGER.warn("Frosted glass failed on {}; continuing without blur",
                    backend.type().displayName(), exception);
            configureFrostedGlass(false, glassStyle);
            return 0L;
        }
    }

    /** Ends the frame ({@code ImGui.render()}) and hands the draw data to the backend. */
    public void endFrame() {
        if (!frameOpen) {
            return;
        }
        frameOpen = false;
        ImGui.render();
        if (backend != null && backend.isReadyToRender()) {
            backend.render(ImGui.getDrawData());
        }
        updateTextInput(ImGui.getIO().getWantTextInput());
    }

    /** Starts the platform's text input while an ImGui text field is focused (SDL, 26.3+). */
    private void updateTextInput(boolean wanted) {
        if (wanted != textInputActive) {
            textInputActive = wanted;
            compat.setTextInputActive(wanted);
        }
    }

    public static void imGuiFrame(int width, int height, float deltaTime) {
        ImGuiIO io = ImGui.getIO();
        io.setDisplaySize(width, height);
        io.setDeltaTime(deltaTime > 0.0f ? deltaTime : 0.005f);
        ImGui.newFrame();
    }

    private boolean ensureBackend() {
        if (backend == null) {
            Optional<RenderBackendType> detected = compat.renderBackend();
            if (detected.isEmpty()) {
                return false;
            }
            RenderBackendType type = detected.get();
            backend = registry.create(type, compat, BackendDiagnostics.prefix(
                    compat.minecraftVersion(), type, MicaVersion.get()));
            backend.configureFrostedGlass(glassEnabled, glassStyle);
            LOGGER.info("Mica detected Minecraft's {} backend; using the {} renderer.",
                    type.displayName(), type.displayName());
        }
        if (!backend.isInitialized()) {
            ensureContext();
            boolean ready;
            try {
                ready = backend.initialize();
            } catch (MicaBackendException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new MicaBackendException(diagnostics() + " Reason: initialising the "
                        + backend.type().displayName() + " renderer failed: " + exception,
                        backend.type(), exception);
            }
            if (!ready) {
                return false;
            }
            textures.setAvailable(true);
            LOGGER.info("Mica renderer initialised: {}", backend.describe());
        }
        return true;
    }

    private void ensureContext() {
        if (contextCreated) {
            return;
        }
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        ImGuiFonts.load(fontRegistries);
        contextCreated = true;
    }

    private void fail(MicaBackendException exception) {
        failure = exception;
        LOGGER.error("{} The overlay is disabled for this session.", exception.getMessage(),
                exception);
        if (frameOpen) {
            try {
                ImGui.endFrame();
            } catch (RuntimeException ignored) {
                // The context may be half-initialised; nothing more to unwind.
            }
            frameOpen = false;
        }
    }

    private RenderBackendType currentType() {
        return activeBackend().orElse(RenderBackendType.UNKNOWN);
    }

    private String diagnostics() {
        return BackendDiagnostics.prefix(compat.minecraftVersion(), currentType(), MicaVersion.get());
    }

    // ---- textures ----------------------------------------------------------------------

    /** A long-lived, ref-counted texture for a Minecraft texture or atlas. */
    @NotNull
    public MicaTexture texture(@NotNull Identifier textureId, @NotNull TextureFilter filter) {
        return textures.acquire(textureId, filter);
    }

    /** Registers a raw backend handle (deprecated raw-texture API). */
    public long registerNativeTexture(long nativeHandle, int nativeLayout, @NotNull TextureFilter filter) {
        if (!isEnabled() || !compat.isOnRenderThread()) {
            return 0L;
        }
        return backend.registerNativeTexture(nativeHandle, nativeLayout, filter);
    }

    /** {@code true} once the backend has everything the draw data needs (the font atlas). */
    public boolean isFontTextureReady() {
        return isEnabled() && backend.isReadyToRender();
    }

    // ---- teardown ----------------------------------------------------------------------

    /**
     * Destroys every GPU resource and the ImGui context. The renderer can start again on the
     * next {@link #beginFrame()}.
     */
    public void shutdown() {
        if (frameOpen) {
            ImGui.endFrame();
            frameOpen = false;
        }
        updateTextInput(false);
        if (backend != null) {
            // Outstanding MicaTextures stay usable: they re-register after a restart.
            textures.setAvailable(false);
            backend.shutdown();
            backend = null;
        }
        if (contextCreated) {
            ImGui.destroyContext();
            ImGuiFonts.reset();
            contextCreated = false;
        }
        viewport = null;
        lastFrameNanos = 0L;
        failure = null;
    }

    // ---- per-frame values for RenderContext ---------------------------------------------

    public float viewportWidthOrDefault() {
        return viewport != null ? viewport.width() : 1.0f;
    }

    public float viewportHeightOrDefault() {
        return viewport != null ? viewport.height() : 1.0f;
    }

    public float currentDeltaTime() {
        return ImGui.getIO().getDeltaTime();
    }

    @NotNull
    public ImDrawList backgroundDrawList() {
        return ImGui.getBackgroundDrawList();
    }
}
