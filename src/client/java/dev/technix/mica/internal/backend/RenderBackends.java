package dev.technix.mica.internal.backend;

import dev.technix.mica.api.MicaBackendException;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.internal.backend.opengl.OpenGLHostAccess;
import dev.technix.mica.internal.backend.opengl.OpenGLRenderBackend;
import dev.technix.mica.internal.backend.vulkan.VulkanHostAccess;
import dev.technix.mica.internal.backend.vulkan.VulkanRenderBackend;
import org.jetbrains.annotations.NotNull;

/**
 * The single place that wires concrete backends to {@link RenderBackendType}s.
 */
public final class RenderBackends {

    private RenderBackends() {
    }

    @NotNull
    public static RenderBackendRegistry<MinecraftCompat> defaultRegistry() {
        return new RenderBackendRegistry<MinecraftCompat>()
                .register(RenderBackendType.VULKAN, compat ->
                        new VulkanRenderBackend(require(compat, VulkanHostAccess.class,
                                RenderBackendType.VULKAN)))
                .register(RenderBackendType.OPENGL, compat ->
                        new OpenGLRenderBackend(require(compat, OpenGLHostAccess.class,
                                RenderBackendType.OPENGL)));
    }

    private static <T> T require(MinecraftCompat compat, Class<T> access, RenderBackendType type) {
        return compat.backendAccess(access).orElseThrow(() -> new MicaBackendException(
                compat.getClass().getSimpleName() + " does not provide " + access.getSimpleName()
                        + ", which the " + type.displayName() + " renderer needs.", type));
    }
}
