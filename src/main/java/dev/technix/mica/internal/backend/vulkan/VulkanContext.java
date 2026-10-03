package dev.technix.mica.internal.backend.vulkan;

import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueue;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;



public final class VulkanContext {

    private final VkInstance instance;
    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue queue;


    private final int queueFamilyIndex;


    private final int subpass;


    private final int msaaSamples;


    private final int framebufferWidth;
    private final int framebufferHeight;



    private final long currentSceneImage;



    private final long currentSceneImageView;



    private final int currentSceneImageLayout;



    private final int colorAttachmentFormat;

    public VulkanContext(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
                         VkQueue queue, int queueFamilyIndex) {
        this(instance, physicalDevice, device, queue, queueFamilyIndex, 0, 1,
                0, 0);
    }

    public VulkanContext(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
                         VkQueue queue, int queueFamilyIndex, int subpass, int msaaSamples,
                         int framebufferWidth, int framebufferHeight) {
        this(instance, physicalDevice, device, queue, queueFamilyIndex, subpass, msaaSamples,
                framebufferWidth, framebufferHeight, 0L, 0L, VK_IMAGE_LAYOUT_UNDEFINED);
    }

    public VulkanContext(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
                         VkQueue queue, int queueFamilyIndex, int subpass, int msaaSamples,
                         int framebufferWidth, int framebufferHeight, long currentSceneImage,
                         long currentSceneImageView, int currentSceneImageLayout) {
        this(instance, physicalDevice, device, queue, queueFamilyIndex, subpass, msaaSamples,
                framebufferWidth, framebufferHeight, currentSceneImage, currentSceneImageView,
                currentSceneImageLayout, 0);
    }



    public VulkanContext(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
                         VkQueue queue, int queueFamilyIndex, int subpass, int msaaSamples,
                         int framebufferWidth, int framebufferHeight, long currentSceneImage,
                         long currentSceneImageView, int currentSceneImageLayout,
                         int colorAttachmentFormat) {
        this.instance = instance;
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.queue = queue;
        this.queueFamilyIndex = queueFamilyIndex;
        this.subpass = subpass;
        this.msaaSamples = msaaSamples;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
        this.currentSceneImage = currentSceneImage;
        this.currentSceneImageView = currentSceneImageView;
        this.currentSceneImageLayout = currentSceneImageLayout;
        this.colorAttachmentFormat = colorAttachmentFormat;
    }

    public VkInstance instance() {
        return instance;
    }

    public VkPhysicalDevice physicalDevice() {
        return physicalDevice;
    }

    public VkDevice device() {
        return device;
    }

    public VkQueue queue() {
        return queue;
    }

    public int queueFamilyIndex() {
        return queueFamilyIndex;
    }

    public int subpass() {
        return subpass;
    }

    public int msaaSamples() {
        return msaaSamples;
    }

    public Integer framebufferWidth() {
        return framebufferWidth > 0 ? framebufferWidth : null;
    }

    public Integer framebufferHeight() {
        return framebufferHeight > 0 ? framebufferHeight : null;
    }



    public long getCurrentSceneImage() {
        return currentSceneImage;
    }



    public long getCurrentSceneImageView() {
        return currentSceneImageView;
    }



    public int getCurrentSceneImageLayout() {
        return currentSceneImageLayout;
    }



    public int colorAttachmentFormat() {
        return colorAttachmentFormat;
    }
}
