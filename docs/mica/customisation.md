# Custom fonts & frosted glass

Mica bundles no font. Its four default faces (`regular`, `medium`, `bold`, `logo`) are
rasterised from **the operating system's own UI font**, so the overlay looks native on
each platform and the jar carries no font licence with it. That plus a half-resolution
Kawase blur at five passes per frame is what you get out of the box; both are
configurable end-to-end.

The two knobs are independent. Pick neither, and you get Mica's defaults. Pick fonts,
and your registry's faces coexist with the platform ones. Pick a glass style, and every
panel drawn with the parameterless `Draw.frostedPanel` re-skins in lockstep.

### The default faces, and replacing them

| Platform | Regular | Medium | Bold |
| --- | --- | --- | --- |
| Windows | `%WINDIR%\Fonts\segoeui.ttf` | `segoeuisb.ttf` (semibold - Windows ships no medium Segoe) | `segoeuib.ttf` |
| macOS | `/System/Library/Fonts/SFNS.ttf` | same | `Supplemental/Arial Bold.ttf`, else SFNS |
| Linux | `fc-match sans-serif`, else DejaVu / Noto / Liberation | `:weight=medium` | `:bold` |

To override a default face, drop a file in `<gamedir>/mica/fonts` under the exact name
for that weight - `system-ui.ttf`, `system-ui-medium.ttf`, `system-ui-bold.ttf`. One
fixed name per weight rather than a search across extensions, so there is one answer to
"where does my font go".

Two limits worth knowing before you trust a weight. ImGui rasterises with stb_truetype,
which applies no variable-font weight axis, so macOS's `SFNS.ttf` renders at regular
weight whatever is asked of it. And a `.ttc` collection needs a face index that
`addFontFromMemoryTTF` does not accept, so collections are only ever a last resort in the
candidate lists. Where a platform has no distinct bold or medium file, that weight
resolves to regular rather than failing - you still get text.

## Custom fonts

A `FontRegistry` reads `*.otf` / `*.ttf` files from a Minecraft resource location you
control. Pick any directory under your own mod id; ship the font files inside your
mod jar at the same path; pass the location to a new `FontRegistry`; register each
face with `add`. Then hand the registry to the builder.

```java
Identifier root = Identifier.fromNamespaceAndPath("mymod", "fonts");
FontRegistry myFonts = new FontRegistry(root);
myFonts.add("heading", "heading-bold.otf",   22f);
myFonts.add("body",    "body-regular.otf",  14f);
myFonts.add("mono",    "roboto-mono.otf",   12f);

OverlayRenderer renderer = OverlayRenderer.builder()
        .withMinecraftCompat(new MinecraftCompatImpl_26_2())
        .withFrostedGlass(true)
        .withFontRegistry(myFonts)
        .build();
```

The expected directory structure inside your mod jar is:

```
assets/mymod/fonts/heading-bold.otf
assets/mymod/fonts/body-regular.otf
assets/mymod/fonts/roboto-mono.otf
```

### Resolution order

A registry face resolves through Minecraft's resource manager first, then the
classloader, then `<gamedir>/mica/fonts`. The resource manager is what reads jars on the
classpath via Fabric Loader; the classloader covers the very early bootstrap before it is
ready; the directory is where a user's own files live.

Mica's four **default** faces do not use that chain - they are not resource-located at
all. They resolve override-then-platform: the fixed `system-ui*.ttf` name in
`<gamedir>/mica/fonts` if present, otherwise the OS font above.

### Looking up a face anywhere

Once the renderer is built, every face — default or user-registered — is reachable
by name through `renderer.font(name)`. The `FontFace` it returns has the rasterised
`ImFont` handle and the pixel size, both ready for `Draw.text(...)`.

```java
FontFace heading = renderer.font("heading");
FontFace body = renderer.font("body");
FontFace boldFallback = renderer.font(FontFace.BOLD);
```

