package dev.technix.mica.api.compat.v26_2;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.SpriteBounds;
import dev.technix.mica.api.VanillaAtlases;
import dev.technix.mica.internal.backend.BackendClassifier;
import dev.technix.mica.internal.backend.opengl.OpenGLHostAccess;
import dev.technix.mica.internal.backend.vulkan.VulkanContext;
import dev.technix.mica.internal.backend.vulkan.VulkanHostAccess;
import dev.technix.mica.mixin.client.CommandEncoderAccessor;
import dev.technix.mica.mixin.client.GpuDeviceAccessor;
import dev.technix.mica.mixin.client.VulkanCommandEncoderAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import java.util.Optional;

import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;


/**
 * Minecraft 26.2 adapter. Detects which backend Minecraft's {@code GpuDevice} runs on and
 * provides the host access for both of Mica's renderers: {@link VulkanHostAccess} (device,
 * frame command buffer, image views) and {@link OpenGLHostAccess} (main render target and
 * texture names). This is the only class allowed to touch Mojang's rendering internals.
 */
public final class MinecraftCompatImpl_26_2 implements MinecraftCompat, VulkanHostAccess,
        OpenGLHostAccess {

    // ---- backend detection ---------------------------------------------------------------

    @Override
    @NotNull
    public Optional<RenderBackendType> renderBackend() {
        GpuDeviceBackend backend = deviceBackend();
        if (backend == null) {
            return Optional.empty();
        }
        if (backend instanceof VulkanDevice) {
            return Optional.of(RenderBackendType.VULKAN);
        }
        return Optional.of(BackendClassifier.classify(backend.getClass().getName()));
    }

    @Override
    @NotNull
    public String minecraftVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("26.2");
    }

    @Override
    public boolean isOnRenderThread() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.isSameThread();
    }

    // ---- Vulkan --------------------------------------------------------------------------

    @Override
    @NotNull
    public Optional<VulkanContext> currentVulkanContext() {
        VulkanDevice device = vulkanDevice();
        if (device == null) {
            return Optional.empty();
        }
        VkDevice vkDevice = device.vkDevice();
        VulkanQueue graphicsQueue = device.graphicsQueue();
        if (vkDevice == null || graphicsQueue == null) {
            return Optional.empty();
        }

        long sceneImage = 0L;
        long sceneImageView = 0L;
        int sceneLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        int colorFormat = 0;
        int width = 0;
        int height = 0;

        RenderTarget target = mainRenderTarget0();
        if (target != null) {
            GpuTexture colorTexture = target.getColorTexture();
            GpuTextureView colorView = target.getColorTextureView();
            if (colorTexture instanceof VulkanGpuTexture vulkanTexture
                    && colorView instanceof VulkanGpuTextureView vulkanView) {
                sceneImage = vulkanTexture.vkImage();
                sceneImageView = vulkanView.vkImageView();



                sceneLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
                colorFormat = VulkanConst.toVk(colorTexture.getFormat());
                width = target.width;
                height = target.height;
            }
        }

        VulkanContext ctx = new VulkanContext(
                device.instance().vkInstance(),
                vkDevice.getPhysicalDevice(),
                vkDevice,
                graphicsQueue.vkQueue(),
                graphicsQueue.queueFamilyIndex(),
                0,
                1,
                width,
                height,
                sceneImage,
                sceneImageView,
                sceneLayout,
                colorFormat);
        return Optional.of(ctx);
    }

    /** {@code true} when Minecraft renders with Vulkan and its main render target exists. */
    public boolean isVulkanRendererActive() {
        return vulkanDevice() != null && mainRenderTarget0() != null;
    }

    @Override
    @Nullable
    public VkCommandBuffer activeCommandBuffer() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return null;
        }
        CommandEncoder encoder = device.createCommandEncoder();
        CommandEncoderBackend backend = ((CommandEncoderAccessor) (Object) encoder).imgui$backend();
        if (!(backend instanceof VulkanCommandEncoder vulkanEncoder)) {
            return null;
        }
        VulkanCommandEncoderAccessor accessor = (VulkanCommandEncoderAccessor) (Object) vulkanEncoder;
        if (accessor.imgui$currentRenderPass() != null) {
            return null;
        }
        return accessor.imgui$currentCommandBuffer();
    }

    @Override
    public long vkImageViewFor(@NotNull Identifier textureId) {
        GpuTextureView view = textureView(textureId);
        return view instanceof VulkanGpuTextureView vulkanView ? vulkanView.vkImageView() : 0L;
    }

    // ---- OpenGL --------------------------------------------------------------------------

    @Override
    @Nullable
    public MainTarget mainRenderTarget() {
        if (renderBackend().orElse(null) != RenderBackendType.OPENGL) {
            return null;
        }
        RenderTarget target = mainRenderTarget0();
        if (target == null) {
            return null;
        }
        int colorTexture = GlTextureNames.of(target.getColorTexture());
        if (colorTexture == 0 || target.width <= 0 || target.height <= 0) {
            return null;
        }
        return new MainTarget(colorTexture, target.width, target.height);
    }

    @Override
    public int glTextureIdFor(@NotNull Identifier textureId) {
        GpuTextureView view = textureView(textureId);
        return view != null ? GlTextureNames.of(view.texture()) : 0;
    }

    // ---- sprites -------------------------------------------------------------------------

    @Override
    @NotNull
    public Optional<SpriteBounds> locateSprite(@NotNull Identifier atlasId, @NotNull Identifier spriteId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return Optional.empty();
        }
        TextureAtlas atlas = resolveAtlas(minecraft, atlasId);
        if (atlas == null) {
            return Optional.empty();
        }
        TextureAtlasSprite sprite = atlas.getSprite(spriteId);
        if (sprite == atlas.missingSprite()) {
            return Optional.empty();
        }
        return Optional.of(new SpriteBounds(atlas.location(),
                sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1()));
    }


    private static @Nullable TextureAtlas resolveAtlas(Minecraft minecraft, Identifier atlasId) {
        if (minecraft.getTextureManager().getTexture(atlasId) instanceof TextureAtlas atlas) {
            return atlas;
        }
        if (atlasId.equals(VanillaAtlases.ITEMS)
                && minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_ITEMS)
                        instanceof TextureAtlas itemsAtlas) {
            return itemsAtlas;
        }
        if (atlasId.equals(VanillaAtlases.BLOCKS)
                && minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS)
                        instanceof TextureAtlas blockAtlas) {
            return blockAtlas;
        }
        return null;
    }

    // ---- helpers -------------------------------------------------------------------------

    @Nullable
    private static GpuDeviceBackend deviceBackend() {
        GpuDevice device = RenderSystem.tryGetDevice();
        if (device == null) {
            return null;
        }
        return ((GpuDeviceAccessor) (Object) device).imgui$backend();
    }

    @Nullable
    private static VulkanDevice vulkanDevice() {
        return deviceBackend() instanceof VulkanDevice vulkanDevice ? vulkanDevice : null;
    }

    @Nullable
    private static GpuTextureView textureView(Identifier textureId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getTextureManager() == null) {
            return null;
        }
        AbstractTexture texture = minecraft.getTextureManager().getTexture(textureId);
        return texture != null ? texture.getTextureView() : null;
    }


    @Nullable
    private static RenderTarget mainRenderTarget0() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameRenderer == null) {
            return null;
        }
        return minecraft.gameRenderer.mainRenderTarget();
    }


    @NotNull
    public static Optional<SpriteBounds> itemIcon(@NotNull ItemStack stack,
                                                   @NotNull MinecraftCompatImpl_26_2 compat) {
        return compat.locateItemIcon(stack);
    }
}
