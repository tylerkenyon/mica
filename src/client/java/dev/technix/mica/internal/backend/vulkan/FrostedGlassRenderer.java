package dev.technix.mica.internal.backend.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_PREINITIALIZED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_STORAGE_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_COMPUTE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_COMPUTE_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBlitImage;
import static org.lwjgl.vulkan.VK10.vkCmdDispatch;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCreateComputePipelines;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;


public final class FrostedGlassRenderer {

    
    private static final int WORKGROUP_SIZE = 16;

    private static final String DUAL_KAWASE_COMPUTE_GLSL = """
            #version 450

            layout(local_size_x = 16, local_size_y = 16, local_size_z = 1) in;


            layout(set = 0, binding = 0, rgba8) uniform readonly image2D inputImage;
            layout(set = 0, binding = 1, rgba8) uniform writeonly image2D outputImage;

            vec4 sampleClamped(ivec2 coordinate, ivec2 extent) {
                return imageLoad(inputImage, clamp(coordinate, ivec2(0), extent - ivec2(1)));
            }

            void main() {
                ivec2 extent = imageSize(inputImage);
                ivec2 pixel = ivec2(gl_GlobalInvocationID.xy);
                if (any(greaterThanEqual(pixel, extent))) {
                    return;
                }

                
                vec4 color = sampleClamped(pixel, extent) * 0.25;
                color += sampleClamped(pixel + ivec2(-2, -2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2( 2, -2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2(-2,  2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2( 2,  2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2(-1,  0), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 1,  0), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 0, -1), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 0,  1), extent) * 0.0625;
                
                
                imageStore(outputImage, pixel, vec4(color.rgb, 1.0));
            }
            """;

    private VulkanContext context;
    private VulkanImGuiBackend backend;
    private VkPhysicalDevice physicalDevice;
    private VkDevice device;

    private long descriptorSetLayout;
    private long descriptorPool;

    private long descriptorSetForward;

    private long descriptorSetReverse;

    
    private int blurPasses = 5;

    
    private int blurScaleDivisor = 2;

    
    private boolean resizeNeeded = false;

    
    private int lastFullWidth = 0;
    private int lastFullHeight = 0;

    private long pipelineLayout;
    private long computePipeline;

    
    private static final class BlurTarget {
        private long image = VK_NULL_HANDLE;
        private long memory = VK_NULL_HANDLE;
        private long view = VK_NULL_HANDLE;

        private int layout = VK_IMAGE_LAYOUT_UNDEFINED;
    }

    
    private BlurTarget inputTarget = new BlurTarget();

    
    private BlurTarget outputTarget = new BlurTarget();

    private int blurWidth;
    private int blurHeight;


    private long sampler;


    private long blurTextureId;

    private boolean cleanedUp;

    
    public FrostedGlassRenderer() {
    }



    public void init(VulkanContext context, VulkanImGuiBackend backend) {
        this.context = Objects.requireNonNull(context, "context");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.physicalDevice = Objects.requireNonNull(context.physicalDevice(), "physicalDevice");
        this.device = Objects.requireNonNull(context.device(), "device");

        try {
            createDescriptorSetLayout();
            createDescriptorPool();
            allocateDescriptorSets();
            createPipelineLayout();
            createComputePipeline();
            createSampler();
        } catch (RuntimeException exception) {
            cleanup();
            throw exception;
        }
    }



