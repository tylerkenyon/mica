package dev.technix.mica.api;

import dev.technix.mica.internal.backend.vulkan.VulkanContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.Optional;



public interface MinecraftCompat {


    @NotNull
    Optional<VulkanContext> currentVulkanContext();



    boolean isVulkanRendererActive();

    @Nullable
    VkCommandBuffer activeCommandBuffer();

    long vkImageViewFor(@NotNull Identifier textureId);



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
