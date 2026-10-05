package dev.technix.mica.internal.backend.opengl;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the OpenGL renderer needs from Minecraft. Implemented by the version compat adapter
 * and obtained through {@code MinecraftCompat.backendAccess(OpenGLHostAccess.class)}.
 *
 * <p>Mica never creates a GL context: every call happens on Minecraft's render thread,
 * where Minecraft's own context is current.
 */
public interface OpenGLHostAccess {

    /** Minecraft's main render target, or {@code null} if it does not exist or is not OpenGL. */
    @Nullable
    MainTarget mainRenderTarget();

    /** The OpenGL texture name of a Minecraft texture, {@code 0} if missing. */
    int glTextureIdFor(@NotNull Identifier textureId);

    /**
     * @param colorTexture GL texture name of the target's colour attachment
     */
    record MainTarget(int colorTexture, int width, int height) {
    }
}
