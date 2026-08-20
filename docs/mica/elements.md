# Authoring `OverlayElement`s

An `OverlayElement` is the smallest unit you can register with Mica. This page walks
through the lifecycle of an element from registration to per-frame draw, the four
knobs every element exposes, and a full worked example.

## Minimal element

```java
public final class MyHudElement implements OverlayElement {

    @Override
    public String name() {
        return "MyHud";
    }

    @Override
    public MicaScreen renderScope() {
        return MicaScreen.IN_GAME_HUD;
    }

    @Override
    public void render(RenderContext context) {
        Draw.text(context, /* face */ null,
                  Palette.TEXT, "Hello, Mica!", 32f, 32f);
    }
}
```

When `renderFace` is `null`, `Draw.text` falls back to the platform default; if no
font has been registered yet it uses Dear ImGui's built-in default font.

Register it with:

```java
OverlayRenderer renderer = OverlayRenderer.builder()
        .withMinecraftCompat(new MinecraftCompatImpl_26_2())
        .build();

renderer.registerElement(new MyHudElement());
ActiveRenderers.set(renderer);
```

## Per-frame lifecycle

Every frame on the render thread, the platform does this:

1. Read `Minecraft.getInstance().gui.screen()` and translate it to a `MicaScreen`.
2. For each registered element, in registration order:
   * Skip if the element is **failed** — a previous `render` threw, the platform
     disabled it.
   * Skip if `renderScope() != MicaScreen.ANY && renderScope() != currentScreen`.
   * If `isVisible(context)`, call `render(context)`.
3. Submit everything into the host's `VkCommandBuffer`.

Back-to-front is registration order: register background HUDs first, foreground
notifications last.

## The four knobs

### `name()`

A stable identifier printed in logs. Keep it short, kebab-case or PascalCase, no
slashes. The platform reads this only for diagnostic logs today; future inspector
GUIs may key on it.

### `isVisible(RenderContext)`

Per-frame "should I draw" gate. Returning `false` short-circuits and skips
`render()`. Default `true`. Override this for "draw only when a target entity is in
range", "draw only when the data feed is healthy", "draw only on screen-id X", etc.
It's cheaper and clearer than drawing nothing.

### `renderScope()`

Which `MicaScreen` value the element is meaningful on. Default `MicaScreen.ANY`
(draws on every screen). Override to scope down:

| Constant        | When it's right                                                   |
| --------------- | ----------------------------------------------------------------- |
| `IN_GAME_HUD`   | Traditional HUDs that should not appear on the title screen or in the pause menu. |
| `TITLE`         | Brand logos, world-creation helpers, anything that's a separate page. |
| `PAUSE`         | Pause-menu exclusive widgets.                                     |
| `INVENTORY`     | Inventory extensions, container previews.                         |
| `CHAT`          | Chat-overlay tweakers.                                             |
| `OTHER`         | Rarely used; matches anything the detector doesn't classify.     |

The detector runs `instanceof` checks against Minecraft's canonical screen classes;
anything not matched is `OTHER`. See [`contexts.md`](./contexts.md) for the
mechanism.

### `render(RenderContext)`

The actual draw. Read framebuffer dimensions off `RenderContext`, sample the blur
texture ID if you want a frosted backdrop, draw with `Draw.*`. Do **not** hold onto
the `RenderContext` between frames — it is a per-frame record.

## What to draw with

The platform ships a flat namespace of `Draw.*` primitives:

