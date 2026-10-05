package dev.technix.mica.api;

import dev.technix.mica.internal.ActiveRenderers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Mica's entry point. Works the same whether Minecraft renders with Vulkan or OpenGL: the
 * matching renderer is picked automatically the first time Minecraft draws a frame.
 *
 * <pre>{@code
 * public void onInitializeClient() {
 *     Mica mica = Mica.create();
 *     mica.registerOverlay(ctx -> {
 *         ImGui.begin("My Mod");
 *         ImGui.text("Hello Minecraft!");
 *         if (ImGui.button("Click me")) {
 *             // ...
 *         }
 *         ImGui.end();
 *     });
 * }
 * }</pre>
 *
 * <p>{@link #create()} installs the instance as the active overlay (the bundled mixins render
 * it and route input to it) and closes it when the client stops. Only one Mica instance is
 * active at a time; creating another replaces the previous one.
 */
public final class Mica implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final OverlayRenderer renderer;
    private final AtomicInteger anonymousOverlays = new AtomicInteger();
    private volatile boolean closed;

    private Mica(OverlayRenderer renderer) {
        this.renderer = renderer;
    }

    /** Builds and installs a Mica instance with default settings (frosted glass on). */
    @NotNull
    public static Mica create() {
        return builder().build();
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Registers a lambda overlay, drawn every frame on every screen. */
    @NotNull
    public OverlayElement registerOverlay(@NotNull MicaOverlay overlay) {
        return registerOverlay("overlay-" + anonymousOverlays.incrementAndGet(), overlay);
    }

    /** Registers a named lambda overlay (the name appears in logs if it ever throws). */
    @NotNull
    public OverlayElement registerOverlay(@NotNull String name, @NotNull MicaOverlay overlay) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(overlay, "overlay");
        OverlayElement element = new OverlayElement() {
            @Override
            public @NotNull String name() {
                return name;
            }

            @Override
            public void render(@NotNull RenderContext context) {
                overlay.render(context);
            }
        };
        renderer.registerElement(element);
        return element;
    }

    /** Registers a full {@link OverlayElement} (screen scope, visibility, ...). */
    @NotNull
    public <T extends OverlayElement> T registerOverlay(@NotNull T element) {
        renderer.registerElement(element);
        return element;
    }

    public void unregisterOverlay(@NotNull OverlayElement element) {
        renderer.unregisterElement(element);
    }

    /** A backend-independent texture for a Minecraft texture or atlas. */
    @NotNull
    public MicaTexture texture(@NotNull Identifier textureId, @NotNull TextureFilter filter) {
        return renderer.texture(textureId, filter);
    }

    /** The underlying renderer, for fonts, glass style, palette and texture helpers. */
    @NotNull
    public OverlayRenderer renderer() {
        return renderer;
    }

    /**
     * The rendering backend Minecraft is using, or empty before it created its GPU device.
     * For diagnostics only.
     */
    @NotNull
    public Optional<RenderBackendType> backend() {
        return renderer.activeBackend();
    }

    /** Uninstalls and releases everything. Call on the render thread; idempotent. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (ActiveRenderers.get() == renderer) {
            ActiveRenderers.set(null);
        }
        renderer.close();
    }

    public static final class Builder {

        private final OverlayRenderer.Builder renderer = OverlayRenderer.builder();
        private boolean closeOnClientStop = true;

        private Builder() {
        }

        @NotNull
        public Builder frostedGlass(boolean enabled) {
            renderer.withFrostedGlass(enabled);
            return this;
        }

        @NotNull
        public Builder frostedGlassStyle(@NotNull FrostedGlassStyle style) {
            renderer.withFrostedGlassStyle(style);
            return this;
        }

        @NotNull
        public Builder fontRegistry(@NotNull FontRegistry registry) {
            renderer.withFontRegistry(registry);
            return this;
        }

        /** Overrides the auto-detected Minecraft version adapter. Rarely needed. */
        @NotNull
        public Builder minecraftCompat(@NotNull MinecraftCompat compat) {
            renderer.withMinecraftCompat(compat);
            return this;
        }

        /** Whether to close automatically on Fabric's {@code CLIENT_STOPPING}. Default {@code true}. */
        @NotNull
        public Builder closeOnClientStop(boolean enabled) {
            this.closeOnClientStop = enabled;
            return this;
        }

        @NotNull
        public Mica build() {
            Mica mica = new Mica(renderer.build());
            OverlayRenderer previous = ActiveRenderers.get();
            if (previous != null) {
                LOGGER.warn("A Mica overlay was already active; the new instance replaces it.");
            }
            ActiveRenderers.set(mica.renderer);
            if (closeOnClientStop) {
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> mica.close());
            }
            return mica;
        }
    }
}