    public void reallocateTargets(int screenWidth, int screenHeight) {
        ensureOpen();
        if (screenWidth <= 0 || screenHeight <= 0) {
            throw new IllegalArgumentException("Framebuffer dimensions must be positive");
        }        int targetWidth = Math.max(1, screenWidth / blurScaleDivisor);
        int targetHeight = Math.max(1, screenHeight / blurScaleDivisor);

        BlurTarget newInput = createBlurTarget(targetWidth, targetHeight);
        BlurTarget newOutput;
        try {
            newOutput = createBlurTarget(targetWidth, targetHeight);
        } catch (RuntimeException exception) {
            destroyBlurTarget(newInput);
            throw exception;
        }

        releaseImGuiTexture();
        destroyBlurTarget(inputTarget);
        destroyBlurTarget(outputTarget);
        inputTarget = newInput;
        outputTarget = newOutput;
        blurWidth = targetWidth;
        blurHeight = targetHeight;
        lastFullWidth = screenWidth;
        lastFullHeight = screenHeight;
        resizeNeeded = false;
        updateStorageImageDescriptors();
    }

    
    public void recordBlurPass(VkCommandBuffer cmd, long sourceImage, long sourceImageView,
                               int sourceLayout, int fullWidth, int fullHeight) {
        ensureOpen();
        Objects.requireNonNull(cmd, "cmd");
        if (sourceImage == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("recordBlurPass requires a host scene VkImage");
        }
        if (inputTarget.image == VK_NULL_HANDLE || outputTarget.image == VK_NULL_HANDLE) {
            throw new IllegalStateException(
                    "No blur targets allocated; call reallocateTargets(int, int) first");
        }
        if (fullWidth <= 0 || fullHeight <= 0) {
            throw new IllegalArgumentException("Scene image dimensions must be positive");
        }

        try (MemoryStack stack = stackPush()) {
            recordImageBarrier(stack, cmd, sourceImage,
                    sourceLayout, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    VK_ACCESS_MEMORY_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            transition(stack, cmd, inputTarget, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_WRITE_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);


            VkImageSubresourceLayers layers = VkImageSubresourceLayers.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0)
                    .baseArrayLayer(0)
                    .layerCount(1);
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.get(0)
                    .srcSubresource(layers)
                    .dstSubresource(layers);
            region.get(0).srcOffsets(0).set(0, 0, 0);
            region.get(0).srcOffsets(1).set(fullWidth, fullHeight, 1);
            region.get(0).dstOffsets(0).set(0, 0, 0);
            region.get(0).dstOffsets(1).set(blurWidth, blurHeight, 1);
            vkCmdBlitImage(cmd,
                    sourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                    inputTarget.image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                    region, VK_FILTER_LINEAR);


            transition(stack, cmd, inputTarget, VK_IMAGE_LAYOUT_GENERAL,
                    VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
            transition(stack, cmd, outputTarget, VK_IMAGE_LAYOUT_GENERAL,
                    VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);



            vkCmdBindPipeline(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, computePipeline);
            int groupsX = ceilDiv(blurWidth, WORKGROUP_SIZE);
            int groupsY = ceilDiv(blurHeight, WORKGROUP_SIZE);
            for (int pass = 0; pass < blurPasses; pass++) {
                long set = (pass % 2 == 0) ? descriptorSetForward : descriptorSetReverse;
                vkCmdBindDescriptorSets(cmd, VK_PIPELINE_BIND_POINT_COMPUTE, pipelineLayout, 0,
                        stack.longs(set), null);
                vkCmdDispatch(cmd, groupsX, groupsY, 1);

                if (pass + 1 < blurPasses) {


                    barrierBetweenPasses(stack, cmd);
                }
            }


            transition(stack, cmd, outputTarget, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                    VK_ACCESS_SHADER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                    VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);



            if (sourceLayout != VK_IMAGE_LAYOUT_UNDEFINED
                    && sourceLayout != VK_IMAGE_LAYOUT_PREINITIALIZED) {
                recordImageBarrier(stack, cmd, sourceImage,
                        VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, sourceLayout,
                        VK_ACCESS_TRANSFER_READ_BIT,
                        VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            }
        }
    }


    public void checkResize(int newWidth, int newHeight) {
        ensureOpen();
        if (newWidth <= 0 || newHeight <= 0) {
            return;
        }

        int targetWidth = Math.max(1, newWidth / blurScaleDivisor);
        int targetHeight = Math.max(1, newHeight / blurScaleDivisor);
        if (!resizeNeeded && outputTarget.image != VK_NULL_HANDLE
                && targetWidth == blurWidth && targetHeight == blurHeight) {
            lastFullWidth = newWidth;
            lastFullHeight = newHeight;
            return;
        }

        check(vkDeviceWaitIdle(device), "vkDeviceWaitIdle (glass resize)");
        reallocateTargets(newWidth, newHeight);
    }


    public long getImGuiTextureId() {
        ensureOpen();
        if (outputTarget.view == VK_NULL_HANDLE) {
            return VK_NULL_HANDLE;
        }
        if (blurTextureId == VK_NULL_HANDLE) {
            blurTextureId = backend.addTexture(sampler, outputTarget.view,
                    VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        }
        return blurTextureId;
    }



    public boolean isBlurTargetReady() {
        return !cleanedUp && outputTarget.layout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    }

    
    public void applyStyle(@org.jetbrains.annotations.NotNull
            dev.technix.mica.api.FrostedGlassStyle style) {
        this.blurPasses = style.blurPasses();
        int newDivisor = style.blurScaleDivisor();
        if (this.blurScaleDivisor != newDivisor) {
            this.blurScaleDivisor = newDivisor;
            this.resizeNeeded = true;
        }
    }

    
    public int blurScaleDivisor() {
        return blurScaleDivisor;
    }

    
    public int blurPasses() {
        return blurPasses;
    }


    public void cleanup() {
        if (cleanedUp) {
            return;
        }

        releaseImGuiTexture();
        if (sampler != VK_NULL_HANDLE) {
            vkDestroySampler(device, sampler, null);
        }
        if (computePipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(device, computePipeline, null);
        }
        if (pipelineLayout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(device, pipelineLayout, null);
        }
        if (descriptorPool != VK_NULL_HANDLE) {
            vkDestroyDescriptorPool(device, descriptorPool, null);
        }
        if (descriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
        }
        destroyBlurTarget(inputTarget);
        destroyBlurTarget(outputTarget);

        computePipeline = VK_NULL_HANDLE;
        pipelineLayout = VK_NULL_HANDLE;
        descriptorSetForward = VK_NULL_HANDLE;
        descriptorSetReverse = VK_NULL_HANDLE;
        descriptorPool = VK_NULL_HANDLE;
        descriptorSetLayout = VK_NULL_HANDLE;
        sampler = VK_NULL_HANDLE;
        blurWidth = 0;
        blurHeight = 0;
        cleanedUp = true;
    }



    private void barrierBetweenPasses(MemoryStack stack, VkCommandBuffer cmd) {
        int access = VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_SHADER_WRITE_BIT;
        recordImageBarrier(stack, cmd, inputTarget.image,
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL, access, access,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
        recordImageBarrier(stack, cmd, outputTarget.image,
                VK_IMAGE_LAYOUT_GENERAL, VK_IMAGE_LAYOUT_GENERAL, access, access,
                VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT);
    }


    private static void transition(MemoryStack stack, VkCommandBuffer cmd, BlurTarget target,
                                   int newLayout, int srcAccess, int dstAccess,
                                   int srcStage, int dstStage) {
        recordImageBarrier(stack, cmd, target.image, target.layout, newLayout,
                srcAccess, dstAccess, srcStage, dstStage);
        target.layout = newLayout;
    }



    private static void recordImageBarrier(MemoryStack stack, VkCommandBuffer cmd, long image,
                                           int oldLayout, int newLayout, int srcAccess,
                                           int dstAccess, int srcStage, int dstStage) {
        if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED) {
            srcAccess = 0;
            srcStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        }

        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0)
                .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                .srcAccessMask(srcAccess)
                .dstAccessMask(dstAccess)
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image)
                .subresourceRange(VkImageSubresourceRange.calloc(stack)
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(1)
                        .baseArrayLayer(0)
                        .layerCount(1));
        vkCmdPipelineBarrier(cmd, srcStage, dstStage, 0, null, null, barrier);
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private void createDescriptorSetLayout() {
        try (MemoryStack stack = stackPush()) {
            
            VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(2, stack);
            for (int index = 0; index < 2; index++) {
                bindings.get(index)
                        .binding(index)
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1)
                        .stageFlags(VK_SHADER_STAGE_COMPUTE_BIT);
            }
            VkDescriptorSetLayoutCreateInfo createInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(bindings);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(device, createInfo, null, pLayout),
                    "vkCreateDescriptorSetLayout (glass)");
            descriptorSetLayout = pLayout.get(0);
        }
    }

