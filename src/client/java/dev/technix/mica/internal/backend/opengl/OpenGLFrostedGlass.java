package dev.technix.mica.internal.backend.opengl;

import dev.technix.mica.api.FrostedGlassStyle;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GLCapabilities;

import static org.lwjgl.opengl.GL33.*;


/**
 * OpenGL port of the Vulkan {@code FrostedGlassRenderer}: downsample Minecraft's main render
 * target into a reduced-size texture with a linear {@code glBlitFramebuffer}, then run the
 * same 9-tap Kawase kernel as the Vulkan compute shader, ping-ponging between two textures
 * with a full-screen fragment pass (compute shaders are not available on OpenGL 4.1/macOS).
 */
final class OpenGLFrostedGlass {

    private static final String VERTEX_SHADER = """
            #version 150
            void main() {
                vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String KAWASE_FRAGMENT_SHADER = """
            #version 150
            uniform sampler2D InputTexture;
            out vec4 Out_Color;

            vec4 sampleClamped(ivec2 coordinate, ivec2 extent) {
                return texelFetch(InputTexture, clamp(coordinate, ivec2(0), extent - ivec2(1)), 0);
            }

            void main() {
                ivec2 extent = textureSize(InputTexture, 0);
                ivec2 pixel = ivec2(gl_FragCoord.xy);
                vec4 color = sampleClamped(pixel, extent) * 0.25;
                color += sampleClamped(pixel + ivec2(-2, -2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2( 2, -2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2(-2,  2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2( 2,  2), extent) * 0.125;
                color += sampleClamped(pixel + ivec2(-1,  0), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 1,  0), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 0, -1), extent) * 0.0625;
                color += sampleClamped(pixel + ivec2( 0,  1), extent) * 0.0625;
                Out_Color = vec4(color.rgb, 1.0);
            }
            """;

    private final GLCapabilities caps;

    private int program;
    private int uniformInput;
    private int emptyVertexArray;

    private final int[] textures = new int[2];
    private final int[] framebuffers = new int[2];
    private int blurWidth;
    private int blurHeight;

    private int blurPasses = 5;
    private int blurScaleDivisor = 2;

    private int resultIndex = -1;

    OpenGLFrostedGlass(@NotNull GLCapabilities caps) {
        this.caps = caps;
    }

    void init() {
        int vertex = OpenGLImGuiBackend.compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment;
        try {
            fragment = OpenGLImGuiBackend.compileShader(GL_FRAGMENT_SHADER, KAWASE_FRAGMENT_SHADER);
        } catch (RuntimeException exception) {
            glDeleteShader(vertex);
            throw exception;
        }
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glBindFragDataLocation(program, 0, "Out_Color");
        glLinkProgram(program);
        glDetachShader(program, vertex);
        glDetachShader(program, fragment);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            cleanup();
            throw new IllegalStateException("Linking the frosted-glass program failed: " + log);
        }
        uniformInput = glGetUniformLocation(program, "InputTexture");
        emptyVertexArray = glGenVertexArrays();
    }

    void applyStyle(@NotNull FrostedGlassStyle style) {
        blurPasses = style.blurPasses();
        blurScaleDivisor = style.blurScaleDivisor();
    }

    /**
     * Blurs the colour attachment of {@code sourceFramebuffer}. Returns {@code true} when
     * {@link #resultTexture()} holds this frame's backdrop.
     */
    boolean record(int sourceFramebuffer, int fullWidth, int fullHeight) {
        if (program == 0 || sourceFramebuffer == 0 || fullWidth <= 0 || fullHeight <= 0) {
            return false;
        }
        GlStateSnapshot saved = GlStateSnapshot.capture(caps);
        try {
            ensureTargets(Math.max(1, fullWidth / blurScaleDivisor),
                    Math.max(1, fullHeight / blurScaleDivisor));

            glDisable(GL_SCISSOR_TEST);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, sourceFramebuffer);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            glBlitFramebuffer(0, 0, fullWidth, fullHeight, 0, 0, blurWidth, blurHeight,
                    GL_COLOR_BUFFER_BIT, GL_LINEAR);

            GlStateSnapshot.applyOverlayDefaults(caps.OpenGL31);
            glDisable(GL_BLEND);
            glViewport(0, 0, blurWidth, blurHeight);
            glUseProgram(program);
            glUniform1i(uniformInput, 0);
            glBindVertexArray(emptyVertexArray);
            glActiveTexture(GL_TEXTURE0);
            if (caps.OpenGL33 || caps.GL_ARB_sampler_objects) {
                glBindSampler(0, 0);
            }
            int source = 0;
            for (int pass = 0; pass < blurPasses; pass++) {
                int destination = 1 - source;
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffers[destination]);
                glBindTexture(GL_TEXTURE_2D, textures[source]);
                glDrawArrays(GL_TRIANGLES, 0, 3);
                source = destination;
            }
            resultIndex = source;
            return true;
        } finally {
            saved.restore();
        }
    }

    int resultTexture() {
        return resultIndex < 0 ? 0 : textures[resultIndex];
    }

    private void ensureTargets(int width, int height) {
        if (textures[0] != 0 && width == blurWidth && height == blurHeight) {
            return;
        }
        destroyTargets();
        for (int index = 0; index < 2; index++) {
            textures[index] = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textures[index]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE,
                    (java.nio.ByteBuffer) null);

            framebuffers[index] = glGenFramebuffers();
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffers[index]);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                    textures[index], 0);
            int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                destroyTargets();
                throw new IllegalStateException("Frosted-glass framebuffer incomplete: 0x"
                        + Integer.toHexString(status));
            }
        }
        blurWidth = width;
        blurHeight = height;
    }

    private void destroyTargets() {
        for (int index = 0; index < 2; index++) {
            if (framebuffers[index] != 0) glDeleteFramebuffers(framebuffers[index]);
            if (textures[index] != 0) glDeleteTextures(textures[index]);
            framebuffers[index] = 0;
            textures[index] = 0;
        }
        blurWidth = 0;
        blurHeight = 0;
        resultIndex = -1;
    }

    void cleanup() {
        destroyTargets();
        if (emptyVertexArray != 0) glDeleteVertexArrays(emptyVertexArray);
        if (program != 0) glDeleteProgram(program);
        emptyVertexArray = 0;
        program = 0;
    }
}
