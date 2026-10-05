package dev.technix.mica.internal.backend.opengl;

import org.lwjgl.opengl.GLCapabilities;

import static org.lwjgl.opengl.GL33.*;

/**
 * Every piece of OpenGL state Mica's renderer touches, captured with {@code glGet*} and put
 * back exactly. Minecraft's {@code GlStateManager} caches GL state on the Java side; restoring
 * the real values keeps that cache truthful, so Minecraft never notices Mica drew anything.
 */
final class GlStateSnapshot {

    private static final int MAX_TRACKED_DRAW_BUFFERS = 8;

    private final boolean hasSamplers;
    private final boolean hasPrimitiveRestart;
    private final int drawBuffers;

    private int program;
    private int activeTexture;
    private int textureBinding;
    private int samplerBinding;
    private int arrayBuffer;
    private int vertexArray;
    private int drawFramebuffer;
    private int readFramebuffer;
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private final boolean[] blendEnabled;
    private final boolean[][] colorMask;
    private boolean cullFace;
    private boolean depthTest;
    private boolean stencilTest;
    private boolean scissorTest;
    private boolean primitiveRestart;
    private boolean framebufferSrgb;
    private boolean depthMask;
    private int polygonMode;
    private int unpackAlignment;
    private int unpackRowLength;
    private int unpackSkipPixels;
    private int unpackSkipRows;
    private int unpackBuffer;

    private GlStateSnapshot(GLCapabilities caps) {
        this.hasSamplers = caps.OpenGL33 || caps.GL_ARB_sampler_objects;
        this.hasPrimitiveRestart = caps.OpenGL31;
        this.drawBuffers = Math.max(1, Math.min(MAX_TRACKED_DRAW_BUFFERS,
                glGetInteger(GL_MAX_DRAW_BUFFERS)));
        this.blendEnabled = new boolean[drawBuffers];
        this.colorMask = new boolean[drawBuffers][4];
    }

    static GlStateSnapshot capture(GLCapabilities caps) {
        GlStateSnapshot s = new GlStateSnapshot(caps);
        s.program = glGetInteger(GL_CURRENT_PROGRAM);
        s.activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        s.textureBinding = glGetInteger(GL_TEXTURE_BINDING_2D);
        s.samplerBinding = s.hasSamplers ? glGetInteger(GL_SAMPLER_BINDING) : 0;
        s.arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        s.vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        s.drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        s.readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        glGetIntegerv(GL_VIEWPORT, s.viewport);
        glGetIntegerv(GL_SCISSOR_BOX, s.scissorBox);
        s.blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        s.blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        s.blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        s.blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        s.blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        s.blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.ByteBuffer mask = stack.malloc(4);
            for (int index = 0; index < s.drawBuffers; index++) {
                s.blendEnabled[index] = glIsEnabledi(GL_BLEND, index);
                glGetBooleani_v(GL_COLOR_WRITEMASK, index, mask);
                for (int channel = 0; channel < 4; channel++) {
                    s.colorMask[index][channel] = mask.get(channel) != 0;
                }
            }
        }
        s.cullFace = glIsEnabled(GL_CULL_FACE);
        s.depthTest = glIsEnabled(GL_DEPTH_TEST);
        s.stencilTest = glIsEnabled(GL_STENCIL_TEST);
        s.scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        s.primitiveRestart = s.hasPrimitiveRestart && glIsEnabled(GL_PRIMITIVE_RESTART);
        s.framebufferSrgb = glIsEnabled(GL_FRAMEBUFFER_SRGB);
        s.depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int[] modes = new int[2];
        glGetIntegerv(GL_POLYGON_MODE, modes);
        s.polygonMode = modes[0];
        s.unpackAlignment = glGetInteger(GL_UNPACK_ALIGNMENT);
        s.unpackRowLength = glGetInteger(GL_UNPACK_ROW_LENGTH);
        s.unpackSkipPixels = glGetInteger(GL_UNPACK_SKIP_PIXELS);
        s.unpackSkipRows = glGetInteger(GL_UNPACK_SKIP_ROWS);
        s.unpackBuffer = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        return s;
    }

    void restore() {
        glUseProgram(program);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureBinding);
        if (hasSamplers) {
            glBindSampler(0, samplerBinding);
        }
        glActiveTexture(activeTexture);
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        for (int index = 0; index < drawBuffers; index++) {
            if (blendEnabled[index]) {
                glEnablei(GL_BLEND, index);
            } else {
                glDisablei(GL_BLEND, index);
            }
            boolean[] mask = colorMask[index];
            glColorMaski(index, mask[0], mask[1], mask[2], mask[3]);
        }
        set(GL_CULL_FACE, cullFace);
        set(GL_DEPTH_TEST, depthTest);
        set(GL_STENCIL_TEST, stencilTest);
        set(GL_SCISSOR_TEST, scissorTest);
        if (hasPrimitiveRestart) {
            set(GL_PRIMITIVE_RESTART, primitiveRestart);
        }
        set(GL_FRAMEBUFFER_SRGB, framebufferSrgb);
        glDepthMask(depthMask);
        glPolygonMode(GL_FRONT_AND_BACK, polygonMode);
        glPixelStorei(GL_UNPACK_ALIGNMENT, unpackAlignment);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, unpackRowLength);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, unpackSkipPixels);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, unpackSkipRows);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
    }

    /** The state Mica's own passes draw with: no depth/stencil/cull, full colour writes. */
    static void applyOverlayDefaults(boolean hasPrimitiveRestart) {
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
        if (hasPrimitiveRestart) {
            glDisable(GL_PRIMITIVE_RESTART);
        }
        glDepthMask(false);
        glColorMask(true, true, true, true);
        glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
    }

    private static void set(int capability, boolean enabled) {
        if (enabled) {
            glEnable(capability);
        } else {
            glDisable(capability);
        }
    }
}
