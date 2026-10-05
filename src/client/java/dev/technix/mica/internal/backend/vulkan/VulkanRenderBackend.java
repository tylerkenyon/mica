package dev.technix.mica.internal.backend.vulkan;

import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.internal.backend.RenderBackend;
import imgui.ImDrawData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkSamplerCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;
import java.util.Objects;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.VK_FILTER_LINEAR;
import static org.lwjgl.vulkan.VK10.VK_FILTER_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
import static org.lwjgl.vulkan.VK10.VK_SAMPLER_MIPMAP_MODE_NEAREST;
import static org.lwjgl.vulkan.VK10.VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateSampler;
import static org.lwjgl.vulkan.VK10.vkDestroySampler;


/**
 * {@link RenderBackend} on Minecraft's Vulkan device. Records into Minecraft's own frame
 * command buffer; the draw itself is {@link VulkanImGuiBackend}, the backdrop blur is
 * {@link FrostedGlassRenderer}.
 */
public final class VulkanRenderBackend implements RenderBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final VulkanHostAccess host;

    private VulkanContext context;
    private VulkanImGuiBackend imgui;
    private FrostedGlassRenderer glass;

    private boolean glassEnabled;
    private boolean glassFailed;
    private FrostedGlassStyle glassStyle = FrostedGlassStyle.DEFAULT;

    private long sharedSampler;
    private long nearestSampler;

    public VulkanRenderBackend(@NotNull VulkanHostAccess host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public @NotNull RenderBackendType type() {
        return RenderBackendType.VULKAN;
    }

    @Override
    public boolean initialize() {
        if (imgui != null) {
            return true;
        }
        VulkanContext current = host.currentVulkanContext().orElse(null);
        if (current == null) {
            return false;
        }
        context = current;
        VulkanImGuiBackend backend = new VulkanImGuiBackend(context);
        backend.init();
        imgui = backend;
        if (glassEnabled) {
            createGlass();
        }
        return true;
    }

    private void createGlass() {
        FrostedGlassRenderer renderer = new FrostedGlassRenderer();
        renderer.applyStyle(glassStyle);
        try {
            renderer.init(context, imgui);
            glass = renderer;
        } catch (RuntimeException exception) {
            LOGGER.warn("Vulkan frosted glass unavailable; panels draw without blur", exception);
            glass = null;
            glassFailed = true;
        }
    }

    @Override
    public boolean isInitialized() {
        return imgui != null;
    }

    @Override
    public @Nullable Viewport beginFrame() {
        if (imgui == null) {
            return null;
        }
        VulkanContext current = host.currentVulkanContext().orElse(null);
        if (current == null) {
            return null;
        }
        context = current;
        Integer contextWidth = context.framebufferWidth();
        Integer contextHeight = context.framebufferHeight();
        if (glassEnabled && glass == null && !glassFailed) {
            createGlass();
        }
        if (glass != null && contextWidth != null && contextHeight != null) {
            glass.checkResize(contextWidth, contextHeight);
        }
        int width = contextWidth != null && contextWidth > 0 ? contextWidth : 1;
        int height = contextHeight != null && contextHeight > 0 ? contextHeight : 1;
        return new Viewport(width, height);
    }

    @Override
    public void prepareFrame() {
        if (imgui == null) {
            return;
        }
        VkCommandBuffer commandBuffer = host.activeCommandBuffer();
        if (commandBuffer != null) {
            imgui.recordPendingTransfers(commandBuffer);
        }
    }

    @Override
    public long recordBackdrop() {
        if (imgui == null || glass == null || context == null) {
            return 0L;
        }
        VkCommandBuffer commandBuffer = host.activeCommandBuffer();
        long sceneImage = context.getCurrentSceneImage();
        Integer width = context.framebufferWidth();
        Integer height = context.framebufferHeight();
        if (commandBuffer != null && sceneImage != 0L && width != null && height != null) {
            glass.checkResize(width, height);
            glass.recordBlurPass(commandBuffer, sceneImage, context.getCurrentSceneImageView(),
                    context.getCurrentSceneImageLayout(), width, height);
        }
        return glass.isBlurTargetReady() ? glass.getImGuiTextureId() : 0L;
    }

    @Override
    public boolean isReadyToRender() {
        return imgui != null && imgui.isFontTextureReady();
    }

    @Override
    public void render(@NotNull ImDrawData drawData) {
        if (imgui == null) {
            return;
        }
        VkCommandBuffer commandBuffer = host.activeCommandBuffer();
        if (commandBuffer == null) {
            return;
        }
        VulkanContext target = host.currentVulkanContext().orElse(null);
        if (target == null || target.getCurrentSceneImageView() == 0L) {
            return;
        }
        Integer width = target.framebufferWidth();
        Integer height = target.framebufferHeight();
        if (width == null || height == null) {
            return;
        }
        imgui.renderInOwnPass(drawData, commandBuffer, target.getCurrentSceneImageView(),
                width, height);
    }

    @Override
    public long hostTextureHandle(@NotNull Identifier textureId) {
        return host.vkImageViewFor(textureId);
    }

    @Override
    public long registerTexture(long hostTextureHandle, @NotNull TextureFilter filter) {
        return registerNativeTexture(hostTextureHandle, VK_IMAGE_LAYOUT_GENERAL, filter);
    }

    @Override
    public long registerNativeTexture(long nativeHandle, int nativeLayout, @NotNull TextureFilter filter) {
        if (imgui == null || nativeHandle == VK_NULL_HANDLE) {
            return 0L;
        }
        return imgui.addTexture(samplerFor(filter), nativeHandle, nativeLayout);
    }

    @Override
    public void releaseTexture(long imGuiTextureId) {
        if (imgui == null || imGuiTextureId == 0L) {
            return;
        }
        try {
            imgui.removeTexture(imGuiTextureId);
        } catch (IllegalArgumentException exception) {
            LOGGER.debug("Ignoring release of unknown Vulkan texture {}", imGuiTextureId);
        }
    }

    @Override
    public void configureFrostedGlass(boolean enabled, @NotNull FrostedGlassStyle style) {
        glassEnabled = enabled;
        glassStyle = Objects.requireNonNull(style, "style");
        if (glass != null) {
            glass.applyStyle(style);
        }
    }

    @Override
    public boolean supportsFrostedGlass() {
        return true;
    }

    @Override
    public void shutdown() {
        if (glass != null) {
            glass.cleanup();
            glass = null;
        }
        if (imgui != null) {
            imgui.close();
            imgui = null;
        }
        if (sharedSampler != VK_NULL_HANDLE && context != null) {
            vkDestroySampler(context.device(), sharedSampler, null);
        }
        if (nearestSampler != VK_NULL_HANDLE && context != null) {
            vkDestroySampler(context.device(), nearestSampler, null);
        }
        sharedSampler = VK_NULL_HANDLE;
        nearestSampler = VK_NULL_HANDLE;
        context = null;
        glassFailed = false;
    }

    private long samplerFor(TextureFilter filter) {
        if (filter == TextureFilter.NEAREST) {
            if (nearestSampler == VK_NULL_HANDLE) {
                nearestSampler = createSampler(VK_FILTER_NEAREST, "nearest HUD texture");
            }
            return nearestSampler;
        }
        if (sharedSampler == VK_NULL_HANDLE) {
            sharedSampler = createSampler(VK_FILTER_LINEAR, "shared HUD texture");
        }
        return sharedSampler;
    }

    private long createSampler(int filter, String what) {
        try (MemoryStack stack = stackPush()) {
            VkSamplerCreateInfo createInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO)
                    .magFilter(filter)
                    .minFilter(filter)
                    .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST)
                    .addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                    .maxAnisotropy(1.0f)
                    .minLod(0.0f)
                    .maxLod(0.0f);
            LongBuffer pSampler = stack.mallocLong(1);
            int result = vkCreateSampler(context.device(), createInfo, null, pSampler);
            if (result != VK_SUCCESS) {
                throw new IllegalStateException(
                        "vkCreateSampler (" + what + ") failed with VkResult " + result);
            }
            return pSampler.get(0);
        }
    }
}
