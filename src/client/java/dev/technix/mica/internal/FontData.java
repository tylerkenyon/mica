package dev.technix.mica.internal;

import imgui.ImFont;
import imgui.ImFontAtlas;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.zip.Adler32;


/**
 * Hands TTF/OTF bytes to Dear ImGui so that ImGui owns a native copy.
 *
 * <p>imgui-java's {@code addFontFromMemoryTTF(byte[])} passes a pointer into the Java array
 * and, by default, lets the atlas {@code free()} it on destruction, which aborts the JVM
 * ({@code free(): invalid size}) on {@code ImGui.destroyContext()} or {@code ImFontAtlas.clear()}.
 * With {@code FontDataOwnedByAtlas=false} ImGui keeps reading the Java array after the JNI
 * call returned, which is just as unsafe. {@code addFontFromMemoryCompressedTTF} is the one
 * entry point where ImGui allocates and owns the font buffer itself, so the data is wrapped
 * in an uncompressed (literal-only) {@code stb_compress} stream and loaded through it.
 */
public final class FontData {

    private static final int MAX_LITERAL = 0x10000;

    private FontData() {
    }

    @NotNull
    public static ImFont add(@NotNull ImFontAtlas atlas, @NotNull byte[] ttf, float sizePixels) {
        return atlas.addFontFromMemoryCompressedTTF(stbStored(ttf), sizePixels);
    }

    /** {@code ttf} as an {@code stb_decompress}-compatible stream made only of literal runs. */
    @NotNull
    static byte[] stbStored(@NotNull byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + data.length / MAX_LITERAL * 3 + 32);
        writeInt(out, 0x57BC0000);
        writeInt(out, 0);
        writeInt(out, data.length);
        writeInt(out, 0);
        for (int offset = 0; offset < data.length; offset += MAX_LITERAL) {
            int length = Math.min(MAX_LITERAL, data.length - offset);
            out.write(0x07);
            out.write((length - 1) >>> 8);
            out.write((length - 1) & 0xFF);
            out.write(data, offset, length);
        }
        out.write(0x05);
        out.write(0xFA);
        Adler32 adler = new Adler32();
        adler.update(data);
        writeInt(out, (int) adler.getValue());
        return out.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value >>> 24);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
