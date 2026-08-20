# Troubleshooting

Common failures and where to look. Symptoms are organised by what you see at runtime.

## "I see the vanilla game but no Mica overlay"

Almost always: an OpenGL fallback. Check the log for a line like:

```
[Render thread/WARN] (Minecraft) Graphics backend forced to vulkan by launch argument...
```

If you see that, Vulkan is forced *correctly* — Mica should be drawing. If you see
Minecraft using OpenGL instead:

```
[Render thread/INFO] (Minecraft) Using graphics backend OpenGL, using drivers: ...
```

then the host config (`options.txt` or launch argument) picked OpenGL.

**Fix.** Run with `--graphicsBackend vulkan` on the launcher command line. In a dev
environment, pom.gradle passes this to `runClient` for you — see
[`distribution.md`](./distribution.md). For a shipped mod, instruct your end-users
to do the same.

A single WARN log line is also emitted by the platform itself when it sees a
non-Vulkan `GpuDevice`:

```
imgui-mc-impl requires Vulkan — host GpuDevice is active as X, not VulkanDevice.
```

If you see this line, the host is on OpenGL.

## "I see a Minecraft render but the toast and HUDs are missing"

Same root cause as above (OpenGL fallback), but a log scan is worthwhile because
the platform might be enabled and silently noop'ing. Verify with:

```
grep -i 'mica\|imgui-mc-impl\|Graphics backend' latest.log
```

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

* Verify `withFrostedGlass(true)` on the builder.
* Verify `--graphicsBackend vulkan` is on the launcher.
* Check the platform didn't log the OpenGL-fallback warning.

## "I added a font and `renderer.font("name")` returns null"

The font file isn't on the classpath. Two common causes:

1. The fonts are in your mod jar but not at `assets/<your-mod-id>/<prefix>/`.
2. The fonts are at the right path but the resource manager is racing ahead of
   `Minecraft.getInstance()` (very early bootstrap).

**Fix.** Confirm the directory inside your mod jar is `assets/yourmod/fonts/` and
the `FontRegistry` constructor takes
`Identifier.fromNamespaceAndPath("yourmod", "fonts")`. If the file just isn't
there, the platform logs a `WARN: Font resource missing:` line.

## "I see Vulkan chosen but Mica's WARN still prints"

The OpenGL-fallback WARN is one-shot; the field `NON_VULKAN_WARNING_LOGGED`
tracks it. If you see it on every run, the platform is seeing a non-`VulkanDevice`
backend — even though Vulkan was the launcher-default, Minecraft may have
gracefully recompiled into a different backend on a particular GPU.

**Fix.** Same as above — `--graphicsBackend vulkan`.

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
* `-Dimgui.allowNonVulkan=true` — run when the host picks OpenGL; demotes the
  WARN to INFO.

Useful alone or in combination for bisecting.

## "I want to read the trace of the descriptor-set ring"

`dev.technix.mica.internal.backend.vulkan.FrostedGlassRenderer.BLUR_PASSES` was
made instance-level so `runtime style` swaps take effect. Set a logging trap on
`applyStyle(...)` to inspect what the ring looks like at every change.

## Reading more

* [`vulkan.md`](./vulkan.md) — Vulkan-specific gotchas.
* [`imgui.md`](./imgui.md) — imgui-java binding notes.
* [`distribution.md`](./distribution.md) — what the build pipeline actually emits.
