package dev.technix.mica.internal.backend;

import dev.technix.mica.api.RenderBackendType;
import org.jetbrains.annotations.NotNull;

/**
 * Formats the context line every backend error starts with.
 */
public final class BackendDiagnostics {

    private BackendDiagnostics() {
    }

    @NotNull
    public static String prefix(@NotNull String minecraftVersion, @NotNull RenderBackendType backend,
                                @NotNull String micaVersion) {
        return "Mica could not start its renderer (Minecraft " + minecraftVersion
                + ", rendering backend " + backend.displayName() + ", Mica " + micaVersion + ").";
    }
}
