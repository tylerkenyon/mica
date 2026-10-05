package dev.technix.mica.api;

import org.jetbrains.annotations.NotNull;

/**
 * The rendering API Minecraft is using for this run.
 *
 * <p>Purely informational: Mica picks the matching renderer automatically, so overlay code
 * never needs to branch on this value. It is exposed for diagnostics and for the rare
 * element that wants to tell the user about a backend limitation.
 */
public enum RenderBackendType {

    VULKAN("Vulkan"),

    OPENGL("OpenGL"),

    /** Minecraft is running a backend Mica has no renderer for (for example a third-party one). */
    UNKNOWN("Unknown");

    private final String displayName;

    RenderBackendType(String displayName) {
        this.displayName = displayName;
    }

    @NotNull
    public String displayName() {
        return displayName;
    }
}
