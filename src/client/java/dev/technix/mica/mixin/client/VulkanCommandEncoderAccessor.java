package dev.technix.mica.mixin.client;

//? if >=26.3 {
/*import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPass;
*///?} else {
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanRenderPass;
//?}
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;



@Mixin(VulkanCommandEncoder.class)
public interface VulkanCommandEncoderAccessor {

    // 26.3 renamed Minecraft's textureInitCommandBuffer() to objectInitCommandBuffer().
    //? if >=26.3 {
    /*@Invoker("objectInitCommandBuffer")
    *///?} else {
    @Invoker("textureInitCommandBuffer")
    //?}
    VkCommandBuffer imgui$currentCommandBuffer();

    @Accessor("currentRenderPass")
    @Nullable
    VulkanRenderPass imgui$currentRenderPass();
}
