package dev.technix.mica.api;

import dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

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
     * The compat adapter for the running Minecraft version.
     */
    @NotNull
    static MinecraftCompat detect() {
        return new MinecraftCompatImpl_26_2();
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
