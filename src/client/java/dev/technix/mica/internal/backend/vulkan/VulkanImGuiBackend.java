package dev.technix.mica.internal.backend.vulkan;

import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiBackendFlags;
import imgui.type.ImInt;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExtent2D;
import org.lwjgl.vulkan.VkExtent3D;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkOffset2D;
import org.lwjgl.vulkan.VkOffset3D;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPipelineColorBlendAttachmentState;
import org.lwjgl.vulkan.VkPipelineColorBlendStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineDynamicStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineLayoutCreateInfo;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineRasterizationStateCreateInfo;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.KHRDynamicRendering;
import org.lwjgl.vulkan.VkPipelineRenderingCreateInfoKHR;
import org.lwjgl.vulkan.VkPipelineViewportStateCreateInfo;
import org.lwjgl.vulkan.VkPushConstantRange;
import org.lwjgl.vulkan.VkRect2D;
import org.lwjgl.vulkan.VkRenderingAttachmentInfo;
import org.lwjgl.vulkan.VkRenderingInfo;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;
import org.lwjgl.vulkan.VkViewport;
import org.lwjgl.vulkan.VkWriteDescriptorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.LongBuffer;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
import static org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_SCISSOR;
import static org.lwjgl.vulkan.VK10.VK_DYNAMIC_STATE_VIEWPORT;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TILING_OPTIMAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_SAMPLED_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT16;
import static org.lwjgl.vulkan.VK10.VK_INDEX_TYPE_UINT32;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_BIND_POINT_GRAPHICS;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_POLYGON_MODE_FILL;
import static org.lwjgl.vulkan.VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
import static org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLE_COUNT_1_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_FRAGMENT_BIT;
import static org.lwjgl.vulkan.VK10.VK_SHADER_STAGE_VERTEX_BIT;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.KHRDynamicRendering.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO_KHR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_CLEAR;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_LOAD_OP_LOAD;
import static org.lwjgl.vulkan.VK10.VK_ATTACHMENT_STORE_OP_STORE;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_VERTEX_INPUT_RATE_VERTEX;
import static org.lwjgl.vulkan.VK10.vkAllocateDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkCmdBindDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkCmdBindIndexBuffer;
import static org.lwjgl.vulkan.VK10.vkCmdBindPipeline;
import static org.lwjgl.vulkan.VK10.vkCmdBindVertexBuffers;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBufferToImage;
import static org.lwjgl.vulkan.VK10.vkCmdDrawIndexed;
import static org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier;
import static org.lwjgl.vulkan.VK10.vkCmdPushConstants;
import static org.lwjgl.vulkan.VK10.vkCmdSetScissor;
import static org.lwjgl.vulkan.VK10.vkCmdSetViewport;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkCreateDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;
import static org.lwjgl.vulkan.VK10.vkCreateImage;
import static org.lwjgl.vulkan.VK10.vkCreateImageView;
import static org.lwjgl.vulkan.VK10.vkCreatePipelineLayout;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkCreateShaderModule;
import static org.lwjgl.vulkan.VK10.vkDestroyBuffer;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorPool;
import static org.lwjgl.vulkan.VK10.vkDestroyDescriptorSetLayout;
import static org.lwjgl.vulkan.VK10.vkDestroyImage;
import static org.lwjgl.vulkan.VK10.vkDestroyImageView;
import static org.lwjgl.vulkan.VK10.vkDestroyPipeline;
import static org.lwjgl.vulkan.VK10.vkDestroyPipelineLayout;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;
import static org.lwjgl.vulkan.VK10.vkDestroyShaderModule;
import static org.lwjgl.vulkan.VK10.vkDeviceWaitIdle;
import static org.lwjgl.vulkan.VK10.vkFreeDescriptorSets;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;
import static org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetImageMemoryRequirements;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;
import static org.lwjgl.vulkan.VK10.vkUpdateDescriptorSets;



