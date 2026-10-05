package dev.technix.mica.internal.backend;

import dev.technix.mica.api.RenderBackendType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendClassifierTest {

    @Test
    void classifiesVulkanDevice() {
        assertEquals(RenderBackendType.VULKAN,
                BackendClassifier.classify("com.mojang.blaze3d.vulkan.VulkanDevice"));
    }

    @Test
    void classifiesPackagePrivateOpenGlDevice() {
        assertEquals(RenderBackendType.OPENGL,
                BackendClassifier.classify("com.mojang.blaze3d.opengl.GlDevice"));
    }

    @Test
    void unknownBackendsAreNotGuessed() {
        assertEquals(RenderBackendType.UNKNOWN,
                BackendClassifier.classify("com.example.d3d12.D3D12Device"));
        assertEquals(RenderBackendType.UNKNOWN,
                BackendClassifier.classify("com.mojang.blaze3d.openglx.Fake"));
    }
}
