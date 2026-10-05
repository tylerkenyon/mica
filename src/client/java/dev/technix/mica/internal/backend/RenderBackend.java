package dev.technix.mica.internal.backend;

import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.TextureFilter;
import imgui.ImDrawData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One GPU implementation of Mica's renderer (Vulkan, OpenGL, ...).
 *
 * <p>The core ({@code ImGuiRenderer}) owns the Dear ImGui context, the frame lifecycle, input
 * and textures, and drives a backend through this interface only. Every method is called
 * on Minecraft's render thread with the ImGui context current.
 *
 * <p>Per frame the core calls, in order: {@link #beginFrame()}, {@code ImGui.newFrame()},
 * {@link #prepareFrame()}, {@link #recordBackdrop()}, the overlay elements,
 * {@code ImGui.render()}, then {@link #render(ImDrawData)} if {@link #isReadyToRender()}.
 */
public interface RenderBackend {

    @NotNull
    RenderBackendType type();

    /**
     * Creates the backend's GPU objects (pipeline, font atlas texture from
     * {@code ImGui.getIO().getFonts()}, ...). Returns {@code false} if the host is not
     * ready yet; the core retries next frame. Throws on a fatal error.
     */
    boolean initialize();

    boolean isInitialized();

    /**
     * Refreshes host state (render target, size) for the coming frame. Returns the
     * framebuffer size to drive ImGui with, or {@code null} to skip the frame.
     */
    @Nullable
    Viewport beginFrame();

    /** Records work that must precede the frame's draws (for example deferred uploads). */
    void prepareFrame();

    /**
     * Produces this frame's blurred backdrop if frosted glass is enabled and supported.
     * Returns its ImGui texture id, or {@code 0} if there is no backdrop this frame.
     */
    long recordBackdrop();

    /** {@code false} while resources the draw data depends on (the font atlas) are pending. */
    boolean isReadyToRender();

    /** Draws the frame's ImGui output on top of Minecraft's main render target. */
    void render(@NotNull ImDrawData drawData);

    /**
     * An identity for the host texture behind {@code textureId} (a Vulkan image view, an
     * OpenGL texture name, ...). {@code 0} if it does not exist. The value changes when
     * Minecraft re-creates the texture, which is how the core notices it must re-register.
     */
    long hostTextureHandle(@NotNull Identifier textureId);

    /** Makes a host texture drawable by ImGui. Returns the ImGui texture id, {@code 0} on failure. */
    long registerTexture(long hostTextureHandle, @NotNull TextureFilter filter);

    /**
     * Registers a raw backend texture handle with a backend-specific layout hint (the Vulkan
     * image layout; ignored elsewhere). Backs the deprecated
     * {@code OverlayRenderer.registerRawTexture}.
     */
    default long registerNativeTexture(long nativeHandle, int nativeLayout, @NotNull TextureFilter filter) {
        return registerTexture(nativeHandle, filter);
    }

    /** Releases an id returned by {@link #registerTexture(long, TextureFilter)}. */
    void releaseTexture(long imGuiTextureId);

    /** Enables/disables frosted glass and applies its style. May be called before {@link #initialize()}. */
    void configureFrostedGlass(boolean enabled, @NotNull FrostedGlassStyle style);

    /** Whether this backend can produce a blurred backdrop at all. */
    boolean supportsFrostedGlass();

    /** Short description of the device for diagnostics (driver, GPU, ...). */
    @NotNull
    default String describe() {
        return type().displayName();
    }

    /** Destroys every GPU object. The backend may be initialised again afterwards. */
    void shutdown();

    record Viewport(int width, int height) {
    }
}
