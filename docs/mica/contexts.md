# `MicaScreen` and the per-frame context filter

`MicaScreen` is the enum an `OverlayElement.renderScope()` returns. The renderer reads
the current screen once per frame and skips elements whose scope does not match.

## The enum

```java
public enum MicaScreen { ANY, TITLE, IN_GAME_HUD, PAUSE, INVENTORY, CHAT, OTHER }
```

| Constant        | Trigger in Minecraft 1.26+                                         |
| --------------- | ------------------------------------------------------------------ |
| `ANY`           | Default; matches every screen.                                     |
| `TITLE`         | `gui.screen() instanceof TitleScreen`.                              |
| `IN_GAME_HUD`   | No top-level screen; the in-world HUD overlay.                     |
| `PAUSE`         | `gui.screen() instanceof PauseScreen`.                              |
| `INVENTORY`     | `gui.screen() instanceof AbstractContainerScreen` (chests, etc.).   |
| `CHAT`          | `gui.screen() instanceof ChatScreen`.                               |
| `OTHER`         | Anything that doesn't classify into the above.                      |

`IN_GAME_HUD` is the most common scope: traditional HUDs (crosshair, hotbar, chat
extensions) live here, and the platform will not draw them on the title screen or in
the pause menu.

## Detector pipeline

The detector runs `instanceof` checks against the canonical screen classes:

```java
Screen screen = Minecraft.getInstance().gui.screen();
if (screen == null) return MicaScreen.IN_GAME_HUD;
if (screen instanceof TitleScreen) return MicaScreen.TITLE;
if (screen instanceof PauseScreen) return MicaScreen.PAUSE;
if (screen instanceof AbstractContainerScreen) return MicaScreen.INVENTORY;
if (screen instanceof ChatScreen) return MicaScreen.CHAT;
return MicaScreen.OTHER;
```

The eight `instanceof` checks run once per frame. The cost lives in the JIT
hot-spot, not in your element code.

## The per-frame filter

For each registered element:

```
if (failed.contains(element))                    continue;
MicaScreen scope = element.renderScope();
if (scope != MicaScreen.ANY && scope != current) continue;
if (element.isVisible(context))                  element.render(context);
```

The `failed.contains(element)` check is a fast ArrayList linear scan but is bounded
because failed elements are typically zero in steady state. If you have hundreds of
elements and observe filter cost, swap the containers for `LinkedHashSet`; the
public API exposes the renderer as an `AutoCloseable` and the element list is
internal.

`ANY` is special: it evaluates true against every value of `currentScreen`, so an
element declaring `ANY` draws regardless of context. That's the right setting for
elements that are universal overlays (toast notifications, dev tooling, an FPS
counter).

## When the detector rewrites

The detector references the canonical Mojang screen classes; new Minecraft releases
introduce new ones. The detector is centralised in
`dev.technix.mica.internal.ScreenDetector`; bumping Minecraft is a one-file change.

## Edge cases

* **Loading overlays.** `Minecraft.getInstance().gui.screen()` may return a loading
  or progress-bar screen during world loading. The detector classifies that as
  `OTHER`. Use `ANY` for elements that should appear during loading.
* **Mod-added content.** A mod that opens a custom screen not extending
  `AbstractContainerScreen` falls into `OTHER` — opt in there if your element is
  the right shape for it.
* **Two-up screens.** The detector returns the *current* screen. If you have a
  layered UI (e.g. chat open over inventory), you may want to match `ANY` rather
  than scope narrowly.

## Per-frame cost

A typical frame with eight elements costs about 0.05 ms for the filter stage (eight
`instanceof` checks + eight set contains lookups). Compare against the rest of the
per-frame draw work (font atlas upload, blur pass, ImGui render submission) which
dominates; the filter cost is what you spend to never have an off-screen element
contaminate the framebuffer.

## See also

* [`elements.md`](./elements.md) — the `OverlayElement` lifecycle that consumes this enum.
* [`api.md`](./api.md) — `MicaScreen` symbol reference.
