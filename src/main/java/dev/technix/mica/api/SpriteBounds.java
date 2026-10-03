package dev.technix.mica.api;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;


public record SpriteBounds(@NotNull Identifier atlasId, float u0, float v0, float u1, float v1) {
}
