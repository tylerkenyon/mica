package dev.technix.mica.api;

/**
 * An opaque, backend-independent texture that can be drawn through Dear ImGui.
 *
 * <p>Obtain one from {@link OverlayRenderer#texture(net.minecraft.resources.Identifier, TextureFilter)}
 * and pass {@link #imGuiTextureId()} to {@code ImGui.image(...)}, {@code ImDrawList.addImage(...)}
 * or {@code Draw.image(...)}. The id is Dear ImGui's own opaque {@code ImTextureID}: it is
 * not a Vulkan descriptor set or an OpenGL texture name you can use outside ImGui, and its
 * meaning differs between backends.
 *
 * <p>Lifetime: the texture is owned by the {@link OverlayRenderer} that created it. It
 * follows Minecraft re-creating the underlying image (resource reloads, resizes) on its
 * own, so the same object stays usable for the whole session. Call {@link #close()} when
 * you no longer need it. Once closed, {@link #imGuiTextureId()} returns {@code 0} and drawing
 * helpers skip it. While the renderer is shut down (or not initialised yet) the id is also
 * {@code 0}; an unclosed texture becomes drawable again when the renderer restarts.
 *
 * <p>Threading: read the id from inside {@link OverlayElement#render(RenderContext)} (the
 * render thread). {@link #close()} may be called from any thread; the GPU-side release is
 * deferred to the render thread.
 */
public interface MicaTexture extends AutoCloseable {

    /**
     * The Dear ImGui texture id for the current frame, or {@code 0} if the texture is not
     * (yet) available, for example before the renderer initialised or after {@link #close()}.
     */
    long imGuiTextureId();

    /** {@code true} when {@link #imGuiTextureId()} would currently return a drawable id. */
    default boolean isValid() {
        return imGuiTextureId() != 0L;
    }

    /** Releases the texture. Idempotent; safe from any thread. */
    @Override
    void close();
}
