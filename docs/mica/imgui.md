# imgui-java in Mica

Mica uses the [imgui-java binding](https://github.com/SpaiR/imgui-java)
(version `1.92.7.1` at the time of writing). Everything Dear ImGui does inside
Mica flows through this Java binding's `imgui.*` package - draw lists,
`ImGuiIO`, `ImVec2`, and so on. The ImGui backend itself lives in
`dev.technix.mica.internal.backend.vulkan.VulkanImGuiBackend`.

## Font handling

Mica ships four SF Pro Display faces (`regular`, `medium`, `bold`, `logo`)
under `assets/mica/font/*.otf`, loaded through `dev.technix.mica.internal.ImGuiFonts`.
The atlas is rasterised once, at `VulkanImGuiBackend.init()`, and uploaded
into Minecraft's frame command buffer (the `recordPendingTransfers` path).

The four faces each have a fixed pixel size - that's a current
`imgui-java 1.92` constraint (`ImGui.pushFont(int)` is `int`, not `float`):
`*Regular 13 / *Medium 13 / *Bold 13 / *Logo 20`. See PROGRESS.md if you ever
need to swap in another typeface - one line per face in `ImGuiFonts`.

Replace path: drop your `.otf`s into the same directory, point each
`Face` constant at your file, the rest of the codebase doesn't change.

## Scissor rects

`ImDrawList.pushClipRect(x0, y0, x1, y1, intersect_with_current_clip_rect)`
is the only scissor primitive imgui-java exposes. It is rectangular: there is
no `pushClipRoundedRect`. Mica uses it where the visual goal is "stay inside
this rounded panel" with the understanding that the clip is a *bounding box*
around the curve, not the curve itself; you see straight cuts at the rounded
corners, not a smooth match. For Mica's toast (a 14 px-rounded panel with a
2 px progress bar at its dead bottom) that reads correctly because the inset
is small relative to the corner radius.

For a true rounded-clip follow, swap to `ImDrawList.pathRect` and a
custom path-fill inside that path. The API surface allows it; Mica just does
not need it for the elements shipped today.

## `ImDrawFlags` constants

`imgui-java 1.92.7.1` exposes the `imgui.flag.ImDrawFlags` enum. Mica uses
two of them today:

| Constant                       | Value | Where Mica uses it                                       |
| ------------------------------ | ----- | --------------------------------------------------------- |
| `RoundCornersBottomLeft`       | 64    | (Reserved - the panel+progress use pushClipRect instead.) |
| `RoundCornersBottomRight`      | 128   | (Reserved.)                                               |
| `RoundCornersBottom`          | 192   | The platform's `Draw.addRectFilled` overload for ground-aligned bars (legacy, kept for the public API). |

`addRectFilled` rounds a rectangle whose height is taller than the requested
rounding by clamping the effective corner radius to half the height - a 2 px
bar with `ROUNDING = 14` ends up rounding to 1 px on each end. That is why the
progress bar's scissor-pad approach is more visually reliable than rounding
the bar itself.

## Things not in the upstream README

* `ImGui.getIO()` allocates a fresh `ImGuiIO` only on first call. Repeated
  calls are cheap, but mixing that with `ImGui.createContext()`/`destroyContext()`
  outside `ImGuiRenderer.beginFrame`/`shutdown()` will produce stale IO
  closures. Always go through the platform's lifecycle.
* `ImGui.calcTextSizeX(Font face, String text)` is a Mica-side helper that
  pushes the font, measures, pops. Without the push/pop dance the size
  returns in the wrong face's metrics.
* `ImDrawList.addImageRounded` accepts floats for the corner radius and
  *does* respect V-flipped UVs. That is what makes the frosted-glass blur
  work even though Minecraft's framebuffer is bottom-up.