    private void createDescriptorPool() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSize = VkDescriptorPoolSize.calloc(1, stack);
            poolSize.get(0)
                    .type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(2);
            VkDescriptorPoolCreateInfo createInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(2)
                    .pPoolSizes(poolSize);
            LongBuffer pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(device, createInfo, null, pPool),
                    "vkCreateDescriptorPool (glass)");
            descriptorPool = pPool.get(0);
        }
    }

    private void allocateDescriptorSets() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetAllocateInfo allocateInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout, descriptorSetLayout));
            LongBuffer pSets = stack.mallocLong(2);
            check(vkAllocateDescriptorSets(device, allocateInfo, pSets),
                    "vkAllocateDescriptorSets (glass)");
            descriptorSetForward = pSets.get(0);
            descriptorSetReverse = pSets.get(1);
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = stackPush()) {
            VkPipelineLayoutCreateInfo createInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(device, createInfo, null, pLayout),
                    "vkCreatePipelineLayout (glass)");
            pipelineLayout = pLayout.get(0);
        }
    }

    private void createComputePipeline() {
        ByteBuffer spirv = VulkanShaderCompiler.compileComputeShader(
                DUAL_KAWASE_COMPUTE_GLSL, "frosted_glass.comp");
        long shaderModule = VK_NULL_HANDLE;
        try {
            shaderModule = createShaderModule(spirv);
            try (MemoryStack stack = stackPush()) {
                VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                        .module(shaderModule)
                        .pName(stack.UTF8("main"));
                VkComputePipelineCreateInfo.Buffer createInfo = VkComputePipelineCreateInfo.calloc(1, stack);
                createInfo.get(0)
                        .sType(VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO)
                        .stage(stage)
                        .layout(pipelineLayout);
                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateComputePipelines(device, VK_NULL_HANDLE, createInfo, null, pPipeline),
                        "vkCreateComputePipelines (glass)");
                computePipeline = pPipeline.get(0);
            }
        } finally {
            if (shaderModule != VK_NULL_HANDLE) {
                vkDestroyShaderModule(device, shaderModule, null);
            }
            MemoryUtil.memFree(spirv);
        }
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo createInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(code);
            LongBuffer pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(device, createInfo, null, pModule),
                    "vkCreateShaderModule (glass)");
            return pModule.get(0);
        }
    }


    private void createSampler() {
        try (MemoryStack stack = stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxAnisotropy(1.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);
            LongBuffer pSampler = stack.mallocLong(1);
            check(vkCreateSampler(device, createInfo, null, pSampler),
                    "vkCreateSampler (frosted glass)");
            sampler = pSampler.get(0);
        }
    }


    private void releaseImGuiTexture() {
        if (blurTextureId != VK_NULL_HANDLE) {
            backend.removeTexture(blurTextureId);
            blurTextureId = VK_NULL_HANDLE;
        }
    }



    private BlurTarget createBlurTarget(int width, int height) {
        BlurTarget target = new BlurTarget();
        try (MemoryStack stack = stackPush()) {
            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .extent(VkExtent3D.calloc(stack).width(width).height(height).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                            | VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer pImage = stack.mallocLong(1);
            check(vkCreateImage(device, imageInfo, null, pImage), "vkCreateImage (glass)");
            target.image = pImage.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, target.image, requirements);
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            LongBuffer pMemory = stack.mallocLong(1);
            check(vkAllocateMemory(device, allocateInfo, null, pMemory),
                    "vkAllocateMemory (glass image)");
            target.memory = pMemory.get(0);
            check(vkBindImageMemory(device, target.image, target.memory, 0),
                    "vkBindImageMemory (glass image)");

            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0)
                    .levelCount(1)
                    .baseArrayLayer(0)
                    .layerCount(1);
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(target.image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .subresourceRange(range);
            LongBuffer pView = stack.mallocLong(1);
            check(vkCreateImageView(device, viewInfo, null, pView),
                    "vkCreateImageView (glass)");
            target.view = pView.get(0);
            return target;
        } catch (RuntimeException exception) {
            destroyBlurTarget(target);
            throw exception;
        }
    }



    private void updateStorageImageDescriptors() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorImageInfo.Buffer inputInfo = VkDescriptorImageInfo.calloc(1, stack);
            inputInfo.get(0)
                    .imageView(inputTarget.view)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
            VkDescriptorImageInfo.Buffer outputInfo = VkDescriptorImageInfo.calloc(1, stack);
            outputInfo.get(0)
                    .imageView(outputTarget.view)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL);

            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(4, stack);


            storageImageWrite(writes.get(0), descriptorSetForward, 0, inputInfo);
            storageImageWrite(writes.get(1), descriptorSetForward, 1, outputInfo);
            storageImageWrite(writes.get(2), descriptorSetReverse, 0, outputInfo);
            storageImageWrite(writes.get(3), descriptorSetReverse, 1, inputInfo);
            vkUpdateDescriptorSets(device, writes, null);
        }
    }

    private static void storageImageWrite(VkWriteDescriptorSet write, long set, int binding,
                                          VkDescriptorImageInfo.Buffer imageInfo) {
        write.sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                .dstSet(set)
                .dstBinding(binding)
                .descriptorCount(1)
                .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                .pImageInfo(imageInfo);
    }

    private int findMemoryType(int typeFilter, int requiredProperties) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties =
                    VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, properties);
            var memoryTypes = properties.memoryTypes();
            for (int index = 0; index < properties.memoryTypeCount(); index++) {
                if ((typeFilter & (1 << index)) != 0
                        && (memoryTypes.get(index).propertyFlags() & requiredProperties)
                        == requiredProperties) {
                    return index;
                }
            }
        }
        throw new IllegalStateException("No Vulkan memory type satisfies flags 0x"
                + Integer.toHexString(requiredProperties));
    }

    private void destroyBlurTarget(BlurTarget target) {
        if (target.view != VK_NULL_HANDLE) {
            vkDestroyImageView(device, target.view, null);
        }
        if (target.image != VK_NULL_HANDLE) {
            vkDestroyImage(device, target.image, null);
        }
        if (target.memory != VK_NULL_HANDLE) {
            vkFreeMemory(device, target.memory, null);
        }
        target.view = VK_NULL_HANDLE;
        target.image = VK_NULL_HANDLE;
        target.memory = VK_NULL_HANDLE;
        target.layout = VK_IMAGE_LAYOUT_UNDEFINED;
    }

    private void ensureOpen() {
        if (cleanedUp) {
            throw new IllegalStateException("FrostedGlassRenderer has already been cleaned up");
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed with VkResult " + result);
        }
    }
}
