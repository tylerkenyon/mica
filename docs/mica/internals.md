# Internals

`dev.technix.mica.internal.*` is the implementation surface. Public by Java
visibility — you can read it, the IDE autocomplete will offer it — but
conceptually private: future versions rewrite internals freely. Use this page when
you are extending Mica (writing a new `MinecraftCompatImpl` for a future Minecraft
release, replacing the compute kernel, or adding a new screen detector).

## Renderer

`dev.technix.mica.internal.ImGuiRenderer` is the renderer behind
`api.OverlayRenderer`. It owns:

* The ImGui context.
* The Vulkan backend binding.
* The active `MinecraftCompat`.
* The optional `FrostedGlassRenderer` attachment.
* The per-frame RenderTarget tracking.

Lifecycle: `init` happens lazily on the first call to `prepareForFrame`, not in
`OverlayRenderer.build()`. At `onInitializeClient` time Minecraft has not yet
created its Vulkan device; eager init would throw.

`ImGuiRenderer.imGuiFrame(int, int, float)` is the standalone `ImGui.newFrame`
for callers that want to drive the frame themselves. The default path uses
`beginFrame()` / `endFrame()` inside `OverlayRenderer.renderOverlay()`.

## Vulkan backend

`dev.technix.mica.internal.backend.vulkan.VulkanImGuiBackend` is the bridge that
submits Dear ImGui draw data into the host's `VkCommandBuffer`. It owns:

* The descriptor pool sized for one descriptor set per registered texture.
* The ImGui texture IDs registered through `OverlayRenderer.registerAtlasTexture`.
* The font atlas upload (rasterised once, uploaded once).
* The pipeline + pipeline layout for the ImGui render pass.

It does NOT own blur resources — those belong to `FrostedGlassRenderer`.

## Frosted glass

`dev.technix.mica.internal.backend.vulkan.FrostedGlassRenderer` owns:

* Two half-resolution ping-pong storage images.
* The descriptor-set ring (forward + reverse).
* The compute pipeline (Kawase 9-tap).
* The sampler for the output target.

`recordBlurPass(cmd, sourceImage, ...)` is called once per frame from
`OverlayRenderer.renderOverlay` when the frosted glass is enabled. Its job: blit
the host's scene image into the input target, run the compute kernel, transition
the output to `VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL`. The result is what
`Draw.backdrop` samples from in the same frame.

`applyStyle(FrostedGlassStyle)` is called by:
- `OverlayRenderer.Builder.build()` at construction.
- `OverlayRenderer.setGlassStyle(...)` at runtime.

If `blurScaleDivisor` changes between calls, the resize path reallocates the
ping-pong targets so the new divisor takes effect.

## Font atlas

`dev.technix.mica.internal.ImGuiFonts` rasterises Mica's bundled SF Pro Display
faces plus every face in every registry passed to
`OverlayRenderer.Builder.withFontRegistry`. Atlas upload is recorded into the
host's frame command buffer once at backend init.

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

`dev.technix.mica.api.compat.v26_2.MinecraftCompatImpl_26_2` is the only file in
the project that imports `com.mojang.*`. It exists to isolate the upstream
release-specific API surface (`com.mojang.blaze3d.vulkan.*`, `com.mojang.blaze3d.systems.*`)
from the public API.

A future Minecraft release becomes `MinecraftCompatImpl_v26_3` (or `v27_0`,
depending on the version). It implements the same `MinecraftCompat` interface and
plugs into `OverlayRenderer.Builder.withMinecraftCompat(...)` unchanged.

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

* [`vulkan.md`](./vulkan.md) — the Vulkan-specific bits of the backend.
* [`api.md`](./api.md) — what the public surface looks like.
