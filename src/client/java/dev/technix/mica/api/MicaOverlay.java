package dev.technix.mica.api;

import org.jetbrains.annotations.NotNull;

/**
 * Functional form of {@link OverlayElement} for lambdas:
 *
 * <pre>{@code
 * mica.registerOverlay(ctx -> {
 *     ImGui.begin("My Mod");
 *     ImGui.text("Hello Minecraft!");
 *     ImGui.end();
 * });
 * }</pre>
 *
 * <p>Called once per frame on the render thread, between Mica's {@code ImGui.newFrame()} and
 * {@code ImGui.render()}, on every screen.
 */
@FunctionalInterface
public interface MicaOverlay {

    void render(@NotNull RenderContext context);
}
