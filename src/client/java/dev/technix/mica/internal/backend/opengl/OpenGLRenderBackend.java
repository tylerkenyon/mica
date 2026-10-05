package dev.technix.mica.internal.backend.opengl;

import dev.technix.mica.api.FrostedGlassStyle;
import dev.technix.mica.api.RenderBackendType;
import dev.technix.mica.api.TextureFilter;
import dev.technix.mica.internal.backend.RenderBackend;
import imgui.ImDrawData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

import static org.lwjgl.opengl.GL33.*;


/**
 * {@link RenderBackend} on Minecraft's OpenGL context.
 *
 * <p>Mica draws into a framebuffer object it owns whose only attachment is the colour texture
 * of Minecraft's main render target, so it neither depends on which framebuffer Minecraft
 * left bound nor touches Minecraft's framebuffer cache. The draw is
 * {@link OpenGLImGuiBackend}; the optional backdrop blur is {@link OpenGLFrostedGlass}.
 */
public final class OpenGLRenderBackend implements RenderBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private final OpenGLHostAccess host;

    private OpenGLImGuiBackend imgui;
    private OpenGLFrostedGlass glass;
    private boolean glassEnabled;
    private boolean glassFailed;
    private FrostedGlassStyle glassStyle = FrostedGlassStyle.DEFAULT;
    private long backdropTextureId;

    private int targetFramebuffer;
    private int attachedTexture;
    private int attachedWidth;
    private int attachedHeight;
    private boolean targetComplete;

    private OpenGLHostAccess.MainTarget frameTarget;
    private String description = "OpenGL";

    public OpenGLRenderBackend(@NotNull OpenGLHostAccess host) {
        this.host = Objects.requireNonNull(host, "host");
    }

    @Override
    public @NotNull RenderBackendType type() {
        return RenderBackendType.OPENGL;
    }

    @Override
    public boolean initialize() {
        if (imgui != null) {
            return true;
        }
        if (host.mainRenderTarget() == null) {
            return false;
        }
        OpenGLImGuiBackend backend = new OpenGLImGuiBackend();
        backend.init();
        imgui = backend;
        description = "OpenGL " + glGetString(GL_VERSION) + " on " + glGetString(GL_RENDERER)
                + " (" + glGetString(GL_VENDOR) + ")";
        LOGGER.info("Mica OpenGL renderer ready: {}", description);
        return true;
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
        frameTarget = host.mainRenderTarget();
        if (frameTarget == null || frameTarget.colorTexture() == 0) {
            return null;
        }
        return new Viewport(Math.max(1, frameTarget.width()), Math.max(1, frameTarget.height()));
    }

    @Override
    public void prepareFrame() {
        // Uploads happen synchronously on OpenGL; nothing to record ahead of the frame.
    }

    @Override
    public long recordBackdrop() {
        if (imgui == null || !glassEnabled || glassFailed || frameTarget == null) {
            return 0L;
        }
        try {
            if (glass == null) {
                OpenGLFrostedGlass created = new OpenGLFrostedGlass(imgui.capabilities());
                created.init();
                created.applyStyle(glassStyle);
                glass = created;
            }
            int framebuffer = targetFramebuffer(frameTarget);
            if (framebuffer == 0
                    || !glass.record(framebuffer, frameTarget.width(), frameTarget.height())) {
                return 0L;
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("OpenGL frosted glass unavailable; panels draw without blur", exception);
            glassFailed = true;
            destroyGlass();
            return 0L;
        }
        int texture = glass.resultTexture();
        if (texture == 0) {
            return 0L;
        }
        if (backdropTextureId == 0L) {
            backdropTextureId = imgui.registerTexture(texture, TextureFilter.LINEAR);
        } else {
            imgui.updateTexture(backdropTextureId, texture);
        }
        return backdropTextureId;
    }

    @Override
    public boolean isReadyToRender() {
        return imgui != null;
    }

    @Override
    public void render(@NotNull ImDrawData drawData) {
        if (imgui == null) {
            return;
        }
        OpenGLHostAccess.MainTarget target = host.mainRenderTarget();
        if (target == null) {
            return;
        }
        int framebuffer = targetFramebuffer(target);
        if (framebuffer != 0) {
            imgui.render(drawData, framebuffer, target.width(), target.height());
        }
    }

    /**
     * Mica's framebuffer object with Minecraft's main colour texture attached. The texture is
     * re-attached every frame because GL may hand a re-created texture the same name.
     */
    private int targetFramebuffer(OpenGLHostAccess.MainTarget target) {
        if (target.colorTexture() == 0 || target.width() <= 0 || target.height() <= 0) {
            return 0;
        }
        int previous = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (targetFramebuffer == 0) {
                targetFramebuffer = glGenFramebuffers();
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFramebuffer);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D,
                    target.colorTexture(), 0);
            boolean changed = target.colorTexture() != attachedTexture
                    || target.width() != attachedWidth || target.height() != attachedHeight;
            if (changed) {
                int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
                targetComplete = status == GL_FRAMEBUFFER_COMPLETE;
                if (!targetComplete) {
                    LOGGER.warn("Cannot draw into Minecraft's main render target (texture {}): "
                            + "framebuffer status 0x{}", target.colorTexture(),
                            Integer.toHexString(status));
                }
                attachedTexture = target.colorTexture();
                attachedWidth = target.width();
                attachedHeight = target.height();
            }
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previous);
        }
        return targetComplete ? targetFramebuffer : 0;
    }

    @Override
    public long hostTextureHandle(@NotNull Identifier textureId) {
        return Integer.toUnsignedLong(host.glTextureIdFor(textureId));
    }

    @Override
    public long registerTexture(long hostTextureHandle, @NotNull TextureFilter filter) {
        if (imgui == null) {
            return 0L;
        }
        return imgui.registerTexture((int) hostTextureHandle, filter);
    }

    @Override
    public void releaseTexture(long imGuiTextureId) {
        if (imgui != null) {
            imgui.releaseTexture(imGuiTextureId);
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
    public @NotNull String describe() {
        return description;
    }

    private void destroyGlass() {
        if (glass != null) {
            glass.cleanup();
            glass = null;
        }
        if (backdropTextureId != 0L && imgui != null) {
            imgui.releaseTexture(backdropTextureId);
        }
        backdropTextureId = 0L;
    }

    @Override
    public void shutdown() {
        destroyGlass();
        glassFailed = false;
        if (imgui != null) {
            imgui.shutdown();
            imgui = null;
        }
        if (targetFramebuffer != 0) {
            glDeleteFramebuffers(targetFramebuffer);
            targetFramebuffer = 0;
        }
        attachedTexture = attachedWidth = attachedHeight = 0;
        targetComplete = false;
        frameTarget = null;
    }
}
