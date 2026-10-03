---
name: run-mica
description: Build, launch, screenshot, and drive the Mica Minecraft 26.2 Fabric dev client from the command line. Use when asked to run, start, launch, test, screenshot, or verify Mica or a client module in the real game, or to toggle a module in the ClickGUI, send chat commands, or measure a movement change in-game.
---

# Run Mica

Mica is a Fabric mod for **Minecraft 26.2** (Vulkan-only ImGui overlay) plus the
author's personal client layer under `dev/technix/client/`. The app is a GUI game,
so everything here goes through one driver script:

**`.claude/skills/run-mica/driver.ps1`** — build, launch, wait on the log, list
clients, screenshot (works unfocused), focus, inject keys/mouse/clicks, type chat
commands, stop. All paths below are relative to the repo root; run from there.

## Prerequisites

No installs were needed on this machine. What must exist:

- **Windows** with a **Vulkan-capable GPU** (the mod ships only a Vulkan renderer;
  verified in-game: `Vulkan 1.4.341 NVIDIA 610.62`, RTX 4050).
- **JDK 25** for the Gradle toolchain at `~/.jdks/ms-25.0.4`. `java` on PATH here
  is Java 8 and cannot read these class files — always use the explicit path for
  ad-hoc `javap`/`javac`.
- PowerShell 5.1 (the driver is PowerShell; the `Bash` tool cannot run it).

## Build

```powershell
.\.claude\skills\run-mica\driver.ps1 build     # gradlew compileClientJava, exit 0 on success
```

```bash
./gradlew test                                  # JUnit 5; 100+ tests, ~40s
```

To compile **one** file when the tree is red because someone else is mid-edit:

```bash
./gradlew -I .claude/skills/run-mica/print-classpath.gradle printClientCp -q | tail -1 > /tmp/cp.txt
"$HOME/.jdks/ms-25.0.4/bin/javac.exe" -nowarn -proc:none \
  -cp "$(cat /tmp/cp.txt);build/classes/java/client" -d /tmp/out \
  src/client/java/dev/technix/client/feature/module/impl/combat/SprintReset.java
```

## Run (agent path)

```powershell
$d = ".\.claude\skills\run-mica\driver.ps1"

& $d launch -QuickPlay DiagWorld          # background; -Diag event probe, -Perf HUD profiler
& $d wait "joined the game" 300           # ~55s cold; prints the matching line
& $d ps                                   # -> Pid 6524, "Minecraft* 26.2 - Singleplayer"
& $d ss "$PWD\run\shot.png" -GamePid 6524 # PrintWindow: captures unfocused/occluded
& $d tail 40                              # last N console lines
& $d log "Disabling overlay|failed during" # grep the whole run log
& $d stop -GamePid 6524                   # kill only this client
```

Driving the game (these need focus and steal the keyboard while they run):

```powershell
& $d focus -GamePid 6524                  # -> "focused pid=6524 foreground=True"
& $d key escape                           # dismiss the pause menu
& $d key f3                               # debug overlay: XYZ, facing, renderer
& $d key rshift                           # open/close the ClickGUI
& $d key w -Hold 2000                     # hold a key for N ms
& $d click left -At "733,470"             # absolute screen coords
& $d mouse 200 0                          # relative camera delta (cursor is grabbed in-game)
& $d chat "/tp @s 106.5 144 -21.5 90 0"   # opens chat, types, sends
& $d type "hello"                         # type into whatever field has focus
```

**Screen coordinates for `click`:** the window is not fullscreen. Get its origin
and add the pixel offset you read off the screenshot (the PNG includes the title
bar, so image coords map 1:1 onto the window rect):

```powershell
Add-Type @"
using System; using System.Runtime.InteropServices;
public class R { [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
public struct RECT { public int Left, Top, Right, Bottom; } }
"@
$rect = New-Object R+RECT
[R]::GetWindowRect((Get-Process -Id 6524).MainWindowHandle, [ref]$rect) | Out-Null
$rect   # this session: left=525 top=269 right=1395 bottom=788  (870x519)
```

### Toggling a module in the ClickGUI (verified flow)

`focus` -> `key rshift` (GUI opens, cursor released) -> `click left -At "<row>"`
to select the module (its settings render in the right pane) -> `click left` on
the header toggle pill at the pane's top-right to flip it (footer reads
`Active` / `Inactive`) -> `key rshift` to close.

Module toggles and settings persist to `run/mica/configs/default.json` on
autosave, so you can also read state from there instead of screenshotting.

### Measuring a movement change (verified recipe)

Movement modules need an A/B on identical terrain, because the spawn area is a
hill and absolute speeds are not comparable between runs:

```powershell
& $d chat "/tp @s 106.5 144 -21.5 90 0"   # fixed spot AND fixed yaw/pitch
& $d key w -Hold 2000
& $d ss "$PWD\run\a.png" -GamePid 6524    # read XYZ off F3
& $d chat "/tp @s 106.5 144 -21.5 90 0"
& $d chat "/effect give @s minecraft:blindness 20 0 true"   # vanilla gate: blocks sprint
& $d key w -Hold 2000
& $d ss "$PWD\run\b.png" -GamePid 6524
& $d chat "/effect clear @s"
```

