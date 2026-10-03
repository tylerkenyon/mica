package dev.technix.mica.api;

import imgui.ImFont;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public record FontFace(@NotNull String name, int pixelSize, @Nullable ImFont imFont) {


    public static final String REGULAR = "regular";

    public static final String MEDIUM = "medium";

    public static final String BOLD = "bold";

    public static final String LOGO = "logo";
}