public final class VulkanImGuiBackend implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");


    private static final int PUSH_CONSTANT_SIZE = 4 * Float.BYTES;

    private static final int MAX_DESCRIPTOR_SETS = 64;
    private static final int MEMORY_HOST_VISIBLE_COHERENT =
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;

    private final VulkanContext context;
    private final VkInstance vkInstance;
    private final VkPhysicalDevice vkPhysicalDevice;
    private final VkDevice vkDevice;


    private long descriptorPool;
    private long descriptorSetLayout;
    private long pipelineLayout;
    private long pipeline;


    private long fontImage;
    private long fontImageMemory;
    private long fontImageView;
    private long fontSampler;
    private long fontDescriptorSet;


    private int fontAtlasWidth;
    private int fontAtlasHeight;




    private long pendingFontStagingBuffer;
    private long pendingFontStagingMemory;
    private boolean fontUploadPending;
    private boolean fontUploadRecorded;
    private final Set<Long> textureDescriptorSets = new HashSet<>();



    private static final class GeometryBuffers {
        private long vertexBuffer;
        private long vertexMemory;
        private long vertexCapacity;
        private long indexBuffer;
        private long indexMemory;
        private long indexCapacity;
    }



    private static final int FRAME_RING = 3;

    private final GeometryBuffers[] frames = new GeometryBuffers[FRAME_RING];
    private int frameIndex;

    private boolean initialized;


    private boolean loggedDrawStats;


    private static final boolean DEBUG_CLEAR_ATTACHMENT =
            Boolean.getBoolean("imgui.debug.clearAttachment");

    public VulkanImGuiBackend(VulkanContext context) {
        this.context = Objects.requireNonNull(context, "context");
        this.vkInstance = context.instance();
        this.vkPhysicalDevice = context.physicalDevice();
        this.vkDevice = context.device();
    }



    public void init() {
        if (initialized) {
            return;
        }
        ImGuiIO io = ImGui.getIO();
        io.setBackendRendererName("imgui-java_impl_vulkan (mica)");
        io.addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);

        createDescriptorPool();
        createDescriptorSetLayout();
        createPipelineLayout();
        createFontSampler();
        createPipeline();
        createFontsTexture();

        initialized = true;
        LOGGER.debug("Vulkan ImGui backend initialized on queue family {} (sub-pass {}, MSAA {})",
                context.queueFamilyIndex(), context.subpass(), context.msaaSamples());
    }


    public void newFrame() {

    }



    public long addTexture(long sampler, long imageView, int imageLayout) {
        if (!initialized) {
            throw new IllegalStateException("Vulkan ImGui backend is not initialized");
        }
        if (sampler == VK_NULL_HANDLE || imageView == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("A Vulkan texture requires a non-null sampler and image view");
        }

        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(vkDevice, setInfo, pSet), "vkAllocateDescriptorSets (texture)");
            long descriptorSet = pSet.get(0);

            VkDescriptorImageInfo.Buffer imageInfo = VkDescriptorImageInfo.calloc(1, stack);
            imageInfo.get(0)
                    .sampler(sampler)
                    .imageView(imageView)
                    .imageLayout(imageLayout);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(descriptorSet)
                    .dstBinding(0)
                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(imageInfo);
            vkUpdateDescriptorSets(vkDevice, write, null);

            textureDescriptorSets.add(descriptorSet);
            return descriptorSet;
        }
    }



    public void removeTexture(long descriptorSet) {
        if (descriptorSet == VK_NULL_HANDLE) {
            return;
        }
        if (!textureDescriptorSets.contains(descriptorSet)) {
            throw new IllegalArgumentException("Descriptor set was not allocated by addTexture");
        }

        try (MemoryStack stack = stackPush()) {
            check(vkFreeDescriptorSets(vkDevice, descriptorPool, stack.longs(descriptorSet)),
                    "vkFreeDescriptorSets (texture)");
        }
        textureDescriptorSets.remove(descriptorSet);
    }


    public void render(ImDrawData drawData, VkCommandBuffer commandBuffer) {
        Objects.requireNonNull(commandBuffer, "commandBuffer");
        if (drawData == null || !drawData.getValid()) {
            return;
        }
        int fbWidth = (int) (drawData.getDisplaySizeX() * drawData.getFramebufferScaleX());
        int fbHeight = (int) (drawData.getDisplaySizeY() * drawData.getFramebufferScaleY());
        if (fbWidth <= 0 || fbHeight <= 0 || drawData.getCmdListsCount() <= 0) {
            return;
        }

        try (MemoryStack stack = stackPush()) {
            if (!loggedDrawStats) {
                loggedDrawStats = true;
                LOGGER.debug("ImGui frame: fb={}x{} cmdLists={} vtx={} idx={} colorFormat={} "
                                + "msaa={}",
                        fbWidth, fbHeight, drawData.getCmdListsCount(),
                        drawData.getTotalVtxCount(), drawData.getTotalIdxCount(),
                        context.colorAttachmentFormat(), context.msaaSamples());
            }

            VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
            viewport.get(0)
                    .x(0).y(0)
                    .width(fbWidth).height(fbHeight)
                    .minDepth(0.0f).maxDepth(1.0f);

            FloatBuffer pushConstants = stack.mallocFloat(4);


            pushConstants.put(0, 2.0f / fbWidth);
            pushConstants.put(1, -2.0f / fbHeight);
            pushConstants.put(2, -1.0f - drawData.getDisplayPosX() * (2.0f / fbWidth));
            pushConstants.put(3, 1.0f + drawData.getDisplayPosY() * (2.0f / fbHeight));



            if (!uploadDrawData(drawData)) {
                return;
            }
            GeometryBuffers geometry = frames[frameIndex];

            vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdSetViewport(commandBuffer, 0, viewport);
            vkCmdPushConstants(commandBuffer, pipelineLayout, VK_SHADER_STAGE_VERTEX_BIT,
                    0, pushConstants);
            vkCmdBindVertexBuffers(commandBuffer, 0,
                    stack.longs(geometry.vertexBuffer), stack.longs(0L));
            vkCmdBindIndexBuffer(commandBuffer, geometry.indexBuffer, 0,
                    ImDrawData.sizeOfImDrawIdx() == 2
                            ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32);

            VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
            scissor.get(0).offset(VkOffset2D.calloc(stack)).extent(VkExtent2D.calloc(stack));

            float clipOffsetX = drawData.getDisplayPosX();
            float clipOffsetY = drawData.getDisplayPosY();
            float clipScaleX = drawData.getFramebufferScaleX();
            float clipScaleY = drawData.getFramebufferScaleY();


            int globalVertexOffset = 0;
            int globalIndexOffset = 0;

            for (int listIndex = 0; listIndex < drawData.getCmdListsCount(); listIndex++) {
                int commandCount = drawData.getCmdListCmdBufferSize(listIndex);
                for (int commandIndex = 0; commandIndex < commandCount; commandIndex++) {
                    int elementCount = drawData.getCmdListCmdBufferElemCount(listIndex, commandIndex);
                    if (elementCount == 0) {
                        continue;
                    }
                    ImVec4 clipRect = drawData.getCmdListCmdBufferClipRect(listIndex, commandIndex);
                    float clipMinX = (clipRect.x - clipOffsetX) * clipScaleX;
                    float clipMinY = (clipRect.y - clipOffsetY) * clipScaleY;
                    float clipMaxX = (clipRect.z - clipOffsetX) * clipScaleX;
                    float clipMaxY = (clipRect.w - clipOffsetY) * clipScaleY;
                    if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) {
                        continue;
                    }
                    clipMinX = Math.max(clipMinX, 0.0f);
                    clipMinY = Math.max(clipMinY, 0.0f);
                    clipMaxX = Math.min(clipMaxX, fbWidth);
                    clipMaxY = Math.min(clipMaxY, fbHeight);

                    scissor.get(0).offset().x((int) clipMinX)



                            .y((int) (fbHeight - clipMaxY));
                    scissor.get(0).extent().width((int) (clipMaxX - clipMinX))
                            .height((int) (clipMaxY - clipMinY));
                    vkCmdSetScissor(commandBuffer, 0, scissor);

                    long textureId = drawData.getCmdListCmdBufferTextureId(listIndex, commandIndex);
                    long descriptorSet = textureId != VK_NULL_HANDLE ? textureId : fontDescriptorSet;
                    vkCmdBindDescriptorSets(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipelineLayout,
                            0, stack.longs(descriptorSet), null);

                    vkCmdDrawIndexed(commandBuffer, elementCount, 1,
                            drawData.getCmdListCmdBufferIdxOffset(listIndex, commandIndex)
                                    + globalIndexOffset,
                            drawData.getCmdListCmdBufferVtxOffset(listIndex, commandIndex)
                                    + globalVertexOffset, 0);
                }
                globalIndexOffset += drawData.getCmdListIdxBufferSize(listIndex);
                globalVertexOffset += drawData.getCmdListVtxBufferSize(listIndex);
            }
        }
    }



    private boolean uploadDrawData(ImDrawData drawData) {
        int vertexStride = ImDrawData.sizeOfImDrawVert();
        int indexStride = ImDrawData.sizeOfImDrawIdx();
        long vertexBytes = (long) drawData.getTotalVtxCount() * vertexStride;
        long indexBytes = (long) drawData.getTotalIdxCount() * indexStride;
        if (vertexBytes == 0 || indexBytes == 0) {
            return false;
        }



        frameIndex = (frameIndex + 1) % FRAME_RING;
        GeometryBuffers geometry = frames[frameIndex];
        if (geometry == null) {
            geometry = new GeometryBuffers();
            frames[frameIndex] = geometry;
        }

        ensureVertexCapacity(geometry, vertexBytes);
        ensureIndexCapacity(geometry, indexBytes);
        if (geometry.vertexBuffer == VK_NULL_HANDLE || geometry.indexBuffer == VK_NULL_HANDLE) {
            return false;
        }

        ByteBuffer mappedVertices = mapBuffer(geometry.vertexMemory, vertexBytes);
        try {
            for (int listIndex = 0; listIndex < drawData.getCmdListsCount(); listIndex++) {
                mappedVertices.put(drawData.getCmdListVtxBufferData(listIndex));
            }
        } finally {
            vkUnmapMemory(vkDevice, geometry.vertexMemory);
        }

        ByteBuffer mappedIndices = mapBuffer(geometry.indexMemory, indexBytes);
        try {
            for (int listIndex = 0; listIndex < drawData.getCmdListsCount(); listIndex++) {
                mappedIndices.put(drawData.getCmdListIdxBufferData(listIndex));
            }
        } finally {
            vkUnmapMemory(vkDevice, geometry.indexMemory);
        }

        return true;
    }


    public void renderInOwnPass(ImDrawData drawData, VkCommandBuffer commandBuffer,
                                long colorImageView, int width, int height) {
        Objects.requireNonNull(commandBuffer, "commandBuffer");
        if (!initialized || drawData == null || !drawData.getValid()
                || colorImageView == VK_NULL_HANDLE || width <= 0 || height <= 0) {
            return;
        }

        try (MemoryStack stack = stackPush()) {
            VkRenderingAttachmentInfo.Buffer colorAttachment =
                    VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachment.get(0)
                    .sType$Default()
                    .imageView(colorImageView)
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(DEBUG_CLEAR_ATTACHMENT
                            ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            if (DEBUG_CLEAR_ATTACHMENT) {
                colorAttachment.get(0).clearValue().color().float32(stack.floats(1f, 0f, 1f, 1f));
            }

            VkRenderingInfo renderingInfo = VkRenderingInfo.calloc(stack)
                    .sType$Default()
                    .layerCount(1)
                    .viewMask(0)
                    .pColorAttachments(colorAttachment);
            renderingInfo.renderArea().offset().set(0, 0);
            renderingInfo.renderArea().extent().set(width, height);

            KHRDynamicRendering.vkCmdBeginRenderingKHR(commandBuffer, renderingInfo);
            try {
                render(drawData, commandBuffer);
            } finally {
                KHRDynamicRendering.vkCmdEndRenderingKHR(commandBuffer);
            }
        }
    }



    public boolean recordPendingTransfers(VkCommandBuffer commandBuffer) {
        Objects.requireNonNull(commandBuffer, "commandBuffer");
        if (!fontUploadPending || fontImage == VK_NULL_HANDLE) {
            return false;
        }

        try (MemoryStack stack = stackPush()) {
            VkImageSubresourceRange range = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);

            VkImageMemoryBarrier.Buffer toTransfer = VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(0)
                    .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(fontImage)
                    .subresourceRange(range);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);

            VkBufferImageCopy.Buffer region = VkBufferImageCopy.calloc(1, stack);
            region.get(0)
                    .bufferOffset(0)
                    .bufferRowLength(0).bufferImageHeight(0)
                    .imageSubresource(VkImageSubresourceLayers.calloc(stack)
                            .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .mipLevel(0).baseArrayLayer(0).layerCount(1))
                    .imageOffset(VkOffset3D.calloc(stack).x(0).y(0).z(0))
                    .imageExtent(VkExtent3D.calloc(stack)
                            .width(fontAtlasWidth).height(fontAtlasHeight).depth(1));
            vkCmdCopyBufferToImage(commandBuffer, pendingFontStagingBuffer, fontImage,
                    VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            VkImageMemoryBarrier.Buffer toShaderRead = VkImageMemoryBarrier.calloc(1, stack);
            toShaderRead.get(0)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER)
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_SHADER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(fontImage)
                    .subresourceRange(range);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, 0, null, null, toShaderRead);
        }

        fontUploadPending = false;
        fontUploadRecorded = true;
        LOGGER.info("ImGui font atlas upload recorded into Minecraft's frame command buffer "
                + "({}x{})", fontAtlasWidth, fontAtlasHeight);
        return true;
    }



    public boolean isFontTextureReady() {
        return fontUploadRecorded;
    }


    @Override
    public void close() {
        if (!initialized) {
            return;
        }
        int waitResult = vkDeviceWaitIdle(vkDevice);
        if (waitResult != VK_SUCCESS) {
            LOGGER.warn("vkDeviceWaitIdle during shutdown returned {}", waitResult);
        }
        for (int index = 0; index < frames.length; index++) {
            GeometryBuffers geometry = frames[index];
            if (geometry == null) {
                continue;
            }
            destroyBufferPair(geometry.vertexBuffer, geometry.vertexMemory);
            destroyBufferPair(geometry.indexBuffer, geometry.indexMemory);
            frames[index] = null;
        }
        destroyBufferPair(pendingFontStagingBuffer, pendingFontStagingMemory);
        pendingFontStagingBuffer = pendingFontStagingMemory = 0;
        fontUploadPending = false;
        fontUploadRecorded = false;

        if (descriptorPool != 0) {
            try (MemoryStack stack = stackPush()) {
                LongBuffer descriptorSetBuffer = stack.mallocLong(1);
                for (long descriptorSet : textureDescriptorSets) {
                    descriptorSetBuffer.put(0, descriptorSet);
                    int result = vkFreeDescriptorSets(vkDevice, descriptorPool, descriptorSetBuffer);
                    if (result != VK_SUCCESS) {
                        LOGGER.warn("vkFreeDescriptorSets during shutdown returned {}", result);
                    }
                }
            }
        }
        textureDescriptorSets.clear();

        if (fontImageView != 0) vkDestroyImageView(vkDevice, fontImageView, null);
        if (fontImage != 0) vkDestroyImage(vkDevice, fontImage, null);
        if (fontImageMemory != 0) vkFreeMemory(vkDevice, fontImageMemory, null);
        if (fontSampler != 0) vkDestroySampler(vkDevice, fontSampler, null);
        if (pipeline != 0) vkDestroyPipeline(vkDevice, pipeline, null);
        if (pipelineLayout != 0) vkDestroyPipelineLayout(vkDevice, pipelineLayout, null);
        if (descriptorSetLayout != 0) vkDestroyDescriptorSetLayout(vkDevice, descriptorSetLayout, null);
        if (descriptorPool != 0) vkDestroyDescriptorPool(vkDevice, descriptorPool, null);

        fontImageView = fontImage = fontImageMemory = fontSampler = fontDescriptorSet = 0;
        pipeline = pipelineLayout = descriptorSetLayout = descriptorPool = 0;
        initialized = false;
        LOGGER.debug("Vulkan ImGui backend shut down");
    }

    
    
    

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed with VkResult " + result);
        }
    }

    private int findMemoryType(int typeFilter, int requiredProperties) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties =
                    VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(vkPhysicalDevice, properties);
            var types = properties.memoryTypes();
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeFilter & (1 << i)) != 0
                        && (types.get(i).propertyFlags() & requiredProperties) == requiredProperties) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("No usable memory type with flags 0x"
                + Integer.toHexString(requiredProperties));
    }



    private void ensureVertexCapacity(GeometryBuffers geometry, long needed) {
        if (geometry.vertexBuffer != VK_NULL_HANDLE && geometry.vertexCapacity >= needed) {
            return;
        }
        destroyBufferPair(geometry.vertexBuffer, geometry.vertexMemory);
        geometry.vertexBuffer = geometry.vertexMemory = VK_NULL_HANDLE;
        geometry.vertexCapacity = 0;

        long size = Math.max(needed, 64 * 1024);
        long[] created = createHostBuffer(size, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
        geometry.vertexBuffer = created[0];
        geometry.vertexMemory = created[1];
        geometry.vertexCapacity = size;
    }


    private void ensureIndexCapacity(GeometryBuffers geometry, long needed) {
        if (geometry.indexBuffer != VK_NULL_HANDLE && geometry.indexCapacity >= needed) {
            return;
        }
        destroyBufferPair(geometry.indexBuffer, geometry.indexMemory);
        geometry.indexBuffer = geometry.indexMemory = VK_NULL_HANDLE;
        geometry.indexCapacity = 0;

        long size = Math.max(needed, 64 * 1024);
        long[] created = createHostBuffer(size, VK_BUFFER_USAGE_INDEX_BUFFER_BIT);
        geometry.indexBuffer = created[0];
        geometry.indexMemory = created[1];
        geometry.indexCapacity = size;
    }

    private long[] createHostBuffer(long size, int usage) {
        try (MemoryStack stack = stackPush()) {
            VkBufferCreateInfo info = VkBufferCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO)
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer pBuffer = stack.mallocLong(1);
            check(vkCreateBuffer(vkDevice, info, null, pBuffer), "vkCreateBuffer");

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(vkDevice, pBuffer.get(0), requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(),
                    MEMORY_HOST_VISIBLE_COHERENT);
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer pMemory = stack.mallocLong(1);
            check(vkAllocateMemory(vkDevice, allocInfo, null, pMemory), "vkAllocateMemory");
            check(vkBindBufferMemory(vkDevice, pBuffer.get(0), pMemory.get(0), 0),
                    "vkBindBufferMemory");
            return new long[]{pBuffer.get(0), pMemory.get(0)};
        }
    }

    private ByteBuffer mapBuffer(long memory, long size) {
        try (MemoryStack stack = stackPush()) {
            PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(vkDevice, memory, 0, size, 0, pointer), "vkMapMemory");
            return MemoryUtil.memByteBuffer(pointer.get(0), (int) size);
        }
    }

    private void destroyBufferPair(long buffer, long memory) {
        if (buffer != 0) vkDestroyBuffer(vkDevice, buffer, null);
        if (memory != 0) vkFreeMemory(vkDevice, memory, null);
    }



    private void createFontsTexture() {
        ImFontAtlas fonts = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer pixels = fonts.getTexDataAsRGBA32(width, height);
        int imageSize = width.get() * height.get() * 4;
        if (imageSize <= 0) {
            throw new IllegalStateException("Font atlas build produced an empty texture");
        }

        LOGGER.debug("ImGui font atlas built: {}x{} ({} bytes)",
                width.get(), height.get(), imageSize);
        fontAtlasWidth = width.get();
        fontAtlasHeight = height.get();

        try (MemoryStack stack = stackPush()) {
            VkImageSubresourceRange imageRange = VkImageSubresourceRange.calloc(stack)
                    .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);


            VkImageCreateInfo imageInfo = VkImageCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .extent(VkExtent3D.calloc(stack).width(width.get()).height(height.get()).depth(1))
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            LongBuffer pImage = stack.mallocLong(1);
            check(vkCreateImage(vkDevice, imageInfo, null, pImage), "vkCreateImage");
            fontImage = pImage.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(vkDevice, fontImage, requirements);
            int memoryType = findMemoryType(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
            VkMemoryAllocateInfo allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO)
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer pMemory = stack.mallocLong(1);
            check(vkAllocateMemory(vkDevice, allocInfo, null, pMemory), "vkAllocateMemory (image)");
            fontImageMemory = pMemory.get(0);
            check(vkBindImageMemory(vkDevice, fontImage, fontImageMemory, 0), "vkBindImageMemory");


            long[] staging = createHostBuffer(imageSize, VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
            ByteBuffer staged = mapBuffer(staging[1], imageSize);
            pixels.rewind();
            staged.put(pixels).flip();
            vkUnmapMemory(vkDevice, staging[1]);



            pendingFontStagingBuffer = staging[0];
            pendingFontStagingMemory = staging[1];
            fontUploadPending = true;


            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO)
                    .image(fontImage)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .subresourceRange(imageRange);
            LongBuffer pImageView = stack.mallocLong(1);
            check(vkCreateImageView(vkDevice, viewInfo, null, pImageView), "vkCreateImageView");
            fontImageView = pImageView.get(0);

            VkDescriptorSetAllocateInfo setInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO)
                    .descriptorPool(descriptorPool)
                    .pSetLayouts(stack.longs(descriptorSetLayout));
            LongBuffer pSet = stack.mallocLong(1);
            check(vkAllocateDescriptorSets(vkDevice, setInfo, pSet), "vkAllocateDescriptorSets");
            fontDescriptorSet = pSet.get(0);

            VkDescriptorImageInfo.Buffer fontImageInfo = VkDescriptorImageInfo.calloc(1, stack);
            fontImageInfo.get(0)
                    .sampler(fontSampler)
                    .imageView(fontImageView)
                    .imageLayout(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            VkWriteDescriptorSet.Buffer write = VkWriteDescriptorSet.calloc(1, stack);
            write.get(0)
                    .sType(VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET)
                    .dstSet(fontDescriptorSet)
                    .dstBinding(0)

                    .descriptorCount(1)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .pImageInfo(fontImageInfo);
            vkUpdateDescriptorSets(vkDevice, write, null);

            fonts.setTexID(fontDescriptorSet);
        }
    }


    private void createDescriptorPool() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorPoolSize.Buffer poolSizes = VkDescriptorPoolSize.calloc(1, stack);
            poolSizes.get(0).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(MAX_DESCRIPTOR_SETS);
            VkDescriptorPoolCreateInfo info = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO)
                    .flags(VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .maxSets(MAX_DESCRIPTOR_SETS)
                    .pPoolSizes(poolSizes);
            LongBuffer pPool = stack.mallocLong(1);
            check(vkCreateDescriptorPool(vkDevice, info, null, pPool), "vkCreateDescriptorPool");
            descriptorPool = pPool.get(0);
        }
    }

    private void createDescriptorSetLayout() {
        try (MemoryStack stack = stackPush()) {
            VkDescriptorSetLayoutBinding.Buffer binding = VkDescriptorSetLayoutBinding.calloc(1, stack);
            binding.get(0)
                    .binding(0)
                    .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT);
            VkDescriptorSetLayoutCreateInfo info = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO)
                    .pBindings(binding);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreateDescriptorSetLayout(vkDevice, info, null, pLayout),
                    "vkCreateDescriptorSetLayout");
            descriptorSetLayout = pLayout.get(0);
        }
    }

    private void createPipelineLayout() {
        try (MemoryStack stack = stackPush()) {
            VkPushConstantRange.Buffer range = VkPushConstantRange.calloc(1, stack);
            range.get(0)
                    .stageFlags(VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(PUSH_CONSTANT_SIZE);
            VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO)
                    .setLayoutCount(1)
                    .pSetLayouts(stack.longs(descriptorSetLayout))
                    .pPushConstantRanges(range);
            LongBuffer pLayout = stack.mallocLong(1);
            check(vkCreatePipelineLayout(vkDevice, info, null, pLayout), "vkCreatePipelineLayout");
            pipelineLayout = pLayout.get(0);
        }
    }

    private void createFontSampler() {
        try (MemoryStack stack = stackPush()) {
            VkSamplerCreateInfo info = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(VK_FILTER_LINEAR)
                    .minFilter(VK_FILTER_LINEAR)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .minLod(0.0f)
                    .maxLod(0.0f)
                    .borderColor(VK_BORDER_COLOR_FLOAT_OPAQUE_WHITE)
                    .unnormalizedCoordinates(false);
            LongBuffer pSampler = stack.mallocLong(1);
            check(vkCreateSampler(vkDevice, info, null, pSampler), "vkCreateSampler");
            fontSampler = pSampler.get(0);
        }
    }

    private void createPipeline() {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer vertexSpv = VulkanShaderCompiler.compileVertexShader();
            ByteBuffer fragmentSpv = VulkanShaderCompiler.compileFragmentShader();
            long vertexModule = createShaderModule(vertexSpv);
            long fragmentModule = createShaderModule(fragmentSpv);
            MemoryUtil.memFree(vertexSpv);
            MemoryUtil.memFree(fragmentSpv);
            try {
                VkPipelineShaderStageCreateInfo.Buffer stages =
                        VkPipelineShaderStageCreateInfo.calloc(2, stack);
                stages.get(0)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_VERTEX_BIT)
                        .module(vertexModule)
                        .pName(stack.UTF8("main"));
                stages.get(1)
                        .sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                        .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                        .module(fragmentModule)
                        .pName(stack.UTF8("main"));

                int stride = imgui.ImDrawData.sizeOfImDrawVert();
                VkVertexInputBindingDescription.Buffer binding =
                        VkVertexInputBindingDescription.calloc(1, stack);
                binding.get(0)
                        .binding(0).stride(stride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX);
                VkVertexInputAttributeDescription.Buffer attributes =
                        VkVertexInputAttributeDescription.calloc(3, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(8);
                attributes.get(2).location(2).binding(0).format(VK_FORMAT_R8G8B8A8_UNORM).offset(16);

                VkPipelineVertexInputStateCreateInfo vertexInput =
                        VkPipelineVertexInputStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                                .pVertexBindingDescriptions(binding)
                                .pVertexAttributeDescriptions(attributes);

                VkPipelineInputAssemblyStateCreateInfo inputAssembly =
                        VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                                .topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST)
                                .primitiveRestartEnable(false);

                VkPipelineViewportStateCreateInfo viewportState =
                        VkPipelineViewportStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                                .viewportCount(1)
                                .scissorCount(1);

                VkPipelineRasterizationStateCreateInfo rasterization =
                        VkPipelineRasterizationStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                                .depthClampEnable(false)
                                .rasterizerDiscardEnable(false)
                                .polygonMode(VK_POLYGON_MODE_FILL)
                                .cullMode(VK_CULL_MODE_NONE)
                                .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                                .depthBiasEnable(false)
                                .lineWidth(1.0f);

                VkPipelineMultisampleStateCreateInfo multisample =
                        VkPipelineMultisampleStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                                .rasterizationSamples(context.msaaSamples())
                                .sampleShadingEnable(false)
                                .alphaToCoverageEnable(false)
                                .alphaToOneEnable(false);

                VkPipelineColorBlendAttachmentState.Buffer attachment =
                        VkPipelineColorBlendAttachmentState.calloc(1, stack);
                attachment.get(0)
                                .blendEnable(true)
                                .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                                .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                                .colorBlendOp(VK_BLEND_OP_ADD)
                                .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                                .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                                .alphaBlendOp(VK_BLEND_OP_ADD)
                                .colorWriteMask(0xF);

                VkPipelineColorBlendStateCreateInfo colorBlend =
                        VkPipelineColorBlendStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                                .logicOpEnable(false)
                                .attachmentCount(1)
                                .pAttachments(attachment);

                VkPipelineDynamicStateCreateInfo dynamicState =
                        VkPipelineDynamicStateCreateInfo.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                                .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT,
                                        VK_DYNAMIC_STATE_SCISSOR));

                
                
                
                
                VkPipelineRenderingCreateInfoKHR renderingInfo =
                        VkPipelineRenderingCreateInfoKHR.calloc(stack)
                                .sType(VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO_KHR)
                                .pColorAttachmentFormats(
                                        stack.ints(context.colorAttachmentFormat()));

                VkGraphicsPipelineCreateInfo.Buffer pipelineInfo =
                        VkGraphicsPipelineCreateInfo.calloc(1, stack);
                pipelineInfo.get(0)
                        .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                        .pNext(renderingInfo)
                        .stageCount(2)
                        .pStages(stages)
                        .pVertexInputState(vertexInput)
                        .pInputAssemblyState(inputAssembly)
                        .pViewportState(viewportState)
                        .pRasterizationState(rasterization)
                        .pMultisampleState(multisample)
                        .pColorBlendState(colorBlend)
                        .pDynamicState(dynamicState)
                        .layout(pipelineLayout)
                        .subpass(0)
                        .renderPass(VK_NULL_HANDLE);

                LongBuffer pPipeline = stack.mallocLong(1);
                check(vkCreateGraphicsPipelines(vkDevice, VK_NULL_HANDLE, pipelineInfo,
                        null, pPipeline), "vkCreateGraphicsPipelines");
                pipeline = pPipeline.get(0);
            } finally {
                vkDestroyShaderModule(vkDevice, vertexModule, null);
                vkDestroyShaderModule(vkDevice, fragmentModule, null);
            }
        }
    }

    private long createShaderModule(ByteBuffer code) {
        try (MemoryStack stack = stackPush()) {
            VkShaderModuleCreateInfo info = VkShaderModuleCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO)
                    .pCode(code);
            LongBuffer pModule = stack.mallocLong(1);
            check(vkCreateShaderModule(vkDevice, info, null, pModule), "vkCreateShaderModule");
            return pModule.get(0);
        }
    }
}
