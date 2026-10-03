package dev.technix.mica.api;

import dev.technix.mica.internal.ImGuiFonts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Public facade over the overlay's font atlas — the public way to push faces on
 * the overlay draw list.
 *
 * <p>Push a {@link FontFace} before drawing text on the current ImGui draw list
 * and pop afterwards; {@link Draw}'s text helpers do this for you. The bundled
 * faces ({@link #regular()}, {@link #medium()}, {@link #bold()}, {@link #logo()})
 * are available once the overlay renderer has built its atlas, and any face
 * registered through {@link FontRegistry} is reachable by name via
 * {@link #byName(String)}.</p>
 */
public final class MicaFonts {

    private MicaFonts() {
    }


    /**
     * Pushes the font for {@code face} onto the current ImGui font stack.
     *
     * @return whether a font was actually pushed (false for null faces or faces
     *         without a rasterised {@code ImFont}); pass the result to
     *         {@link #pop(boolean)}.
     */
    public static boolean push(@Nullable FontFace face) {
        return ImGuiFonts.push(face);
    }


    /**
     * Pops the previously pushed font; no-op when {@code pushed} is false.
     */
    public static void pop(boolean pushed) {
        ImGuiFonts.pop(pushed);
    }


    /**
     * Looks up a face by registry name (e.g. {@code "body-lg"}), or the bundled
     * names {@code "regular"}/{@code "medium"}/{@code "bold"}/{@code "logo"}.
     *
     * @return the face, or null when no face with that name exists.
     */
    @Nullable
    public static FontFace byName(@NotNull String name) {
        return ImGuiFonts.byName(name);
    }


    /**
     * The bundled 13px regular face.
     */
    @NotNull
    public static FontFace regular() {
        return ImGuiFonts.regular();
    }


    /**
     * The bundled 13px medium face.
     */
    @NotNull
    public static FontFace medium() {
        return ImGuiFonts.medium();
    }


    /**
     * The bundled 13px bold face.
     */
    @NotNull
    public static FontFace bold() {
        return ImGuiFonts.bold();
    }


    /**
     * The bundled 18px logo face.
     */
    @NotNull
    public static FontFace logo() {
        return ImGuiFonts.logo();
    }
}
