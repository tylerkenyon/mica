package dev.technix.mica.internal.backend.vulkan;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Optional;

/**
 * What the Vulkan renderer needs from Minecraft. Implemented by the version compat adapter
 * and obtained through {@code MinecraftCompat.backendAccess(VulkanHostAccess.class)}.
 */
public interface VulkanHostAccess {

    /** The host's current Vulkan device and main render target, empty when not on Vulkan. */
    @NotNull
    Optional<VulkanContext> currentVulkanContext();

    /** The frame command buffer Mica may record into, or {@code null} if none is open. */
    @Nullable
    VkCommandBuffer activeCommandBuffer();

    /** The {@code VkImageView} of a Minecraft texture, {@code 0} if missing. */
    long vkImageViewFor(@NotNull Identifier textureId);
}
