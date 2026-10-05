package dev.technix.mica.internal.backend.opengl;

import dev.technix.mica.api.TextureFilter;
import imgui.ImDrawData;
import imgui.ImFontAtlas;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.ImVec4;
import imgui.flag.ImGuiBackendFlags;
import imgui.type.ImInt;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33.*;


/**
 * Dear ImGui draw-data renderer on Minecraft's OpenGL context.
 *
 * <p>This follows imgui-java's {@code imgui.gl3.ImGuiImplGl3} (itself a port of Dear ImGui's
 * {@code imgui_impl_opengl3}) but is kept in-tree for two reasons specific to Minecraft 26.2:
 * <ul>
 *   <li>{@code ImGuiImplGl3} binds sampler object 0 for every draw, so a texture is sampled
 *       with its own texture parameters. Minecraft 26.x samples through sampler objects and
 *       does not configure per-texture filtering, so atlases would sample incorrectly and
 *       {@link TextureFilter} could not be honoured. Here every ImGui texture id carries its
 *       own sampler.</li>
 *   <li>It would pull in the {@code imgui-java-lwjgl3} artifact, whose POM drags a second set
 *       of LWJGL modules (including SDL) into the dependency graph. Minecraft already ships
 *       the LWJGL OpenGL bindings this class uses.</li>
 * </ul>
 *
 * <p>Never creates a context or window; every call runs on Minecraft's render thread with
 * Minecraft's context current, and all GL state is captured and restored around each draw.
 */
