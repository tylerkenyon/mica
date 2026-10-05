package dev.technix.mica.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;


/**
 * The Minecraft-version-specific bridge. One implementation exists per supported Minecraft
 * release ({@code api.compat.vXX_X}); it is the only code that touches Mojang's rendering
 * classes.
 *
 * <p>The interface itself is backend-independent: it reports which rendering backend
 * Minecraft is running and hands Mica's internal renderers the backend-specific host access
 * they need through {@link #backendAccess(Class)}. Mod code normally never calls it; use
 * {@link Mica#create()} or {@link OverlayRenderer.Builder#build()}, which pick
 * {@link #detect()} automatically.
 */
public interface MinecraftCompat {

    /**
     * The compat adapter for the Minecraft version this Mica jar was built for. Each Mica jar
     * targets one Minecraft version (built from the same sources with Stonecutter).
     */
    @NotNull
    static MinecraftCompat detect() {
        //? if >=26.3 {
        /*return new dev.technix.mica.api.compat.v26_3.MinecraftCompatImpl_26_3();
        *///?} else {
        return new dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2();
        //?}
    }

    /**
     * The rendering backend Minecraft is actually using, or empty while Minecraft has not
     * created its GPU device yet (very early during start-up).
     * {@link RenderBackendType#UNKNOWN} means a device exists but Mica has no renderer for it.
     */
    @NotNull
    Optional<RenderBackendType> renderBackend();

    /** Human-readable Minecraft version, used in diagnostics. */
    @NotNull
    default String minecraftVersion() {
        return "unknown";
    }

    /**
     * {@code true} when the caller is on Minecraft's render thread, the only thread Mica
     * issues GPU work from.
     */
    default boolean isOnRenderThread() {
        return true;
    }

    /**
     * Backend-specific host access for Mica's internal renderers (for example the Vulkan
     * device and frame command buffer, or the OpenGL render target). The access types live
     * in {@code dev.technix.mica.internal.backend.*} and are not part of the public API.
     *
     * <p>The default implementation returns {@code this} when it implements {@code type}.
     */
    @NotNull
    default <T> Optional<T> backendAccess(@NotNull Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }


    /**
     * The mouse cursor position in window coordinates (what Minecraft's {@code MouseHandler}
     * reports), or {@code null} if unknown. Read once per frame for ImGui, so Mica does not
     * depend on the signature of Minecraft's cursor-move callback, which differs between
     * versions.
     */
    default double @Nullable [] cursorPosition() {
        return null;
    }

    /**
     * Turns the platform's text input on or off while an ImGui text field is focused. On
     * GLFW-based versions text input is always on and this does nothing; with SDL (26.3+)
     * character events only arrive while text input is started.
     */
    default void setTextInputActive(boolean active) {
    }


    @NotNull
    Optional<SpriteBounds> locateSprite(@NotNull Identifier atlasId, @NotNull Identifier spriteId);



    @NotNull
    default Optional<SpriteBounds> locateItemIcon(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Identifier registryId = stack.getItem().builtInRegistryHolder().unwrapKey()
                .map(key -> key.identifier()).orElse(null);
        if (registryId == null) {
            return Optional.empty();
        }
        Identifier spriteId = Identifier.fromNamespaceAndPath(registryId.getNamespace(),
                "item/" + registryId.getPath());
        return locateSprite(VanillaAtlases.ITEMS, spriteId);
    }


}
