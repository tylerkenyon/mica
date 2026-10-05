package dev.technix.mica.internal.backend;

import dev.technix.mica.api.MicaBackendException;
import dev.technix.mica.api.RenderBackendType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps a detected {@link RenderBackendType} to the factory that builds its
 * {@link RenderBackend}. Adding a backend means registering one more factory here; the core
 * never names a concrete backend.
 *
 * @param <C> what a factory needs to build a backend (the {@code MinecraftCompat} in production)
 */
public final class RenderBackendRegistry<C> {

    private final Map<RenderBackendType, Function<C, RenderBackend>> factories =
            new EnumMap<>(RenderBackendType.class);

    @NotNull
    public RenderBackendRegistry<C> register(@NotNull RenderBackendType type,
                                             @NotNull Function<C, RenderBackend> factory) {
        if (type == RenderBackendType.UNKNOWN) {
            throw new IllegalArgumentException("Cannot register a renderer for UNKNOWN");
        }
        factories.put(type, factory);
        return this;
    }

    @NotNull
    public Map<RenderBackendType, Function<C, RenderBackend>> factories() {
        return Collections.unmodifiableMap(factories);
    }

    /**
     * Builds the renderer for {@code detected}. Never falls back to a different backend:
     * the renderer must match what Minecraft is actually using.
     *
     * @param diagnostics prefix naming the Minecraft and Mica versions, added to every error
     * @throws MicaBackendException if there is no renderer for the backend, or the factory fails
     */
    @NotNull
    public RenderBackend create(@NotNull RenderBackendType detected, @NotNull C context,
                                @NotNull String diagnostics) {
        Function<C, RenderBackend> factory = factories.get(detected);
        if (factory == null) {
            throw new MicaBackendException(diagnostics + " Reason: Mica has no renderer for the "
                    + detected.displayName() + " backend (supported: " + factories.keySet() + ").",
                    detected);
        }
        try {
            return Optional.ofNullable(factory.apply(context)).orElseThrow(() ->
                    new MicaBackendException(diagnostics + " Reason: the "
                            + detected.displayName() + " renderer factory returned null.", detected));
        } catch (MicaBackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MicaBackendException(diagnostics + " Reason: creating the "
                    + detected.displayName() + " renderer failed: " + exception, detected, exception);
        }
    }
}
