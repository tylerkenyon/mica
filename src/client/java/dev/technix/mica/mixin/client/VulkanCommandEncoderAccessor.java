package dev.technix.mica.mixin.client;

import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;



@Mixin(VulkanCommandEncoder.class)
public interface VulkanCommandEncoderAccessor {

    @Invoker("textureInitCommandBuffer")
    VkCommandBuffer imgui$currentCommandBuffer();

    @Accessor("currentRenderPass")
    @Nullable
    VulkanRenderPass imgui$currentRenderPass();
}
