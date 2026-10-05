package dev.technix.mica.api;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * A snapshot of an atlas texture registration, as returned by
 * {@link OverlayRenderer#registerAtlasTexture(Identifier, TextureFilter)}.
 *
 * <p>The id is only guaranteed for the frame it was obtained in; call
 * {@code registerAtlasTexture} again each frame (it is cached) or prefer the long-lived
 * {@link OverlayRenderer#texture(Identifier, TextureFilter)}. {@link #close()} is a no-op:
 * the registration is owned by the renderer.
 */
public record TextureHandle(@NotNull Identifier atlasId, long imGuiTextureId) implements MicaTexture {

    @Override
    public void close() {
    }
}
