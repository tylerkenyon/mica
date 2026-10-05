# Troubleshooting

Common failures and where to look. Symptoms are organised by what you see at runtime.

## "I see the vanilla game but no Mica overlay"

Mica works on both Vulkan and OpenGL, so the backend itself is no longer a reason
for a missing overlay. Scan the log:

```
grep -i 'mica' latest.log
```

* `Mica detected Minecraft's <Backend> backend; using the <Backend> renderer.` followed
  by `Mica renderer initialised: ...` means the renderer is running. Check that your
  overlay is registered on the `Mica` instance (or an installed `OverlayRenderer`) and
  that its `renderScope()` matches the current screen.
* `Mica could not start its renderer (Minecraft …, rendering backend …, Mica …).
  Reason: …` means start-up failed and the overlay is disabled for the session. The
  reason names the cause, for example:
  * `Mica has no renderer for the Unknown backend` — a third-party rendering backend
    replaced Minecraft's. Mica deliberately does not fall back to another API.
  * `OpenGL 3.2 core is required` — the driver offers too old a context.
  * `Mica cannot read the OpenGL texture name from …` — this Minecraft build changed
    `GlTexture`; the compat adapter needs an update.
* No Mica line at all — the overlay was never installed (`Mica.create()` not called).

To check one backend in a dev environment, use `./gradlew runClient -PmicaBackend=vulkan`
or `-PmicaBackend=opengl`.

## "My HUD shows up but with wrong colours / upside-down textures"

You are using an atlas texture and the V coordinates are wrong. Minecraft 26.x
uploads host textures top-to-bottom; Dear ImGui's V0 = bottom. Mica's internal
blur target mirrors on V automatically, but `Draw.image(context, long, ...)` does
not.

**Fix.** When calling `Draw.image` with raw UVs, lay them out from the assumption
that V = 0 is the bottom row of the texture. If your sprite looks flipped, swap
`v0` and `v1`.

## "My HUD shows up but the `Draw.frostedPanel` backdrop is blank"

Either `withFrostedGlass(false)` was set, or the blur pass has not yet warmed up.
The first frame after window resample can be empty. If it stays empty:

* Verify `frostedGlass(true)` / `withFrostedGlass(true)` on the builder.
* Look for `frosted glass unavailable; panels draw without blur` or `Frosted glass
  failed on <Backend>` in the log. Mica then keeps rendering without the backdrop
  (`RenderContext.hasBlur()` is `false`) instead of disabling the overlay.

## "I added a font and `renderer.font("name")` returns null"

The font file isn't on the classpath. Two common causes:

1. The fonts are in your mod jar but not at `assets/<your-mod-id>/<prefix>/`.
2. The fonts are at the right path but the resource manager is racing ahead of
   `Minecraft.getInstance()` (very early bootstrap).

**Fix.** Confirm the directory inside your mod jar is `assets/yourmod/fonts/` and
the `FontRegistry` constructor takes
`Identifier.fromNamespaceAndPath("yourmod", "fonts")`. If the file just isn't
there, the platform logs a `WARN: Font resource missing:` line.

## "NoSuchFieldError / mixin apply failure / ClassNotFoundException: com.mojang..." at start-up

You are using a Mica jar built for a different Minecraft version. Each jar targets exactly
one version: `+mc26.2` for 26.2, `+mc26.3` for 26.3 (26.3 moved Mojang's rendering classes
to `com.mojang.renderpearl` and switched input to SDL). Use the jar matching your Minecraft
version ([`multiversion.md`](./multiversion.md)).

## "ImGui text fields don't receive typed characters on 26.3"

With SDL, characters only arrive while text input is started. Mica starts it while an ImGui
text field has focus (`MinecraftCompat.setTextInputActive`). If you drive frames yourself
instead of through Mica's mixins, make sure `OverlayRenderer.renderOverlay()` (which ends
the frame) still runs.

## "The game aborts with `free(): invalid size` on exit or after reloading fonts"

That is imgui-java's `addFontFromMemoryTTF(byte[])` letting ImGui `free()` a pointer
into a Java array. Mica's own font loading goes through `internal.FontData`, which
avoids this. If you add fonts to the atlas yourself, use `FontRegistry` or
`FontData.add(atlas, bytes, size)` instead of calling `addFontFromMemoryTTF` directly.

## "My HUD widgets reflow when a font changes mid-line"

This shouldn't happen with Mica because the atlas is shared — all `FontFace`s
rasterise into the same Dear ImGui atlas. If you see reflow:

* You are likely creating a new `ImFont` directly without using
  `FontRegistry.add`. The new font gets its own atlas slot and a layout swap
  happens at the boundary.

**Fix.** Always go through `FontRegistry.add(String, String, float)` so the
font lands in Mica's atlas, not a side atlas.

## "Element renders once and disappears the next frame"

The element threw during `render(...)`. The platform disables the element and
logs the traceback at `ERROR`. Search the log:

```
grep 'Disabling overlay element .* after a failure' latest.log
```

The element name is your `name()` return value. Re-run the framework by reading
the traceback; the most common causes are null dereferences when
`Minecraft.getInstance().player == null` (e.g. on the title screen, before
`renderScope()` — but `IN_GAME_HUD` should catch that anyway).

## "Build fails on a fresh checkout"

Stale `build/` directory from a previous version. `./gradlew clean` is the
recovery. The `runClient` task also keeps a fresh asset cache under
`run/config/` — `rm -rf run` clears it.

## "Debug environment: I want to log noise without a release build"

The build pipeline exposes:

* `-PimguiDebugClear` — clear the blur attachment every frame (proves the pass
  reaches the presented image).
* `-PimguiDebugSkipBlur` — skip the blur pass entirely (proves the issue is the
  blur, not the ImGui drawing).
* `-PimguiDebugSkipDraw` — skip the ImGui drawing entirely (proves the issue is
  the ImGui submission, not upstream plumbing).
* `-PmicaBackend=vulkan|opengl` — force Minecraft's backend for the run (passed as
  `--graphicsBackend`).
* `-PmicaDemoWindow` — add a plain ImGui demo window (text, button, slider, text
  input, texture, frosted panel) for checking either backend by hand.

Useful alone or in combination for bisecting.

## "I want to read the trace of the descriptor-set ring"

`dev.technix.mica.internal.backend.vulkan.FrostedGlassRenderer.BLUR_PASSES` was
made instance-level so `runtime style` swaps take effect. Set a logging trap on
`applyStyle(...)` to inspect what the ring looks like at every change.

## Reading more

* [`backends.md`](./backends.md) — Vulkan vs OpenGL, detection, feature matrix.
* [`multiversion.md`](./multiversion.md) — Minecraft 26.2 vs 26.3 builds.
* [`vulkan.md`](./vulkan.md) — Vulkan-specific gotchas.
* [`imgui.md`](./imgui.md) — imgui-java binding notes.
* [`distribution.md`](./distribution.md) — what the build pipeline actually emits.
