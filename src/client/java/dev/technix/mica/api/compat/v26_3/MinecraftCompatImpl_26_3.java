//? if >=26.3 {
/*package dev.technix.mica.api.compat.v26_3;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanQueue;
import com.mojang.renderpearl.frontend.FrontendCommandEncoder;
import dev.technix.mica.api.MinecraftCompat;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.SpriteBounds;
import dev.technix.mica.api.VanillaAtlases;
import dev.technix.mica.internal.backend.BackendClassifier;
import dev.technix.mica.internal.backend.opengl.GlTextureNames;
import dev.technix.mica.internal.backend.opengl.OpenGLHostAccess;
import dev.technix.mica.internal.backend.vulkan.VulkanContext;
import dev.technix.mica.internal.backend.vulkan.VulkanHostAccess;
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


/^*
 * Minecraft 26.3 adapter (Renderpearl rendering, SDL input; compiled only into the 26.3 jar,
 * see {@code docs/mica/multiversion.md}). Same responsibilities as the 26.2 adapter; the
 * differences are Mojang's 26.3 moves: Blaze3d's device, encoder, texture and backend classes
 * now live in {@code com.mojang.renderpearl}, {@code FrontendCommandEncoder.backend()} is
 * public, the Vulkan encoder's per-frame buffer is {@code objectInitCommandBuffer()}, and
 * SDL needs text input switched on explicitly. Detects which backend Minecraft's {@code GpuDevice} runs on and
 * provides the host access for both of Mica's renderers: {@link VulkanHostAccess} (device,
 * frame command buffer, image views) and {@link OpenGLHostAccess} (main render target and
 * texture names). This is the only class allowed to touch Mojang's rendering internals.
 ^/
public final class MinecraftCompatImpl_26_3 implements MinecraftCompat, VulkanHostAccess,
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

    @Override
    public void setTextInputActive(boolean active) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.textInputManager() != null) {
            minecraft.textInputManager().onTextInputFocusChange(TEXT_INPUT_OWNER, active);
        }
    }

    @Override
    public double @Nullable [] cursorPosition() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.mouseHandler == null) {
            return null;
        }
        return new double[] {minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos()};
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

    /^* {@code true} when Minecraft renders with Vulkan and its main render target exists. ^/
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
        if (!(encoder instanceof FrontendCommandEncoder frontend)) {
            return null;
        }
        CommandEncoderBackend backend = frontend.backend();
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

    private static final Object TEXT_INPUT_OWNER = new Object();


    @NotNull
    public static Optional<SpriteBounds> itemIcon(@NotNull ItemStack stack,
                                                   @NotNull MinecraftCompatImpl_26_3 compat) {
        return compat.locateItemIcon(stack);
    }
}
*///?}
