package dev.technix.mica.api;

import org.jetbrains.annotations.NotNull;

/**
 * Thrown (and logged) when Mica cannot bring up a renderer for the backend Minecraft is
 * using. The message always names the Minecraft version, the detected rendering backend,
 * the Mica version and the reason.
 */
public final class MicaBackendException extends RuntimeException {

    private final RenderBackendType backend;

    public MicaBackendException(@NotNull String message, @NotNull RenderBackendType backend) {
        super(message);
        this.backend = backend;
    }

    public MicaBackendException(@NotNull String message, @NotNull RenderBackendType backend,
                                Throwable cause) {
        super(message, cause);
        this.backend = backend;
    }

    @NotNull
    public RenderBackendType backend() {
        return backend;
    }
}
