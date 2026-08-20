package dev.technix.mica.api;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;


public record TextureHandle(@NotNull Identifier atlasId, long imGuiTextureId) {
}
