# mica — public API

A library that lets you draw Dear ImGui overlays inside Minecraft 26.2's renderer, on
**both** its Vulkan and OpenGL backends, with every Mojang API touchpoint isolated behind
one interface. Nothing here mentions Vulkan or OpenGL; the backend is detected at runtime.

## Quick start

```java
import dev.technix.mica.api.Mica;
import dev.technix.mica.api.OverlayElement;
import dev.technix.mica.api.RenderContext;
import dev.technix.mica.internal.util.Draw;
import dev.technix.mica.internal.ImGuiFonts;
import imgui.ImGui;

public final class MyHud implements OverlayElement {
    @Override public String name() { return "MyHud"; }
    @Override public void render(RenderContext c) {
        Draw.frostedPanel(c, 12, 12, 200, 40, 6);
        Draw.text(c, ImGuiFonts.regular(), 0xFFFFFFFF, "Hello overlay", 24, 24);
    }
}

// In onInitializeClient
Mica mica = Mica.create();
mica.registerOverlay(new MyHud());
mica.registerOverlay(ctx -> {
    ImGui.begin("My Mod");
    ImGui.text("Hello Minecraft!");
    ImGui.end();
});
// Closed automatically on client stop.
```

The bundle's mixins (`GuiRendererMixin`, `MouseHandlerMixin`, `KeyboardHandlerMixin`) hook
Minecraft's render loop and input automatically once `Mica.create()` installed the overlay.

## Public surface

| Type | Purpose |
|------|---------|
| `Mica` | The entrypoint: `Mica.create()`, `registerOverlay(...)`, `texture(...)`, `close()`. |
| `MicaOverlay` | Functional overlay for lambdas: `ctx -> { ... }`. |
| `MicaTexture` | Opaque, backend-independent texture for ImGui (`imGuiTextureId()`, `close()`). |
| `RenderBackendType` | `VULKAN` / `OPENGL` / `UNKNOWN`, for diagnostics only. |
| `MicaBackendException` | Why a renderer could not start (Minecraft version, backend, Mica version, reason). |
| `OverlayRenderer` | The render loop behind `Mica`. Build with `OverlayRenderer.builder()`, call `prepareForFrame()` / `renderOverlay()` from your own driver if you replace the bundled mixins. |
| `MinecraftCompat` | The version-shaped bridge. One implementation per Minecraft version (`api/compat/v26_2/MinecraftCompatImpl_26_2`). Detects the rendering backend, hands Mica's internal renderers their Vulkan/OpenGL host access, and looks up atlas sprites. |
| `OverlayElement` | Author-side interface. Implement `name`, `isVisible`, `render(context)`. |
| `RenderContext` | Per-frame draw list, framebuffer size, blur texture ID, elapsed time. |
| `Palette` | Shared colour tokens (`PANEL`, `ACCENT`, `TEXT`, ...). |
| `SpriteBounds` | Normalised UV rect inside a host atlas. |
| `TextureHandle` | Per-frame snapshot of an atlas registration (`registerAtlasTexture`); a `MicaTexture`. |
| `TextureFilter` | `LINEAR` (default) or `NEAREST` (HUD pixel art). |
| `VanillaAtlases` | `ITEMS`, `BLOCKS`, `DEFAULT_SKIN` identifiers. |

## Drawing helpers

`dev.technix.mica.internal.util.Draw` provides:

- `frostedPanel(context, x, y, w, h, rounding)` — blur + tint + rim, with fallback when the
  blur is not ready yet.
- `roundedRect`, `roundedRectOutline`, `verticalDivider`, `progressBar`.
- `image(context, handle, x, y, w, h)` and a UV-bearing overload for arbitrary sub-regions.
- `text`, `textVCentered`, `textCentered`, `textWidth`, `textHeight`.

## What is NOT public

Anything under `dev.technix.mica.internal` is private to the platform and may change without
notice. Application code only imports from `dev.technix.mica.api` (and `internal.util.Draw`,
which is treated as a public helper).

## Forward compatibility

When a new Minecraft release changes a Mojang-internal class name or signature, only the
matching `api/compat/vN/MinecraftCompatImpl_N` adapter needs to change. The rest of the
platform — including user overlay elements — is untouched. To support a new version, copy
the existing adapter, fix the broken imports, and return it from `MinecraftCompat.detect()`.
A new rendering backend is a new `internal/backend/<name>` package; see
`docs/mica/backends.md`.