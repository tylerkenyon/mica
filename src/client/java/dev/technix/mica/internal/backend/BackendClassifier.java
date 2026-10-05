package dev.technix.mica.internal.backend;

import dev.technix.mica.api.RenderBackendType;
import org.jetbrains.annotations.NotNull;

/**
 * Classifies Minecraft's {@code GpuDeviceBackend} implementation by its package. Used by the
 * compat adapters because some backend classes (OpenGL's {@code GlDevice} since 26.1) are
 * package-private and cannot be named in an {@code instanceof}. Knows the Blaze3d packages
 * (26.2) and the Renderpearl packages they moved to in 26.3.
 */
public final class BackendClassifier {

    private static final String[] VULKAN_PACKAGES = {
            "com.mojang.blaze3d.vulkan.", "com.mojang.renderpearl.backend.vulkan."};
    private static final String[] OPENGL_PACKAGES = {
            "com.mojang.blaze3d.opengl.", "com.mojang.renderpearl.backend.opengl."};

    private BackendClassifier() {
    }

    @NotNull
    public static RenderBackendType classify(@NotNull String deviceBackendClassName) {
        if (startsWithAny(deviceBackendClassName, VULKAN_PACKAGES)) {
            return RenderBackendType.VULKAN;
        }
        if (startsWithAny(deviceBackendClassName, OPENGL_PACKAGES)) {
            return RenderBackendType.OPENGL;
        }
        return RenderBackendType.UNKNOWN;
    }

    private static boolean startsWithAny(String value, String[] prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
