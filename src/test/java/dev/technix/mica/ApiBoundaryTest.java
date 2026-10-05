package dev.technix.mica;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps rendering-backend specifics out of the public API and out of Mica's core. Only the
 * backend packages and the version compat adapters may name Vulkan or OpenGL types.
 */
class ApiBoundaryTest {

    /** Gradle passes the shared source tree, since tests run once per Minecraft version subproject. */
    private static final Path SOURCES = Path.of(System.getProperty("mica.sourceRoot", "src/client/java"))
            .resolve("dev/technix/mica");

    private static final List<String> BACKEND_SPECIFIC = List.of(
            "org.lwjgl.vulkan", "org.lwjgl.opengl", "com.mojang.blaze3d.vulkan",
            "com.mojang.blaze3d.opengl", "internal.backend.vulkan", "internal.backend.opengl",
            "VkCommandBuffer", "VkImageView", "VkDescriptorSet", "VulkanContext");

    @Test
    void publicApiIsBackendIndependent() throws IOException {
        assertClean(SOURCES.resolve("api"), path -> !path.toString().contains("compat"));
    }

    @Test
    void coreIsBackendIndependent() throws IOException {
        assertClean(SOURCES.resolve("internal"), path -> !path.toString().contains("backend")
                || path.getFileName().toString().equals("RenderBackend.java")
                || path.getFileName().toString().equals("RenderBackendRegistry.java"));
    }

    private static void assertClean(Path root, java.util.function.Predicate<Path> include)
            throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).filter(include).toList()) {
                List<String> lines = Files.readAllLines(file);
                for (int index = 0; index < lines.size(); index++) {
                    String line = lines.get(index);
                    if (line.trim().startsWith("*") || line.trim().startsWith("//")) {
                        continue;
                    }
                    for (String forbidden : BACKEND_SPECIFIC) {
                        if (line.contains(forbidden)) {
                            violations.add(file + ":" + (index + 1) + " references " + forbidden);
                        }
                    }
                }
            }
        }
        assertTrue(violations.isEmpty(), String.join("\n", violations));
    }
}