final class OpenGLImGuiBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private static final int ATTRIB_POSITION = 0;
    private static final int ATTRIB_UV = 1;
    private static final int ATTRIB_COLOR = 2;

    private static final String VERTEX_SHADER = """
            #version 150
            uniform mat4 ProjMtx;
            in vec2 Position;
            in vec2 UV;
            in vec4 Color;
            out vec2 Frag_UV;
            out vec4 Frag_Color;
            void main() {
                Frag_UV = UV;
                Frag_Color = Color;
                gl_Position = ProjMtx * vec4(Position.xy, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 150
            uniform sampler2D Texture;
            in vec2 Frag_UV;
            in vec4 Frag_Color;
            out vec4 Out_Color;
            void main() {
                Out_Color = Frag_Color * texture(Texture, Frag_UV.st);
            }
            """;

    private static final class TextureEntry {
        int glTexture;
        final TextureFilter filter;

        TextureEntry(int glTexture, TextureFilter filter) {
            this.glTexture = glTexture;
            this.filter = filter;
        }
    }

    private GLCapabilities caps;
    private boolean hasSamplers;
    private boolean hasClipControl;

    private int program;
    private int uniformProjection;
    private int uniformTexture;
    private int vertexArray;
    private int vertexBuffer;
    private int indexBuffer;
    private int linearSampler;
    private int nearestSampler;

    private int fontTexture;
    private long fontTextureId;

    private final Map<Long, TextureEntry> textures = new HashMap<>();
    private long nextTextureId = 1L;

    private final float[] projection = new float[16];
    private final ImVec4 clipRect = new ImVec4();

    private boolean initialized;

    GLCapabilities capabilities() {
        return caps;
    }

    boolean isInitialized() {
        return initialized;
    }

    void init() {
        if (initialized) {
            return;
        }
        try {
            caps = GL.getCapabilities();
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("No OpenGL context is current on the render thread "
                    + "(Mica never creates its own context).", exception);
        }
        if (!caps.OpenGL32) {
            throw new IllegalStateException("OpenGL 3.2 core is required, the context reports "
                    + glGetString(GL_VERSION));
        }
        hasSamplers = caps.OpenGL33 || caps.GL_ARB_sampler_objects;
        hasClipControl = caps.OpenGL45 || caps.GL_ARB_clip_control;

        GlStateSnapshot saved = GlStateSnapshot.capture(caps);
        try {
            createProgram();
            createBuffers();
            if (hasSamplers) {
                linearSampler = createSampler(GL_LINEAR);
                nearestSampler = createSampler(GL_NEAREST);
            }
            createFontsTexture();
        } catch (RuntimeException exception) {
            saved.restore();
            destroyObjects();
            throw exception;
        }
        saved.restore();

        ImGuiIO io = ImGui.getIO();
        io.setBackendRendererName("mica_impl_opengl3");
        io.addBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
        initialized = true;
        LOGGER.debug("OpenGL ImGui backend initialised (samplers={}, clipControl={})",
                hasSamplers, hasClipControl);
    }

    long registerTexture(int glTexture, @NotNull TextureFilter filter) {
        if (!initialized || glTexture == 0) {
            return 0L;
        }
        long id = nextTextureId++;
        textures.put(id, new TextureEntry(glTexture, filter));
        return id;
    }

    void updateTexture(long textureId, int glTexture) {
        TextureEntry entry = textures.get(textureId);
        if (entry != null) {
            entry.glTexture = glTexture;
        }
    }

    void releaseTexture(long textureId) {
        if (textureId != fontTextureId) {
            textures.remove(textureId);
        }
    }

    /**
     * Draws {@code drawData} into {@code framebuffer} (a framebuffer object whose colour
     * attachment is Minecraft's main render target).
     */
    void render(@NotNull ImDrawData drawData, int framebuffer, int targetWidth, int targetHeight) {
        if (!initialized || !drawData.getValid() || drawData.getCmdListsCount() <= 0) {
            return;
        }
        int fbWidth = (int) (drawData.getDisplaySizeX() * drawData.getFramebufferScaleX());
        int fbHeight = (int) (drawData.getDisplaySizeY() * drawData.getFramebufferScaleY());
        if (fbWidth <= 0 || fbHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return;
        }
        int vertexStride = ImDrawData.sizeOfImDrawVert();
        int indexStride = ImDrawData.sizeOfImDrawIdx();
        long vertexBytes = (long) drawData.getTotalVtxCount() * vertexStride;
        long indexBytes = (long) drawData.getTotalIdxCount() * indexStride;
        if (vertexBytes == 0 || indexBytes == 0) {
            return;
        }

        GlStateSnapshot saved = GlStateSnapshot.capture(caps);
        try {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            setupRenderState(drawData, fbWidth, fbHeight);

            glBufferData(GL_ARRAY_BUFFER, vertexBytes, GL_STREAM_DRAW);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexBytes, GL_STREAM_DRAW);
            long vertexOffset = 0;
            long indexOffset = 0;
            for (int listIndex = 0; listIndex < drawData.getCmdListsCount(); listIndex++) {
                ByteBuffer vertices = drawData.getCmdListVtxBufferData(listIndex);
                int vertexSize = vertices.remaining();
                glBufferSubData(GL_ARRAY_BUFFER, vertexOffset, vertices);
                vertexOffset += vertexSize;
                ByteBuffer indices = drawData.getCmdListIdxBufferData(listIndex);
                int indexSize = indices.remaining();
                glBufferSubData(GL_ELEMENT_ARRAY_BUFFER, indexOffset, indices);
                indexOffset += indexSize;
            }

            int indexType = indexStride == 2 ? GL_UNSIGNED_SHORT : GL_UNSIGNED_INT;
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
                    drawData.getCmdListCmdBufferClipRect(clipRect, listIndex, commandIndex);
                    float clipMinX = (clipRect.x - clipOffsetX) * clipScaleX;
                    float clipMinY = (clipRect.y - clipOffsetY) * clipScaleY;
                    float clipMaxX = (clipRect.z - clipOffsetX) * clipScaleX;
                    float clipMaxY = (clipRect.w - clipOffsetY) * clipScaleY;
                    if (clipMaxX <= clipMinX || clipMaxY <= clipMinY) {
                        continue;
                    }
                    glScissor((int) clipMinX, (int) (fbHeight - clipMaxY),
                            (int) (clipMaxX - clipMinX), (int) (clipMaxY - clipMinY));

                    bindTexture(drawData.getCmdListCmdBufferTextureId(listIndex, commandIndex));

                    int idxOffset = drawData.getCmdListCmdBufferIdxOffset(listIndex, commandIndex)
                            + globalIndexOffset;
                    int vtxOffset = drawData.getCmdListCmdBufferVtxOffset(listIndex, commandIndex)
                            + globalVertexOffset;
                    glDrawElementsBaseVertex(GL_TRIANGLES, elementCount, indexType,
                            (long) idxOffset * indexStride, vtxOffset);
                }
                globalIndexOffset += drawData.getCmdListIdxBufferSize(listIndex);
                globalVertexOffset += drawData.getCmdListVtxBufferSize(listIndex);
            }
        } finally {
            saved.restore();
        }
    }

    private void setupRenderState(ImDrawData drawData, int fbWidth, int fbHeight) {
        GlStateSnapshot.applyOverlayDefaults(caps.OpenGL31);
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_SCISSOR_TEST);
        glViewport(0, 0, fbWidth, fbHeight);

        float left = drawData.getDisplayPosX();
        float right = left + drawData.getDisplaySizeX();
        float top = drawData.getDisplayPosY();
        float bottom = top + drawData.getDisplaySizeY();
        if (hasClipControl && glGetInteger(org.lwjgl.opengl.GL45.GL_CLIP_ORIGIN)
                == org.lwjgl.opengl.GL45.GL_UPPER_LEFT) {
            float swap = top;
            top = bottom;
            bottom = swap;
        }
        projection[0] = 2.0f / (right - left);
        projection[5] = 2.0f / (top - bottom);
        projection[10] = -1.0f;
        projection[12] = (right + left) / (left - right);
        projection[13] = (top + bottom) / (bottom - top);
        projection[15] = 1.0f;

        glUseProgram(program);
        glUniform1i(uniformTexture, 0);
        glUniformMatrix4fv(uniformProjection, false, projection);
        glActiveTexture(GL_TEXTURE0);
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
    }

    private void bindTexture(long textureId) {
        TextureEntry entry = textures.get(textureId == 0L ? fontTextureId : textureId);
        if (entry == null) {
            entry = textures.get(fontTextureId);
        }
        glBindTexture(GL_TEXTURE_2D, entry != null ? entry.glTexture : 0);
        if (hasSamplers) {
            glBindSampler(0, entry != null && entry.filter == TextureFilter.NEAREST
                    ? nearestSampler : linearSampler);
        }
    }

    private void createProgram() {
        int vertex = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
        int fragment = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        glBindAttribLocation(program, ATTRIB_POSITION, "Position");
        glBindAttribLocation(program, ATTRIB_UV, "UV");
        glBindAttribLocation(program, ATTRIB_COLOR, "Color");
        glBindFragDataLocation(program, 0, "Out_Color");
        glLinkProgram(program);
        glDetachShader(program, vertex);
        glDetachShader(program, fragment);
        glDeleteShader(vertex);
        glDeleteShader(fragment);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(program);
            throw new IllegalStateException("Linking the ImGui OpenGL program failed: " + log);
        }
        uniformProjection = glGetUniformLocation(program, "ProjMtx");
        uniformTexture = glGetUniformLocation(program, "Texture");
    }

    static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Compiling a Mica OpenGL shader failed: " + log);
        }
        return shader;
    }

    private void createBuffers() {
        vertexBuffer = glGenBuffers();
        indexBuffer = glGenBuffers();
        vertexArray = glGenVertexArrays();
        int stride = ImDrawData.sizeOfImDrawVert();
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glEnableVertexAttribArray(ATTRIB_POSITION);
        glEnableVertexAttribArray(ATTRIB_UV);
        glEnableVertexAttribArray(ATTRIB_COLOR);
        glVertexAttribPointer(ATTRIB_POSITION, 2, GL_FLOAT, false, stride, 0L);
        glVertexAttribPointer(ATTRIB_UV, 2, GL_FLOAT, false, stride, 8L);
        glVertexAttribPointer(ATTRIB_COLOR, 4, GL_UNSIGNED_BYTE, true, stride, 16L);
    }

    private static int createSampler(int filter) {
        int sampler = glGenSamplers();
        glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, filter);
        glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, filter);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return sampler;
    }

    private void createFontsTexture() {
        ImFontAtlas fonts = ImGui.getIO().getFonts();
        ImInt width = new ImInt();
        ImInt height = new ImInt();
        ByteBuffer pixels = fonts.getTexDataAsRGBA32(width, height);
        if (width.get() <= 0 || height.get() <= 0) {
            throw new IllegalStateException("Font atlas build produced an empty texture");
        }
        fontTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        pixels.rewind();
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width.get(), height.get(), 0,
                GL_RGBA, GL_UNSIGNED_BYTE, pixels);

        fontTextureId = nextTextureId++;
        textures.put(fontTextureId, new TextureEntry(fontTexture, TextureFilter.LINEAR));
        fonts.setTexID(fontTextureId);
        LOGGER.debug("ImGui font atlas uploaded to OpenGL ({}x{})", width.get(), height.get());
    }

    void shutdown() {
        if (caps == null) {
            return;
        }
        destroyObjects();
        textures.clear();
        fontTextureId = 0L;
        if (initialized) {
            ImGuiIO io = ImGui.getIO();
            io.removeBackendFlags(ImGuiBackendFlags.RendererHasVtxOffset);
        }
        initialized = false;
    }

    private void destroyObjects() {
        if (fontTexture != 0) glDeleteTextures(fontTexture);
        if (linearSampler != 0) glDeleteSamplers(linearSampler);
        if (nearestSampler != 0) glDeleteSamplers(nearestSampler);
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        if (vertexBuffer != 0) glDeleteBuffers(vertexBuffer);
        if (indexBuffer != 0) glDeleteBuffers(indexBuffer);
        if (program != 0) glDeleteProgram(program);
        fontTexture = linearSampler = nearestSampler = 0;
        vertexArray = vertexBuffer = indexBuffer = program = 0;
    }
}
