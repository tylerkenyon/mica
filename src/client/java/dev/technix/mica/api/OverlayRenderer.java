package dev.technix.mica.api;

import dev.technix.mica.internal.ActiveRenderers;
import dev.technix.mica.internal.ImGuiFonts;
import dev.technix.mica.internal.ImGuiRenderer;
import dev.technix.mica.internal.ScreenDetector;
import dev.technix.mica.internal.backend.RenderBackends;
import dev.technix.mica.internal.util.Theme;
import imgui.ImDrawList;
import imgui.ImGui;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * The overlay render loop. Owns the registered {@link OverlayElement}s and Mica's renderer,
 * which drives whichever GPU backend Minecraft is running (Vulkan or OpenGL, picked
 * automatically). Nothing on this class depends on the backend.
 *
 * <p>Most mods should use {@link Mica#create()}, which builds and installs one of these.
 *
 * <p>Threading: elements are rendered on Minecraft's render thread only.
 * {@link #registerElement(OverlayElement)}, {@link #unregisterElement(OverlayElement)} and
 * {@link MicaTexture#close()} are safe from any thread.
 */
public final class OverlayRenderer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final MinecraftCompat compat;
    private final boolean frostedGlass;
    private final ImGuiRenderer renderer;
    private final List<OverlayElement> elements = new CopyOnWriteArrayList<>();
    private final Set<OverlayElement> failed = ConcurrentHashMap.newKeySet();
    private final List<AtlasRegistration> registrations = new CopyOnWriteArrayList<>();


    private volatile FrostedGlassStyle frostedGlassStyle = FrostedGlassStyle.DEFAULT;


    private record AtlasRegistration(Identifier atlasId, TextureFilter filter, MicaTexture texture) {
    }

    private OverlayRenderer(MinecraftCompat compat, boolean frostedGlass, ImGuiRenderer renderer,
                            FrostedGlassStyle glassStyle) {
        this.compat = Objects.requireNonNull(compat, "compat");
        this.frostedGlass = frostedGlass;
        this.renderer = renderer;
        this.frostedGlassStyle = Objects.requireNonNull(glassStyle, "glassStyle");
        renderer.configureFrostedGlass(frostedGlass, glassStyle);
    }


    public MinecraftCompat minecraftCompat() {
        return compat;
    }

    @NotNull
    public static Builder builder() {
        return new Builder();
    }


    public void registerElement(@NotNull OverlayElement element) {
        Objects.requireNonNull(element, "element");
        elements.add(element);
        LOGGER.debug("Registered overlay element {}", element.name());
    }

    public void unregisterElement(@NotNull OverlayElement element) {
        elements.remove(element);
        failed.remove(element);
    }


    @NotNull
    public List<OverlayElement> elements() {
        return List.copyOf(elements);
    }


    /**
     * A long-lived texture for a Minecraft texture or atlas ({@code minecraft:textures/atlas/items.png},
     * ...). The returned {@link MicaTexture} follows Minecraft re-creating the image and works
     * on every backend; {@link MicaTexture#close() close} it when done.
     */
    @NotNull
    public MicaTexture texture(@NotNull Identifier textureId, @NotNull TextureFilter filter) {
        Objects.requireNonNull(textureId, "textureId");
        Objects.requireNonNull(filter, "filter");
        return renderer.texture(textureId, filter);
    }

    /**
     * The current ImGui texture id for an atlas, cached per (atlas, filter) for the lifetime
     * of this renderer. Empty while the texture or the renderer is not available.
     *
     * @see #texture(Identifier, TextureFilter)
     */
    @NotNull
    public Optional<TextureHandle> registerAtlasTexture(@NotNull Identifier atlasId,
                                                         @NotNull TextureFilter filter) {
        Objects.requireNonNull(atlasId, "atlasId");
        Objects.requireNonNull(filter, "filter");
        long texId = registrationFor(atlasId, filter).texture().imGuiTextureId();
        if (texId == 0L) {
            return Optional.empty();
        }
        return Optional.of(new TextureHandle(atlasId, texId));
    }

    private AtlasRegistration registrationFor(Identifier atlasId, TextureFilter filter) {
        for (AtlasRegistration reg : registrations) {
            if (reg.atlasId().equals(atlasId) && reg.filter() == filter) {
                return reg;
            }
        }
        synchronized (registrations) {
            for (AtlasRegistration reg : registrations) {
                if (reg.atlasId().equals(atlasId) && reg.filter() == filter) {
                    return reg;
                }
            }
            AtlasRegistration reg = new AtlasRegistration(atlasId, filter,
                    renderer.texture(atlasId, filter));
            registrations.add(reg);
            return reg;
        }
    }


    /**
     * Registers a raw backend texture handle (a Vulkan {@code VkImageView} plus image layout).
     *
     * @deprecated backend-specific; works only while Minecraft runs Vulkan and returns
     *             {@code 0} otherwise. Use {@link #texture(Identifier, TextureFilter)}.
     */
    @Deprecated(forRemoval = false)
    public long registerRawTexture(long imageView, int imageLayout, @NotNull TextureFilter filter) {
        if (activeBackend().orElse(RenderBackendType.UNKNOWN) != RenderBackendType.VULKAN) {
            return 0L;
        }
        return renderer.registerNativeTexture(imageView, imageLayout, filter);
    }

    /**
     * The rendering backend Minecraft is using, or empty before Minecraft created its GPU
     * device. Informational only: overlays never need to branch on it.
     */
    @NotNull
    public Optional<RenderBackendType> activeBackend() {
        return renderer.activeBackend();
    }

    /** Why the renderer stopped, if it could not start on this run's backend. */
    @NotNull
    public Optional<MicaBackendException> failure() {
        return renderer.failure();
    }


    /** Render thread, before Minecraft's GUI draws. Starts the ImGui frame. */
    public boolean prepareForFrame() {
        try {
            return renderer.beginFrame();
        } catch (Throwable t) {
            LOGGER.error("ImGui prepareForFrame failed; disabling overlay", t);
            close();
            return false;
        }
    }


    /** Render thread, after Minecraft's GUI drew. Runs every element and submits the frame. */
    public void renderOverlay() {
        if (!renderer.isFrameOpen()) {
            return;
        }
        try {
            long blurTextureId = frostedGlass ? renderer.recordBackdrop() : 0L;
            RenderContext context = newRenderContext(blurTextureId);

            MicaScreen currentScreen = ScreenDetector.current();
            for (OverlayElement element : elements) {
                if (failed.contains(element)) {
                    continue;
                }
                MicaScreen scope = element.renderScope();
                if (scope != MicaScreen.ANY && scope != currentScreen) {
                    continue;
                }
                try {
                    if (element.isVisible(context)) {
                        element.render(context);
                    }
                } catch (Throwable t) {
                    failed.add(element);
                    LOGGER.error("Disabling overlay element {} after a failure", element.name(), t);
                }
            }
            renderer.endFrame();
        } catch (Throwable t) {
            LOGGER.error("ImGui renderOverlay failed; disabling overlay", t);
            close();
        }
    }

    @NotNull
    public Palette palette() {
        return Theme.palette();
    }


    @NotNull
    public ImGuiRenderer internalRenderer() {
        return renderer;
    }


    @NotNull
    public Fonts fonts() {
        return new Fonts();
    }


    private @NotNull RenderContext newRenderContext(long blurTextureId) {
        float width = renderer.viewportWidthOrDefault();
        float height = renderer.viewportHeightOrDefault();
        float deltaTime = renderer.currentDeltaTime();
        return new RenderContext(renderer.backgroundDrawList(), width, height,
                blurTextureId, deltaTime, frostedGlassStyle, this);
    }

    @org.jetbrains.annotations.Nullable
    public FontFace font(@NotNull String name) {
        return ImGuiFonts.byName(name);
    }


    @NotNull
    public FrostedGlassStyle glassStyle() {
        return frostedGlassStyle;
    }


    public void setGlassStyle(@NotNull FrostedGlassStyle style) {
        this.frostedGlassStyle = Objects.requireNonNull(style, "style");
        renderer.configureFrostedGlass(frostedGlass, style);
    }

    public static final class Fonts {
        public boolean push(@NotNull FontFace face) {
            return ImGuiFonts.push(face);
        }

        public void pop(boolean pushed) {
            ImGuiFonts.pop(pushed);
        }

        @NotNull
        public FontFace regular() {
            return ImGuiFonts.regular();
        }

        @NotNull
        public FontFace medium() {
            return ImGuiFonts.medium();
        }

        @NotNull
        public FontFace bold() {
            return ImGuiFonts.bold();
        }

        @NotNull
        public FontFace logo() {
            return ImGuiFonts.logo();
        }
    }

    public static boolean pushFont(@NotNull FontFace face) {
        return ImGuiFonts.push(face);
    }

    public static void popFont(boolean pushed) {
        ImGuiFonts.pop(pushed);
    }

    public static void text(@NotNull ImDrawList drawList, @NotNull FontFace face, int color,
                            @NotNull String text, float x, float y) {
        boolean pushed = ImGuiFonts.push(face);
        drawList.addText(x, y, color, text);
        ImGuiFonts.pop(pushed);
    }

    public static void imGuiNewFrame(int width, int height, float deltaTime) {
        ImGuiRenderer.imGuiFrame(width, height, deltaTime);
    }

    /** Releases every GPU resource. Call on the render thread (for example from CLIENT_STOPPING). */
    @Override
    public void close() {
        try {
            renderer.shutdown();
        } catch (Throwable t) {
            LOGGER.error("Error during Renderer shutdown", t);
        }
    }

    
    public static final class Builder {

        private MinecraftCompat compat;
        private boolean frostedGlass = true;
        private FrostedGlassStyle frostedGlassStyle = FrostedGlassStyle.DEFAULT;
        private final List<FontRegistry> fontRegistries = new ArrayList<>();

        private Builder() {
        }

        
        @NotNull
        public static Builder create() {
            return new Builder();
        }

        
        /**
         * Overrides the version adapter. Optional: {@link MinecraftCompat#detect()} is used
         * when none is given.
         */
        @NotNull
        public Builder withMinecraftCompat(@NotNull MinecraftCompat compat) {
            this.compat = Objects.requireNonNull(compat, "compat");
            return this;
        }

        
        @NotNull
        public Builder withFrostedGlass(boolean enabled) {
            this.frostedGlass = enabled;
            return this;
        }

        
        @NotNull
        public Builder withFrostedGlassStyle(@NotNull FrostedGlassStyle style) {
            this.frostedGlassStyle = Objects.requireNonNull(style, "style");
            return this;
        }

 
        @NotNull
        public Builder withFontRegistry(@NotNull FontRegistry registry) {
            this.fontRegistries.add(Objects.requireNonNull(registry, "registry"));
            return this;
        }


        @NotNull
        public Builder withFontRegistries(@NotNull List<FontRegistry> registries) {
            for (FontRegistry registry : registries) {
                this.fontRegistries.add(Objects.requireNonNull(registry, "registry"));
            }
            return this;
        }


        @NotNull
        public OverlayRenderer build() {
            MinecraftCompat resolved = compat != null ? compat : MinecraftCompat.detect();
            List<FontRegistry> allRegistries = new ArrayList<>(
                    ActiveRenderers.fontRegistries().size() + fontRegistries.size());
            allRegistries.addAll(ActiveRenderers.fontRegistries());
            allRegistries.addAll(fontRegistries);
            
            
            ActiveRenderers.consumePending();

            ImGuiRenderer renderer = new ImGuiRenderer(resolved, allRegistries,
                    RenderBackends.defaultRegistry());
            return new OverlayRenderer(resolved, frostedGlass, renderer, frostedGlassStyle);
        }
    }
}