Measured this way, the Sprint module gave 10.195 blocks vs 8.109 blindfolded
(+25.7%, against vanilla's +30% sprint modifier) — proof the sprint actually
engaged rather than just the module being toggled on.

### In-game probe harnesses

The project drives itself for anything needing many ticks: `-PeventDiag`
(`dev.technix.client.event.diag.EventDiagnostics`, counters via
`driver.ps1 diag`) and `-ProtationDiag` (scripted rotation probe that logs
`[rotdiag] PASS ...` and a `SUMMARY passed=N failed=0`). Copy that pattern —
assert in-game and grep the log — instead of scripting clicks.

## Run (human path)

`./gradlew runClient` opens the window in the foreground and blocks the shell;
Ctrl-C kills it. Useless for unattended work: no log file, no PID, and it takes
the keyboard.

## Gotchas

- **`key rshift` only works by scancode.** SendInput with `wVk=VK_RSHIFT` is
  delivered as generic `VK_SHIFT`; GLFW resolves keys from the lParam scancode
  and reports LEFT shift, so the ClickGUI never opens (it silently sneaks
  instead). The driver injects `KEYEVENTF_SCANCODE` with set-1 codes
  (rshift `0x36`, lshift `0x2A`) for exactly this reason.
- **PowerShell member lookup is case-insensitive.** A C# `public const uint MOUSE`
  shadowed the `Mouse()` method in the driver's Add-Type block:
  `[MicaWin32] does not contain a method named 'Mouse'`. The consts are now
  `INPUT_MOUSE` / `INPUT_KEYBOARD`.
- **`pauseOnLostFocus:false` does not stick.** Set it in `run/options.txt` before
  launch and the client still rewrites the file to `true` at startup, so an
  unfocused game sits on the Game Menu. Do `focus` then `key escape` before
  screenshotting; `ss` itself works unfocused, but the menu will be in the shot.
- **Re-read the window rect every launch.** `click -At` takes absolute screen
  coordinates, and the window lands somewhere different each run (525,269 on one
  launch here, elsewhere on the next). Reusing coordinates from an earlier
  session's screenshot silently clicks the wrong rows — that is how you end up
  toggling four unrelated HUD modules.
- **Closing the ClickGUI writes the config.** `closeInternal()` calls
  `ConfigManager.autoSave()`, and so do world change and a clean exit. If an
  experiment left module toggles you do not want persisted, leave the GUI open
  and `stop` (a force kill fires no `ClientStopEvent`), then check
  `run/mica/configs/default.json`'s mtime to confirm nothing was written.
- **`stop` without `-GamePid` kills every dev client on the machine**, including
  one another agent session launched. Always `ps` first.
- **The ClickGUI re-grabs the mouse on close and snaps the camera.** The OS
  cursor you moved for `click` becomes one big mouse delta — the view jumped from
  yaw 0 to yaw 79.9 / pitch 38.4 here. Re-set the view with `/tp @s <x> <y> <z>
  <yaw> <pitch>` before measuring anything.
- **Two clients cannot share `DiagWorld`** — the second gets a `session.lock`
  failure. There is one save (`DiagWorld`), and `-QuickPlay` breaks on world
  names containing spaces (unquoted `Start-Process` args become Gradle tasks).
- **Walking south from spawn stops after 1.2 blocks** (hillside). Face west
  (`yaw 90`) for a clear ~10-block run.
- **`ps` and `diag` exit 1** when there is nothing to report, which makes a
  chained PowerShell call look failed. `diag` also needs `-Diag`, otherwise it
  prints `no counts yet`.
- **A module whose `render()` throws once is disabled for the session** — grep
  `Disabling overlay element`. Logic-only modules should override `shouldDraw`
  to `false` so they never enter the overlay pass.
- **Harmless log noise**, present on every dev run: `Could not authorize you
  against Realms server` / `Failed to parse into SignedJWT: FabricMC` (401,
  offline dev account), `NumberFormatException: For input string:
  "key.keyboard.g"` (vanilla options parse), `swapchain out of date` on resize,
  and `Font <name> added before ImGui context is alive` during startup.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `wait` times out at 300s | `tail 60` — a mixin failure or a Java-version error aborts before any world loads. Check `run/logs/latest.log` too. |
| Screenshot is the Game Menu | The window is unfocused and the client re-enabled `pauseOnLostFocus`. `focus` + `key escape`, then `ss`. |
| `key rshift` does nothing | Something else owns input (a vanilla screen, or the search field has focus). `key escape` first. |
| `click` lands nowhere | Coordinates are absolute screen, not image-relative. Add the window rect origin. |
| Build red but your file is fine | Another session may be mid-edit in `dev/technix/client/`. Compile your single file with `javac` (see Build). |
| `no dev client running` after `launch` | Gradle is still building; `wait "joined the game"` instead of polling `ps`. |
