package dev.technix.mica.api.compat.v26_2;

import com.mojang.blaze3d.textures.GpuTexture;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToIntFunction;


/**
 * Reads the OpenGL texture name out of Minecraft's {@code com.mojang.blaze3d.opengl.GlTexture}.
 *
 * <p>Since 26.1 Mojang's OpenGL backend classes are partly package-private, and they are
 * reached reflectively (Minecraft 26.x ships unobfuscated, so the names are stable) instead
 * of through a mixin accessor: a missing accessor target would fail mixin application and
 * take the Vulkan path down with it, while a failed lookup here only disables the OpenGL
 * renderer, with a log line saying why.
 */
final class GlTextureNames {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    private static final String GL_PACKAGE = "com.mojang.blaze3d.opengl.";

    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private static final ClassValue<ToIntFunction<Object>> READERS = new ClassValue<>() {
        @Override
        protected ToIntFunction<Object> computeValue(Class<?> type) {
            return createReader(type);
        }
    };

    private GlTextureNames() {
    }

    /** The GL texture name, or {@code 0} if {@code texture} is not an OpenGL texture. */
    static int of(@Nullable GpuTexture texture) {
        if (texture == null || !texture.getClass().getName().startsWith(GL_PACKAGE)) {
            return 0;
        }
        try {
            return READERS.get(texture.getClass()).applyAsInt(texture);
        } catch (RuntimeException exception) {
            logFailure(texture.getClass(), exception);
            return 0;
        }
    }

    private static ToIntFunction<Object> createReader(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod("glId");
                if (method.getReturnType() == int.class) {
                    method.setAccessible(true);
                    return texture -> invoke(method, texture);
                }
            } catch (NoSuchMethodException ignored) {
                // Try the superclass, then the field.
            }
        }
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField("id");
                if (field.getType() == int.class) {
                    field.setAccessible(true);
                    return texture -> read(field, texture);
                }
            } catch (NoSuchFieldException ignored) {
                // Keep walking up.
            }
        }
        logFailure(type, null);
        return texture -> 0;
    }

    private static int invoke(Method method, Object texture) {
        try {
            return (int) method.invoke(texture);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int read(Field field, Object texture) {
        try {
            return field.getInt(texture);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void logFailure(Class<?> type, @Nullable Throwable cause) {
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            LOGGER.error("Mica cannot read the OpenGL texture name from {} (no glId() method or "
                    + "id field); the OpenGL overlay cannot draw on this Minecraft build.",
                    type.getName(), cause);
        }
    }
}
