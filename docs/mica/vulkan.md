# Vulkan backend in Mica

Mica is Vulkan-only in Minecraft 26.2. This page documents the parts of the
26.2 Vulkan pipeline that affect Mica's correctness, for anyone digging into
`src/client/java/dev/technix/mica/internal/backend/vulkan/*`.

## Reference

The upstream spec is what you'd hand to a reviewer - Mica's design assumes it.

* https://registry.khronos.org/vulkan/ - current Vulkan 1.x spec
* https://registry.khronos.org/vulkan/specs/1.3-extensions/man/html/VK_KHR_dynamic_rendering.html
* https://www.khronos.org/registry/vulkan/specs/1.3-khr-extensions/chapters/dynamicrendering.html

## Layout transitions (the slow thing)

The `FrostedGlassRenderer` records a `vkCmdBlitImage` followed by a compute
dispatch followed by a barrier. Each transition chains a `VkImageMemoryBarrier`
into the host's command buffer through the `MinecraftVulkanContext` adapter
(`accessors` in `dev.technix.mica.mixin.client.*`).

To keep this from becoming a profile hotspot, the renderer:

1. Records the source scene image to `TRANSFER_SRC_OPTIMAL` (transitions all
   `VK_IMAGE_LAYOUT_GENERAL` images into the temporary layout the blit needs).
2. Records the destination to `TRANSFER_DST_OPTIMAL`.
3. Runs the blit with linear filter, restoring both to their host-reported
   layout afterwards.

We rely on Minecraft's invariant that *"every texture stays in
`VK_IMAGE_LAYOUT_GENERAL`"* - verified in the platform's `GuiRenderState` -
so that the source layout we borrow from and the destination layout we return
to are both well-defined.

## Descriptor-set ring

The descriptor pool is owned by `ImGuiBackend`. We allocate per-texture set,
hand it to the descriptor pool, and free at backend shutdown. The pool is
*not* triple-buffered; a single descriptor set works because Minecraft waits
for frame `N - 2` of submit-completion before frame `N` starts recording, so
the resource being written from last frame is no longer in flight.

If you ever switch to a backend that doesn't honour that wait on a timeline
semaphore, the descriptor-set ring out of `GameRenderer.submit(N - 2)` becomes
the laggy path. The fix would be `n`-deep rings; a single deep pool is fine
for 26.2.

## Pipeline and render-pass

26.x drops `VkRenderPass` in favour of `VK_KHR_dynamic_rendering` - the
`GuiRenderer` calls `vkCmdBeginRenderingKHR` with explicit format/locations
information rather than binding pre-baked `VkRenderPass` objects. Mica's
`VulkanImGuiBackend` declares its colour format via
`VkPipelineRenderingCreateInfoKHR` and records into its *own*
`vkCmdBeginRenderingKHR` scope - the platform does not assume the host has a
render pass open at the time it samples the framebuffer.

## OpenGL fallback

Mica is **Vulkan-only**. The compat layer logs a one-shot `WARN` if it sees
a non-`VulkanDevice` GpuDevice (the line in `MinecraftCompatImpl_26_2.vulkanDevice()`):

```
imgui-mc-impl requires Vulkan - host GpuDevice is active as <Backend>, not VulkanDevice.
The ImGui overlay will be invisible on this run. Force Vulkan with --graphicsBackend vulkan
(build.gradle does this by default for the runClient task).
```

If you boot with `-PimguiAllowNonVulkan`, the message drops to `INFO` rather
than `WARN` so you can iterate on a Vulkan-less machine without the log
shouting at you.

## Image orientation

Minecraft 26.x uploads host textures top-to-bottom. Mica does *not* flip the
sampled UVs for host-side ImGui calls (the panel, the in-world icons), because
that's the orientation Dear ImGui's stock shader already expects. The
exception is the blur, which is rendered into a framebuffer with bottom-up
storage and *is* mirrored on the V axis during sampling - that detail is
handled inside `FrostedGlassRenderer` and not exposed to element authors.

## Debugging on a Vulkan-less environment

* `--vulkanValidation` is a Minecraft CLI flag that toggles the validation
  layer. The validation layer reports layout mismatches, missed barriers,
  descriptor-set write-after-use; turn it on whenever a test manifests.
* The build pipeline exposes `-PimguiDebugClear`, `-PimguiDebugSkipBlur`,
  `-PimguiDebugSkipDraw`. Each half of the Vulkan path can be switched off
  per run.
* `imgui.debug.clearAttachment=true` makes the blur pass clear its attachment
  to magenta instead of loading it, so a single run proves the pass reaches
  the presented image.

There are no Vulkan validation layers installed on this build host, so the
above flags exist specifically because bisecting by hand is the only way to
localise a fault.