| Primitive                          | What it draws                                             |
| ---------------------------------- | --------------------------------------------------------- |
| `Draw.frostedPanel(ctx, x, y, w, h)` | Glass panel with the user's installed `FrostedGlassStyle`. |
| `Draw.frostedPanel(ctx, x, y, w, h, rounding)` | Glass panel with explicit rounding.        |
| `Draw.frostedPanel(ctx, x, y, w, h, rounding, tint, border)` | Full-control glass. |
| `Draw.backdrop(ctx, ...)`          | Just the blurred screen region, when the blur is ready.   |
| `Draw.roundedRect(ctx, ...)`       | A rounded solid rect.                                     |
| `Draw.roundedRectOutline(ctx, ...)` | A rounded rect outline (no fill).                         |
| `Draw.verticalDivider(ctx, ...)`   | A 1px vertical line.                                      |
| `Draw.progressBar(ctx, ...)`       | A horizontal progress track with a rounded fill.          |
| `Draw.image(ctx, handle, ...)`     | A registered texture.                                      |
| `Draw.image(ctx, textureId, ...)`  | A raw ImGui texture ID with explicit UVs.                 |
| `Draw.text(ctx, face, color, ...)` | Text with a registered font face.                         |
| `Draw.textCentered(ctx, ...)`      | Centred text.                                              |
| `Draw.textVCentered(ctx, ...)`     | V-centred text.                                           |
| `Draw.textWidth(face, s)`          | Measure text width with a font face.                       |
| `Draw.textHeight(face)`            | Measure text height (== face pixel size).                  |

Most authors use `Draw.frostedPanel + Draw.text + Draw.image`. The other primitives
exist for the cases where you need to paint something the platform does not bundle.

## Worked example: a toast element

The bundled `ToastElement` in the slim jar is the canonical worked example. A
scaled-down stand-in:

```java
public final class SimpleToastElement implements OverlayElement {

    private final List<Toast> active = new ArrayList<>();
    private float t;

    @Override
    public String name() { return "SimpleToast"; }

    @Override
    public MicaScreen renderScope() { return MicaScreen.ANY; }

    @Override
    public boolean isVisible(RenderContext context) { return !active.isEmpty(); }

    @Override
    public void render(RenderContext context) {
        t += context.deltaTime();
        float w = 240f, h = 50f;
        float margin = 20f;
        for (int idx = active.size() - 1; idx >= 0; idx--) {
            Toast toast = active.get(idx);
            float y = context.height() - margin - h - idx * (h + 6f);
            float x = Math.min(0f, margin - (1f - toast.visible()) * (w + 10f));
            Draw.frostedPanel(context, x, y, w, h);
            Draw.text(context, /* face */ null, Palette.TEXT, toast.body, x + 16f, y + 16f);
            if (toast.visible() <= 0f) active.remove(idx);
        }
    }

    public void enqueue(String body) { active.add(new Toast(body, 4f)); }

    private record Toast(String body, float total) {
        float visible() { return /* pump t */ 1f; }
    }
}
```

The `ToastElement` shipped in the jar does more — frosted glass, animated slide-in,
bottom progress bar scissored to the panel interior, dev-only enabled flag,
`Minecraft.getWindow()`-derived screen-edge clamping — but the skeleton above is
where to start.

## Throwing in `render()`

If `render` throws, the platform disables that element for the rest of the session
(logs at `ERROR`), so a one-frame bug in your overlay cannot crash the rest of the
HUDs. Wrap non-critical draw calls in try/catch, but you don't need to wrap *every*
primitive — the safety net is there if you want it.

## Common pitfalls

| Pitfall                                                        | Symptom                              | Fix                                                              |
| -------------------------------------------------------------- | ------------------------------------ | ---------------------------------------------------------------- |
| Returning `null` from `Minecraft.getInstance()` is not handled | `NullPointerException` on first frame | Guard your `render()` early-return on the absence of player/screen. |
| Forgetting `ActiveRenderers.set(renderer)`                    | Renderer built but no per-frame draws visible. | Set it from your client entrypoint. |
| Using `Draw.frostedPanel` in the OpenGL fallback               | Backdrop is blank.                   | Set `--graphicsBackend vulkan` on the launcher command line.      |
| Holding onto a `RenderContext` past one frame                  | Hard-to-diagnose visual artifacts.   | Read `RenderContext` once per `render()` call.                    |

## Reading more

* [`api.md`](./api.md) — `OverlayElement` symbol reference.
* [`contexts.md`](./contexts.md) — `MicaScreen` enum reference.
* [`customisation.md`](./customisation.md) — `FontRegistry` + `FrostedGlassStyle`.
