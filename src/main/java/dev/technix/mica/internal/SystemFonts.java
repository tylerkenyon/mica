package dev.technix.mica.internal;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Locates the operating system's UI font on disk, so Mica can ship without bundling one.
 *
 * <p>Nothing is redistributed and there is no licence surface: the files read here are the ones the
 * platform already installed for its own interface. That is the whole reason this class exists -
 * the alternative was shipping SF Pro Display inside the jar, which is Apple-licensed and cannot be
 * redistributed under an open-source licence.
 *
 * <p>Separate from {@link FontLoader} on purpose. That class resolves {@code Identifier}-keyed
 * resources through Minecraft's resource manager and the classpath; this one deals in absolute
 * platform paths and, on Linux, spawns a process. Different questions, and keeping them apart stops
 * a resource lookup from ever being able to block on {@code fc-match}.
 *
 * <h2>Two limits worth knowing before trusting a weight</h2>
 *
 * <ul>
 *   <li><b>Variable fonts rasterise at their default instance only.</b> ImGui's rasteriser is
 *       stb_truetype, which applies no {@code wght} axis, so macOS's {@code SFNS.ttf} yields regular
 *       weight whatever is asked of it. Where a platform has no separate bold file, a bold request
 *       resolves to the regular one rather than failing - the caller still gets text.</li>
 *   <li><b>TrueType collections are avoided.</b> A {@code .ttc} holds several faces and needs an
 *       index to pick one; {@code ImFontAtlas.addFontFromMemoryTTF} takes no index, so feeding it a
 *       collection gets whichever face sits first, if it parses at all. The candidate lists below
 *       prefer plain {@code .ttf}/{@code .otf} and only fall back to a collection as a last resort.</li>
 * </ul>
 */
public final class SystemFonts {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

    /** How long {@code fc-match} gets before it is abandoned. Font setup is on the render thread. */
    private static final long FC_MATCH_TIMEOUT_SECONDS = 1L;

    /** The weights Mica asks the platform for. Not every platform has a distinct file for each. */
    public enum Weight {
        REGULAR("sans-serif"),
        MEDIUM("sans-serif:weight=medium"),
        BOLD("sans-serif:bold");

        /** The fontconfig pattern for this weight, used on Linux. */
        private final String fontconfigPattern;

        Weight(String fontconfigPattern) {
            this.fontconfigPattern = fontconfigPattern;
        }
    }

    private SystemFonts() {
    }

    /**
     * Reads the platform UI font for {@code weight}, or {@code null} when none can be found.
     *
     * <p>{@code null} is a normal outcome on a stripped-down container with no fonts installed, and
     * the caller is expected to degrade to no text rather than treat it as fatal.
     */
    public static @Nullable byte[] read(@NotNull Weight weight) {
        Path path = locate(weight);
        if (path == null) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            LOGGER.warn("Could not read system font {}", path, exception);
            return null;
        }
    }

    /** The file the platform UI font for {@code weight} lives in, or {@code null}. */
    public static @Nullable Path locate(@NotNull Weight weight) {
        for (Path candidate : candidates(weight)) {
            if (candidate != null && Files.isReadable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Candidate files for {@code weight}, best first.
     *
     * <p>Each list ends with the regular face, so a platform without a distinct bold or medium file
     * degrades to regular instead of to nothing.
     */
    private static List<Path> candidates(Weight weight) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return windows(weight);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return macos(weight);
        }
        return linux(weight);
    }

    /**
     * Segoe UI, which has real per-weight files - the only platform here that does.
     *
     * <p>{@code segoeuisb.ttf} is semibold; Windows ships no "medium" Segoe, and semibold is the
     * nearer of the two neighbours. The directory comes from {@code WINDIR} rather than a hardcoded
     * {@code C:\Windows} so a non-standard install still resolves.
     */
    private static List<Path> windows(Weight weight) {
        String windir = System.getenv("WINDIR");
        // Built from segments rather than a literal path so there is no separator to escape and
        // no assumption about which one this JVM uses.
        Path fonts = windir == null || windir.isBlank()
                ? Path.of("C:", "Windows", "Fonts")
                : Path.of(windir, "Fonts");
        List<Path> list = new ArrayList<>(3);
        switch (weight) {
            case BOLD -> list.add(fonts.resolve("segoeuib.ttf"));
            case MEDIUM -> list.add(fonts.resolve("segoeuisb.ttf"));
            case REGULAR -> { }
        }
        list.add(fonts.resolve("segoeui.ttf"));
        list.add(fonts.resolve("tahoma.ttf"));
        return list;
    }

    /**
     * San Francisco, then Arial.
     *
     * <p>{@code SFNS.ttf} is a variable font, so every weight rasterises at regular - see the class
     * javadoc. Arial's separate bold file is therefore listed ahead of it for a bold request: a real
     * bold face beats a nominal one. {@code Helvetica.ttc} is last because it is a collection.
     */
    private static List<Path> macos(Weight weight) {
        Path system = Path.of("/System/Library/Fonts");
        Path supplemental = system.resolve("Supplemental");
        List<Path> list = new ArrayList<>(4);
        if (weight == Weight.BOLD) {
            list.add(supplemental.resolve("Arial Bold.ttf"));
        }
        list.add(system.resolve("SFNS.ttf"));
        list.add(supplemental.resolve("Arial.ttf"));
        list.add(supplemental.resolve("Helvetica.ttc"));
        return list;
    }

    /**
     * fontconfig first, since it is the only authority on what a Linux box actually has, then the
     * paths the common distributions use.
     */
    private static List<Path> linux(Weight weight) {
        List<Path> list = new ArrayList<>(5);
        Path matched = fcMatch(weight.fontconfigPattern);
        if (matched != null) {
            list.add(matched);
        }
        for (String dir : new String[]{"/usr/share/fonts/truetype/dejavu", "/usr/share/fonts/dejavu",
                "/usr/share/fonts/truetype/noto", "/usr/share/fonts/noto",
                "/usr/share/fonts/truetype/liberation", "/usr/share/fonts/liberation"}) {
            Path base = Path.of(dir);
            if (weight == Weight.BOLD) {
                list.add(base.resolve("DejaVuSans-Bold.ttf"));
                list.add(base.resolve("NotoSans-Bold.ttf"));
                list.add(base.resolve("LiberationSans-Bold.ttf"));
            }
            list.add(base.resolve("DejaVuSans.ttf"));
            list.add(base.resolve("NotoSans-Regular.ttf"));
            list.add(base.resolve("LiberationSans-Regular.ttf"));
        }
        return list;
    }

    /**
     * Asks fontconfig for the file behind a pattern.
     *
     * <p>Bounded and failure-tolerant by design: no {@code fc-match} on the box, a container without
     * fontconfig, or a hung call all fall through to the static path list rather than stalling the
     * render thread. The process is destroyed on timeout rather than left behind.
     */
    private static @Nullable Path fcMatch(String pattern) {
        Process process = null;
        try {
            process = new ProcessBuilder("fc-match", "-f", "%{file}", pattern)
                    .redirectErrorStream(false)
                    .start();
            if (!process.waitFor(FC_MATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            String out = new String(process.getInputStream().readAllBytes()).trim();
            return out.isEmpty() ? null : Path.of(out);
        } catch (IOException | InterruptedException | RuntimeException exception) {
            if (process != null) {
                process.destroyForcibly();
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
