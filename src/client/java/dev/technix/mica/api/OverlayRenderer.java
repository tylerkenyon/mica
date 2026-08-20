package dev.technix.mica.api;

import dev.technix.mica.internal.ActiveRenderers;
import dev.technix.mica.internal.ImGuiFonts;
import dev.technix.mica.internal.ImGuiRenderer;
import dev.technix.mica.internal.ScreenDetector;
import dev.technix.mica.internal.backend.vulkan.FrostedGlassRenderer;
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

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;


public final class OverlayRenderer implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final MinecraftCompat compat;
    private final FrostedGlassRenderer frostedGlass;
    private final ImGuiRenderer renderer;
    private final List<FontRegistry> fontRegistries;
    private final List<OverlayElement> elements = new ArrayList<>();
    private final List<OverlayElement> failed = new ArrayList<>();
    private final List<TextureRegistration> registrations = new ArrayList<>();


    private volatile FrostedGlassStyle frostedGlassStyle = FrostedGlassStyle.DEFAULT;


    private static final class TextureRegistration {
        final Identifier atlasId;
        final TextureFilter filter;
        long cachedImageView;
        long cachedTextureId;

        TextureRegistration(Identifier atlasId, TextureFilter filter) {
            this.atlasId = atlasId;
            this.filter = filter;
        }
    }

    private OverlayRenderer(MinecraftCompat compat, FrostedGlassRenderer frostedGlass,
                            ImGuiRenderer renderer, List<FontRegistry> fontRegistries,
                            FrostedGlassStyle glassStyle) {
        this.compat = Objects.requireNonNull(compat, "compat");
        this.frostedGlass = frostedGlass;
        this.renderer = renderer;
        this.fontRegistries = List.copyOf(fontRegistries);
        this.frostedGlassStyle = Objects.requireNonNull(glassStyle, "glassStyle");
        if (frostedGlass != null) {
            frostedGlass.applyStyle(glassStyle);
        }
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


    @NotNull
    public Optional<TextureHandle> registerAtlasTexture(@NotNull Identifier atlasId,
                                                         @NotNull TextureFilter filter) {
        Objects.requireNonNull(atlasId, "atlasId");
        Objects.requireNonNull(filter, "filter");
        long imageView = compat.vkImageViewFor(atlasId);
        if (imageView == 0L) {
            return Optional.empty();
        }
        TextureRegistration reg = registrationFor(atlasId, filter);
        if (reg.cachedImageView == imageView && reg.cachedTextureId != 0L) {
            return Optional.of(new TextureHandle(atlasId, reg.cachedTextureId));
        }
        long texId = renderer.registerTexture(imageView, VK_IMAGE_LAYOUT_GENERAL, filter);
        if (texId == 0L) {
            return Optional.empty();
        }
        reg.cachedImageView = imageView;
        reg.cachedTextureId = texId;
        return Optional.of(new TextureHandle(atlasId, texId));
    }

    private TextureRegistration registrationFor(Identifier atlasId, TextureFilter filter) {
        for (TextureRegistration reg : registrations) {
            if (reg.atlasId.equals(atlasId) && reg.filter == filter) {
                return reg;
            }
        }
        TextureRegistration reg = new TextureRegistration(atlasId, filter);
        registrations.add(reg);
        return reg;
    }


    public long registerRawTexture(long imageView, int imageLayout, @NotNull TextureFilter filter) {
        return renderer.registerTexture(imageView, imageLayout, filter);
    }


    public boolean prepareForFrame() {
        if (!compat.isVulkanRendererActive()) {
            return false;
        }
        try {
            renderer.refreshVulkanContext();
            renderer.beginFrame();
            renderer.recordPendingTransfers();
            return true;
        } catch (Throwable t) {
            LOGGER.error("ImGui prepareForFrame failed; disabling overlay", t);
            close();
            return false;
        }
    }


    public void renderOverlay() {
        if (renderer.getVulkanContext() == null) {
            return;
        }
        try {
            boolean blurred = frostedGlass != null && renderer.recordFrostedGlassBlur();

            renderer.beginElementsFrame();
            RenderContext context = newRenderContext();

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
            renderer.endElementsFrame();

            if (!renderer.isFontTextureReady()) {
                return;
            }

            Optional<ImGuiRenderer.HostRenderTarget> target = renderer.currentHostRenderTarget(compat);
            if (target.isEmpty()) {
                return;
            }
            renderer.submit(context, target.get(), blurred);
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


    private @NotNull RenderContext newRenderContext() {
        long blurTextureId = 0L;
        if (frostedGlass != null && frostedGlass.isBlurTargetReady()) {
            blurTextureId = frostedGlass.getImGuiTextureId();
        }
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
        if (frostedGlass != null) {
            frostedGlass.applyStyle(style);
        }
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

    @Override
    public void close() {
        try {
            if (frostedGlass != null) {
                frostedGlass.cleanup();
            }
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
            if (compat == null) {
                throw new IllegalStateException(
                        "Builder requires a MinecraftCompat. Call withMinecraftCompat(...) first.");
            }
            List<FontRegistry> allRegistries = new ArrayList<>(
                    ActiveRenderers.fontRegistries().size() + fontRegistries.size());
            allRegistries.addAll(ActiveRenderers.fontRegistries());
            allRegistries.addAll(fontRegistries);
            
            
            ActiveRenderers.consumePending();

            ImGuiRenderer renderer = new ImGuiRenderer(compat);
            FrostedGlassRenderer glass = frostedGlass ? new FrostedGlassRenderer() : null;
            if (glass != null) {
                renderer.attachFrostedGlass(glass);
            }
            return new OverlayRenderer(compat, glass, renderer, allRegistries, frostedGlassStyle);
        }
    }
}
