# Internals

`dev.technix.mica.internal.*` is the implementation surface. Public by Java
visibility — you can read it, the IDE autocomplete will offer it — but
conceptually private: future versions rewrite internals freely. Use this page when
you are extending Mica (writing a new `MinecraftCompatImpl` for a future Minecraft
release, replacing the compute kernel, or adding a new screen detector).

## Renderer core

`dev.technix.mica.internal.ImGuiRenderer` is the backend-independent core behind
`api.OverlayRenderer`. It owns:

* The ImGui context and the font atlas (`ImGuiFonts`).
* The frame lifecycle: `beginFrame()` (`ImGui.newFrame()`), `recordBackdrop()`,
  `endFrame()` (`ImGui.render()` + hand-off to the backend). An unfinished frame is
  closed before the next one starts, so ImGui's begin/end pairing always holds.
* Backend selection: on the first frame with a GPU device it asks
  `MinecraftCompat.renderBackend()` and builds the matching `RenderBackend` through
  `RenderBackendRegistry` (wired in `internal.backend.RenderBackends`).
* `TextureCache`: the ref-counted registrations behind `MicaTexture`, with deferred
  release and render-thread-only GPU calls.
* Error handling: a start-up failure becomes one `MicaBackendException` (Minecraft
  version, backend, Mica version, reason), logged once; the overlay is disabled for
  the session without throwing into Minecraft.

It never imports Vulkan or OpenGL (`ApiBoundaryTest` checks this).

Lifecycle: initialisation happens lazily on the first `beginFrame()`, not in
`OverlayRenderer.build()`. At `onInitializeClient` time Minecraft has not yet created
its GPU device; eager init would fail. `shutdown()` destroys the backend and the ImGui
context; the next frame starts everything again, and outstanding `MicaTexture`s
re-register.

`ImGuiRenderer.imGuiFrame(int, int, float)` is the standalone `ImGui.newFrame` for
callers that want to drive the frame themselves.

## Backends

`internal.backend.RenderBackend` is the contract between the core and a graphics
API: `initialize`, `beginFrame`, `prepareFrame`, `recordBackdrop`, `render(ImDrawData)`,
host texture lookup and registration, frosted-glass configuration, `shutdown`. See
[`backends.md`](./backends.md) for the full architecture and how to add a backend.

### Vulkan

`VulkanRenderBackend` adapts the existing Vulkan classes:

* `VulkanImGuiBackend` submits Dear ImGui draw data into the host's `VkCommandBuffer`.
  It owns the descriptor pool (one set per registered texture), the font atlas upload
  (recorded into the frame command buffer) and the dynamic-rendering pipeline.
* `FrostedGlassRenderer` owns two half-resolution ping-pong storage images, the
  forward/reverse descriptor sets, the compute pipeline (Kawase 9-tap) and the output
  sampler. `recordBlurPass(cmd, sourceImage, ...)` blits the host's scene image into
  the input target, runs the kernel and transitions the output to
  `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`, which `Draw.backdrop` samples in the
  same frame.
* `VulkanRenderBackend` itself owns the HUD texture samplers (linear + nearest) and
  re-reads the `VulkanContext` every frame.

If `blurScaleDivisor` changes, the resize path reallocates the ping-pong targets.

### OpenGL

`OpenGLRenderBackend` runs on Minecraft's existing GL context (never a new one):

* `OpenGLImGuiBackend` is the draw-data renderer (GLSL 150 core, per-texture sampler
  objects, `glDrawElementsBaseVertex`). It renders into a Mica-owned framebuffer object
  whose attachment is the main render target's colour texture.
* `OpenGLFrostedGlass` downsamples with `glBlitFramebuffer` and runs the same Kawase
  kernel as fragment-shader ping-pong passes.
* `GlStateSnapshot` captures and restores all GL state Mica touches, so Minecraft's
  `GlStateManager` cache stays valid.

`applyStyle(FrostedGlassStyle)` reaches either backend through
`RenderBackend.configureFrostedGlass`, called by `OverlayRenderer.Builder.build()` and
`OverlayRenderer.setGlassStyle(...)`. A blur failure on either backend switches glass
off with one WARN; normal rendering continues.

## Font atlas

`dev.technix.mica.internal.ImGuiFonts` rasterises Mica's bundled SF Pro Display
faces plus every face in every registry passed to
`OverlayRenderer.Builder.withFontRegistry`. Atlas upload is recorded into the
backend once at init (recorded into the frame command buffer on Vulkan, uploaded
synchronously on OpenGL). Font bytes always go through `FontData` (see
[`imgui.md`](./imgui.md#font-handling)).

`load(List<FontRegistry>)` is called from `OverlayRenderer.Builder.build()`; the
internal registry list is mirrored to the atlas so subsequent calls to
`renderer.font(name)` resolve through a single `Map<String, FontFace>`.

`reload()` is platform-internal. Production code should know its full set of
fonts at builder time.

## Screen detector

`dev.technix.mica.internal.ScreenDetector` reads `Minecraft.getInstance().gui.screen()`
once per frame and runs an `instanceof` ladder to translate the Mojang screen to
`MicaScreen`. Future Minecraft releases with new canonical screen types update
this one file.

## Input router

`dev.technix.mica.internal.ImGuiInputRouter` is the receiver for mouse / keyboard
events forwarded by the input mixins. It writes into `ImGui.getIO()` directly;
nothing user-facing lives here beyond the entry points in `ActiveRenderers`.

## Active renderer registry

`dev.technix.mica.internal.ActiveRenderers` is the process-wide singleton bridge
between:

* The input mixins, which call `ActiveRenderers.feedMouseMove(...)` etc.
* The render mixin, which calls `ActiveRenderers.prepareForFrame()` /
  `renderOverlay()`.

It also holds the staging-area references for `FontRegistry` /
`FrostedGlassStyle` so authors can stage them before invoking the builder.

## Compatibility adapter

`dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2` is the only code in the
project that touches Mojang's rendering internals (`com.mojang.blaze3d.*`). It
implements:

* `MinecraftCompat`: backend detection (`renderBackend()`), version, render-thread
  check, sprite lookup.
* `VulkanHostAccess`: `VulkanContext`, the frame `VkCommandBuffer`, image views.
* `OpenGLHostAccess`: the main render target's GL colour texture and size, and texture
  GL names (via `GlTextureNames`, reflective because `GlTexture`'s internals are not
  public API).

A future Minecraft release becomes `MinecraftCompatImpl_v26_3` (or `v27_0`, depending
on the version). It implements the same interfaces and is returned by
`MinecraftCompat.detect()`.

## Mixin accessors

`dev.technix.mica.mixin.client.*` exposes Mojang-private methods and fields via
accessor interfaces. These are necessary because the 26.2 Vulkan adapter needs
to read Mojang-internal state (`getCurrentSceneImage`,
`getCurrentSceneImageLayout`) without conferring public visibility.

## Build pipeline

`dev.technix.mica.mixin.client.GuiRendererMixin` is the entry point that hooks
into `GuiRenderer.render(...)`. It calls `ActiveRenderers.prepareForFrame()`
ahead of host GUI submissions, then `ActiveRenderers.renderOverlay()` after.

## Reading more

* [`backends.md`](./backends.md) — Vulkan + OpenGL architecture, adding a backend.
* [`vulkan.md`](./vulkan.md) — the Vulkan-specific bits of the backend.
* [`api.md`](./api.md) — what the public surface looks like.
