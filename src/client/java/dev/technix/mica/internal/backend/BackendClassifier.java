package dev.technix.mica.internal.backend;

import dev.technix.mica.api.RenderBackendType;
import org.jetbrains.annotations.NotNull;

/**
 * Classifies Minecraft's {@code GpuDeviceBackend} implementation by its package. Used by the
 * compat adapters because some backend classes (OpenGL's {@code GlDevice} since 26.1) are
 * package-private and cannot be named in an {@code instanceof}.
 */
public final class BackendClassifier {

    private static final String VULKAN_PACKAGE = "com.mojang.blaze3d.vulkan.";
    private static final String OPENGL_PACKAGE = "com.mojang.blaze3d.opengl.";

    private BackendClassifier() {
    }

    @NotNull
    public static RenderBackendType classify(@NotNull String deviceBackendClassName) {
        if (deviceBackendClassName.startsWith(VULKAN_PACKAGE)) {
            return RenderBackendType.VULKAN;
        }
        if (deviceBackendClassName.startsWith(OPENGL_PACKAGE)) {
            return RenderBackendType.OPENGL;
        }
        return RenderBackendType.UNKNOWN;
    }
}