If a name was never registered, `renderer.font(name)` returns `null`. Use `null`-aware
draw helpers (`Draw` already routes `null` through `ImGuiFonts.push`'s fallback).

### Builder order

Registries added via `withFontRegistry(...)` and `withFontRegistries(...)` are
merged in insertion order. Within a registry, faces are registered in `add()` order.
Mica's default platform faces are added last, so they keep their canonical names
(`regular`, `medium`, `bold`, `logo`) even if your registry also exposes a `regular`
face. Your face wins for that name.

You can chain as many registries as you like:

```java
OverlayRenderer renderer = OverlayRenderer.builder()
        .withFontRegistry(myModFonts)
        .withFontRegistry(otherModFonts)            // optional
        .withFontRegistries(List.of(brand, debug))
        .build();
```

### Pixel sizes

imgui-java 1.92 takes integer pixel sizes; `add(name, fileName, sizePixels)` rounds
your float. A 13 px body font and a 22 px heading font occupy separate atlas slots;
metrics are independent.

### Live reloading

`ImGuiFonts.reload()` re-rasterises every registered font into the atlas and uploads
once on the next ImGui frame. The call is platform-internal (used by editor tooling
that swaps fonts on hot reload). Production user code should make the set of fonts
known at builder time.

## Custom frosted-glass style

`FrostedGlassStyle` is a record with five fields, built through
`FrostedGlassStyle.builder()`:

| Field               | Effect                                                              |
| ------------------- | ------------------------------------------------------------------- |
| `blurScaleDivisor` (≥2) | Screen downsampling factor. `2` is half-resolution, `3` is third-res. Bigger = blurrier corners, cheaper to compute. |
| `blurPasses` (1–16) | Kawase passes per frame. More passes = longer bleed.                |
| `defaultTint`       | RGBA tint applied over the blurred backdrop of every parameterless `Draw.frostedPanel`. |
| `defaultBorder`     | RGBA hairline rim. `0` suppresses the rim entirely.                 |
| `defaultRounding`   | Corner radius for the parameterless overlay.                       |

```java
FrostedGlassStyle sleek = FrostedGlassStyle.builder()
        .blurScaleDivisor(3)
        .blurPasses(7)
        .defaultTint(ImColor.rgba(38, 42, 50, 235))
        .defaultBorder(ImColor.rgba(255, 255, 255, 32))
        .defaultRounding(14f)
        .build();

OverlayRenderer renderer = OverlayRenderer.builder()
        .withMinecraftCompat(new MinecraftCompatImpl_26_2())
        .withFrostedGlass(true)
        .withFrostedGlassStyle(sleek)
        .build();
```

### Resolution at draw time

The parameterless `Draw.frostedPanel(context, x, y, w, h)` pulls `tint`, `border`,
and `rounding` from `context.glassStyle()`. Per-panel overrides still work: pass the
explicit `(context, x, y, w, h, rounding, tint, border)` overload to break style
for one panel.

```java
@Override
public void render(RenderContext context) {
    Draw.frostedPanel(context, x, y, w, h);

    Draw.frostedPanel(context, x2, y2, w2, h2, 8f,
            ImColor.rgba(220, 60, 60, 235),
            ImColor.rgba(255, 60, 60, 32));
}
```

### Live re-skinning

`renderer.setGlassStyle(newStyle)` swaps the style on the renderer. The pass count
takes effect on the next blur pass; the divisor takes effect when `checkResize` runs
(it forces a re-allocation of both ping-pong blur targets so the new dimensions
sample at the new resolution). `RenderContext.glassStyle()` returns the new style
from the next frame onward.

```java
debugKeyPressed = ...;
if (debugKeyPressed) {
    renderer.setGlassStyle(renderer.glassStyle() == FrostedGlassStyle.DEFAULT
            ? FrostedGlassStyle.builder()
                    .blurPasses(2)
                    .defaultTint(ImColor.rgba(0, 0, 0, 200))
                    .build()
            : FrostedGlassStyle.DEFAULT);
}
```

### Why a record + builder

The visual configuration is small enough that a value-object record fits in a single
page of API reference, but the user almost always wants to override one or two
fields rather than hand-build the whole struct. The builder preloads
`FrostedGlassStyle.DEFAULT` so overrides are a one- or two-liner change, and
validates the divisor and pass count on `build()` so a typo throws at mod init
rather than producing a render bug that only manifests in a screenshot review.

## What you cannot change

A handful of things in the render pipeline are intentionally not part of either API:

* The compute kernel (Kawase, with the 9-tap weights chosen for hover-fidelity over
  performance). Replacing the kernel requires a new `FrostedGlassRenderer` subclass
  and a Vulkan shader rebuild pipeline; out of scope for the user-facing API today.
* The descriptor-set ring size (`descriptorSetForward` + `descriptorSetReverse`
  ping-pong). The choice is per-frame re-entrancy; raising it is a memory trade-off.
* The colour sampling filter (`VK_FILTER_LINEAR`). The host's scene image is sampled
  linearly; changing this affects only the crispness of the backdrop, not
  user-visible behaviour.

These are deliberate. The platform keeps the moving parts you cannot change small
so the parts you can change — fonts, glass style, the user-level draw primitives —
stay stable.

## Reading more

* [`api.md`](./api.md) — symbol-by-symbol reference for `FontRegistry`, `FontFace`,
  `FrostedGlassStyle`, `RenderContext.glassStyle()`, the renderer builder, and
  `setGlassStyle`.
* [`vulkan.md`](./vulkan.md) — why the divisor forces a target re-allocate.
* [`distribution.md`](./distribution.md) — the slim jar still contains the public
  types unchanged; consumer modders don't have to do anything special to receive
  the new APIs.
