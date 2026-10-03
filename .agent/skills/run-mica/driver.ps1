# driver.ps1 - build / launch / observe / drive / stop the Mica Minecraft 26.2 dev client.
# Agent tooling: every command is safe to run unattended and writes to $env:MICA_RUN_DIR.
# Usage:
#   driver.ps1 build                        fast compile check (client sourceset)
#   driver.ps1 launch [-Diag] [-Perf] [-QuickPlay <world>] [-GradleProp @('-Pfoo')]
#   driver.ps1 wait <regex> [seconds]       wait until run log matches (default 180s)
#   driver.ps1 tail [lines]                 last N lines of the run log (default 40)
#   driver.ps1 log <regex>                  every matching run-log line
#   driver.ps1 ps                           list running dev clients (pid + window title)
#   driver.ps1 ss <out.png> [-GamePid n]    screenshot (PrintWindow; works unfocused)
#   driver.ps1 focus [-GamePid n]           bring the game window to the foreground
#   driver.ps1 key <keys> [-Hold ms]        send keystrokes to the focused window
#   driver.ps1 mouse <dx> <dy>              relative mouse move (camera; cursor grabbed in game)
#   driver.ps1 click [left|right] [-At x,y] click, optionally at absolute screen coords
#   driver.ps1 type <text>                  type text into the focused field
#   driver.ps1 chat <message>               open chat, type, send (slash commands work)
#   driver.ps1 diag                         print event-probe counters from the run log
#   driver.ps1 stop [-GamePid n]            kill the game process (never the Gradle daemon)
#
# Input commands (focus/key/mouse/click) drive the real window: they need the game
# focused and they steal the user's keyboard for as long as they run. ss/tail/log/ps
# are passive and safe at any time.
param(
    [Parameter(Position = 0, Mandatory = $true)]
    [ValidateSet('build', 'launch', 'wait', 'tail', 'log', 'ps', 'ss', 'focus', 'key',
                 'mouse', 'click', 'type', 'chat', 'diag', 'stop')]
    [string]$Cmd,
    [switch]$Diag,
    [switch]$Perf,
    [string]$QuickPlay,
    [string[]]$GradleProp = @(),
    [int]$GamePid = 0,
    [int]$Hold = 40,
    [string]$At,
    [Parameter(Position = 1)][string]$Arg1,
    [Parameter(Position = 2)][string]$Arg2,
    [Parameter(Position = 3)][int]$Seconds = 180
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSCommandPath)))
$runDir = if ($env:MICA_RUN_DIR) { $env:MICA_RUN_DIR } else { Join-Path $env:TEMP 'mica-run' }
$outLog = Join-Path $runDir 'client-console.log'
New-Item -ItemType Directory -Force $runDir | Out-Null

function Gradle([string[]]$GradleArgs) {
    & (Join-Path $repo 'gradlew.bat') @GradleArgs --console=plain @args
}
# Win32 surface used by ps/ss/focus/key/mouse/click.
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class MicaWin32 {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hwnd, IntPtr hdc, uint flags);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hwnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hwnd, int cmd);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern uint SendInput(uint n, INPUT[] i, int size);
    [DllImport("user32.dll")] public static extern short VkKeyScan(char c);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint code, uint mapType);
    public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)]
    public struct MOUSEINPUT { public int dx, dy; public uint mouseData, dwFlags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Sequential)]
    public struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public IntPtr extra; }
    [StructLayout(LayoutKind.Explicit)]
    public struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)]
    public struct INPUT { public uint type; public UNION u; }
    // Named INPUT_MOUSE / INPUT_KEYBOARD, not MOUSE / KEYBOARD: PowerShell member
    // lookup is case-insensitive, so a const MOUSE shadows the Mouse() method and
    // the call fails with "does not contain a method named 'Mouse'".
    public const uint INPUT_MOUSE = 0, INPUT_KEYBOARD = 1;
    public const uint KEYUP = 0x0002, MOVE = 0x0001, SCANCODE = 0x0008, EXTENDED = 0x0001;
    public const uint LDOWN = 0x0002, LUP = 0x0004, RDOWN = 0x0008, RUP = 0x0010;
    // GLFW resolves keys from the SCANCODE in lParam, not the virtual key: an
    // injected VK_RSHIFT arrives as generic VK_SHIFT and GLFW reports it as LEFT
    // shift. So always inject by scancode.
    public static void Key(ushort vk, ushort scan, bool up, bool ext) {
        INPUT[] i = new INPUT[1];
        uint flags = SCANCODE;
        if (up) flags |= KEYUP;
        if (ext) flags |= EXTENDED;
        i[0].type = INPUT_KEYBOARD; i[0].u.ki.wVk = vk; i[0].u.ki.wScan = scan; i[0].u.ki.dwFlags = flags;
        SendInput(1, i, Marshal.SizeOf(typeof(INPUT)));
    }
    public static void Mouse(uint flags, int dx, int dy) {
        INPUT[] i = new INPUT[1];
        i[0].type = INPUT_MOUSE; i[0].u.mi.dx = dx; i[0].u.mi.dy = dy; i[0].u.mi.dwFlags = flags;
        SendInput(1, i, Marshal.SizeOf(typeof(INPUT)));
    }
}
"@

# Every running dev client: the java process whose command line is the game, not the daemon.
function Get-GameProcs {
    $procs = @()
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | ForEach-Object {
        if ($_.CommandLine -match 'DevLaunch|fabric.*knot|KnotClient') {
            $p = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
            $procs += [pscustomobject]@{
                Pid    = $_.ProcessId
                Title  = if ($p) { $p.MainWindowTitle } else { '' }
                Handle = if ($p) { $p.MainWindowHandle } else { [IntPtr]::Zero }
            }
        }
    }
    return $procs
}
# The window to act on: -GamePid if given, else the first client with a real window.
function Get-GameWindow {
    $procs = Get-GameProcs
    if ($GamePid -gt 0) {
        $hit = $procs | Where-Object { $_.Pid -eq $GamePid } | Select-Object -First 1
        if (-not $hit) { throw ("no dev client with pid " + $GamePid + " (see: driver.ps1 ps)") }
        return $hit
    }
    $windowed = $procs | Where-Object { $_.Handle -ne [IntPtr]::Zero -and $_.Title -match 'Minecraft' }
    if (($windowed | Measure-Object).Count -gt 1) {
        Write-Warning ("more than one dev client is running (" +
            (($windowed | ForEach-Object { $_.Pid }) -join ', ') +
            "); acting on the first - pass -GamePid to choose")
    }
    return ($windowed | Select-Object -First 1)
}

# Friendly key names -> { Vk, Scan, Ext }. Set-1 scancodes, because that is what
# GLFW reads; MapVirtualKey fills in anything not listed.
function Resolve-Key([string]$name) {
    $table = @{
        'rshift' = @(0xA1, 0x36, $false); 'lshift' = @(0xA0, 0x2A, $false)
        'shift'  = @(0xA0, 0x2A, $false); 'lctrl'  = @(0xA2, 0x1D, $false)
        'rctrl'  = @(0xA3, 0x1D, $true);  'ctrl'   = @(0xA2, 0x1D, $false)
        'alt'    = @(0xA4, 0x38, $false); 'escape' = @(0x1B, 0x01, $false)
        'esc'    = @(0x1B, 0x01, $false); 'enter'  = @(0x0D, 0x1C, $false)
        'space'  = @(0x20, 0x39, $false); 'tab'    = @(0x09, 0x0F, $false)
        'up'     = @(0x26, 0x48, $true);  'down'   = @(0x28, 0x50, $true)
        'left'   = @(0x25, 0x4B, $true);  'right'  = @(0x27, 0x4D, $true)
        'backspace' = @(0x08, 0x0E, $false)
    }
    $key = $name.ToLower()
    if ($table.ContainsKey($key)) {
        $e = $table[$key]
        return [pscustomobject]@{ Vk = [int]$e[0]; Scan = [int]$e[1]; Ext = [bool]$e[2] }
    }
    $vk = 0
    if ($key -match '^f([1-9]|1[0-2])$') {
        $vk = 0x6F + [int]$Matches[1]
    } elseif ($key.Length -eq 1) {
        $scan = [MicaWin32]::VkKeyScan($key[0])
        if ($scan -eq -1) { throw ("cannot map key: " + $name) }
        $vk = ($scan -band 0xFF)
    } else {
        throw ("unknown key name: " + $name + " (try w, rshift, escape, f3)")
    }
    return [pscustomobject]@{
        Vk = $vk
        Scan = [int][MicaWin32]::MapVirtualKey([uint32]$vk, 0)
        Ext = $false
    }
}

# Types a string one character at a time, holding shift where the layout needs it.
# Chat and text fields read WM_CHAR, which SendInput produces from the scancode.
function Send-Text([string]$text) {
    foreach ($ch in $text.ToCharArray()) {
        $scanRes = [MicaWin32]::VkKeyScan($ch)
        if ($scanRes -eq -1) { throw ("cannot type character: " + $ch) }
        $vk = $scanRes -band 0xFF
        $needShift = ((($scanRes -shr 8) -band 1) -ne 0)
        $sc = [int][MicaWin32]::MapVirtualKey([uint32]$vk, 0)
        if ($needShift) { [MicaWin32]::Key(0xA0, 0x2A, $false, $false) }
        [MicaWin32]::Key([uint16]$vk, [uint16]$sc, $false, $false)
        Start-Sleep -Milliseconds 12
        [MicaWin32]::Key([uint16]$vk, [uint16]$sc, $true, $false)
        if ($needShift) { [MicaWin32]::Key(0xA0, 0x2A, $true, $false) }
        Start-Sleep -Milliseconds 18
    }
}
switch ($Cmd) {

    'build' {
        Gradle 'compileClientJava' | ForEach-Object { Write-Output $_ }
        exit $LASTEXITCODE
    }

    'launch' {
        if (Test-Path $outLog) { Remove-Item -Force $outLog }
        $props = @()
        if ($Diag) { $props += '-PeventDiag' }
        if ($Perf) { $props += '-PimguiPerf' }
        if ($QuickPlay) { $props += "-PimguiQuickPlay=$QuickPlay" }
        if ($GradleProp) { $props += $GradleProp }
        $p = Start-Process -FilePath (Join-Path $repo 'gradlew.bat') `
            -ArgumentList (@('runClient', '--console=plain') + $props) `
            -WorkingDirectory $repo -RedirectStandardOutput $outLog `
            -RedirectStandardError (Join-Path $runDir 'client-console.err.log') `
            -PassThru -WindowStyle Hidden
        Write-Output ("launched gradle pid=" + $p.Id + " log=" + $outLog)
    }

    'wait' {
        if (-not $Arg1) { throw 'wait <regex> [seconds]' }
        $deadline = (Get-Date).AddSeconds($Seconds)
        while ((Get-Date) -lt $deadline) {
            if (Test-Path $outLog) {
                $hit = Select-String -Path $outLog -Pattern $Arg1 -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($hit) { Write-Output $hit.Line; exit 0 }
            }
            Start-Sleep -Seconds 3
        }
        Write-Output ("TIMEOUT waiting for: " + $Arg1)
        exit 1
    }

    'tail' {
        if (-not (Test-Path $outLog)) { throw ('no run log at ' + $outLog + ' - launch first') }
        $n = if ($Arg1) { [int]$Arg1 } else { 40 }
        Get-Content $outLog -Tail $n
    }

    'log' {
        if (-not $Arg1) { throw 'log <regex>' }
        if (-not (Test-Path $outLog)) { throw ('no run log at ' + $outLog + ' - launch first') }
        Select-String -Path $outLog -Pattern $Arg1 | ForEach-Object { $_.Line }
    }

    'ps' {
        $procs = Get-GameProcs
        if (($procs | Measure-Object).Count -eq 0) { Write-Output 'no dev client running'; exit 1 }
        $procs | Format-Table -AutoSize | Out-String | Write-Output
    }
    'ss' {
        if (-not $Arg1) { throw 'ss <out.png>' }
        Add-Type -AssemblyName System.Drawing
        # PrintWindow(PW_RENDERFULLCONTENT) renders the window itself even when
        # occluded (AnyDesk popups, etc.), unlike CopyFromScreen.
        $captured = $false
        $bmp = $null
        $win = Get-GameWindow
        if ($win -and $win.Handle -ne [IntPtr]::Zero) {
            $r = New-Object MicaWin32+RECT
            [MicaWin32]::GetWindowRect($win.Handle, [ref]$r) | Out-Null
            $w = $r.Right - $r.Left; $h2 = $r.Bottom - $r.Top
            if ($w -gt 50 -and $h2 -gt 50) {
                $bmp = New-Object System.Drawing.Bitmap($w, $h2)
                $g = [System.Drawing.Graphics]::FromImage($bmp)
                $hdc = $g.GetHdc()
                $captured = [MicaWin32]::PrintWindow($win.Handle, $hdc, 2)
                $g.ReleaseHdc($hdc)
                $g.Dispose()
            }
        }
        if (-not $captured -or $null -eq $bmp) {
            Add-Type -AssemblyName System.Windows.Forms
            $vs = [System.Windows.Forms.SystemInformation]::VirtualScreen
            $bmp = New-Object System.Drawing.Bitmap($vs.Width, $vs.Height)
            $g = [System.Drawing.Graphics]::FromImage($bmp)
            $g.CopyFromScreen($vs.X, $vs.Y, 0, 0, $bmp.Size)
        }
        $bmp.Save($Arg1, [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output ("saved " + $Arg1 + " (" + $bmp.Width + "x" + $bmp.Height + ") printwindow=" + $captured)
    }

    'focus' {
        $win = Get-GameWindow
        if (-not $win -or $win.Handle -eq [IntPtr]::Zero) { throw 'no dev client window (see: driver.ps1 ps)' }
        [MicaWin32]::ShowWindow($win.Handle, 9) | Out-Null   # SW_RESTORE
        $ok = [MicaWin32]::SetForegroundWindow($win.Handle)
        if (-not $ok) {
            # Windows blocks SetForegroundWindow from a background process; the shell
            # COM object is allowed to do it.
            (New-Object -ComObject WScript.Shell).AppActivate($win.Pid) | Out-Null
        }
        Start-Sleep -Milliseconds 400
        $now = [MicaWin32]::GetForegroundWindow()
        Write-Output ("focused pid=" + $win.Pid + " foreground=" + ($now -eq $win.Handle))
    }
    'key' {
        if (-not $Arg1) { throw 'key <keys> [-Hold ms]   e.g. key rshift | key w -Hold 1000 | key "w,w"' }
        foreach ($name in ($Arg1 -split '[,+ ]+' | Where-Object { $_ })) {
            $k = Resolve-Key $name
            [MicaWin32]::Key([uint16]$k.Vk, [uint16]$k.Scan, $false, $k.Ext)
            Start-Sleep -Milliseconds $Hold
            [MicaWin32]::Key([uint16]$k.Vk, [uint16]$k.Scan, $true, $k.Ext)
            Start-Sleep -Milliseconds 60
            Write-Output ("sent " + $name + " (vk=0x" + $k.Vk.ToString('X2') +
                " scan=0x" + $k.Scan.ToString('X2') + " ext=" + $k.Ext + ") hold=" + $Hold + "ms")
        }
    }

    'mouse' {
        if (-not $Arg1 -or -not $Arg2) { throw 'mouse <dx> <dy>' }
        # In game the cursor is grabbed, so only relative deltas move the camera.
        # Split the move so the game samples it over several frames.
        $dx = [int]$Arg1; $dy = [int]$Arg2
        $steps = 8
        for ($i = 0; $i -lt $steps; $i++) {
            [MicaWin32]::Mouse([MicaWin32]::MOVE, [int]($dx / $steps), [int]($dy / $steps))
            Start-Sleep -Milliseconds 16
        }
        Write-Output ("moved mouse dx=" + $dx + " dy=" + $dy + " in " + $steps + " steps")
    }

    'click' {
        $button = if ($Arg1) { $Arg1.ToLower() } else { 'left' }
        if ($At) {
            $parts = $At -split '[, ]+'
            if ($parts.Count -ne 2) { throw 'click -At "x,y"' }
            [MicaWin32]::SetCursorPos([int]$parts[0], [int]$parts[1]) | Out-Null
            Start-Sleep -Milliseconds 120
        }
        $down = if ($button -eq 'right') { [MicaWin32]::RDOWN } else { [MicaWin32]::LDOWN }
        $up = if ($button -eq 'right') { [MicaWin32]::RUP } else { [MicaWin32]::LUP }
        [MicaWin32]::Mouse($down, 0, 0)
        Start-Sleep -Milliseconds $Hold
        [MicaWin32]::Mouse($up, 0, 0)
        $where = if ($At) { ' at ' + $At } else { '' }
        Write-Output ("clicked " + $button + $where)
    }

    'type' {
        if (-not $Arg1) { throw 'type <text>   (sends the characters to the focused window)' }
        Send-Text $Arg1
        Write-Output ("typed " + $Arg1.Length + " chars")
    }

    'chat' {
        if (-not $Arg1) { throw 'chat <message>   e.g. chat "/time set day"' }
        # t opens the vanilla chat screen, then the text, then enter to send.
        $k = Resolve-Key 't'
        [MicaWin32]::Key([uint16]$k.Vk, [uint16]$k.Scan, $false, $false)
        Start-Sleep -Milliseconds 40
        [MicaWin32]::Key([uint16]$k.Vk, [uint16]$k.Scan, $true, $false)
        Start-Sleep -Milliseconds 350
        Send-Text $Arg1
        Start-Sleep -Milliseconds 120
        $e = Resolve-Key 'enter'
        [MicaWin32]::Key([uint16]$e.Vk, [uint16]$e.Scan, $false, $false)
        Start-Sleep -Milliseconds 40
        [MicaWin32]::Key([uint16]$e.Vk, [uint16]$e.Scan, $true, $false)
        Start-Sleep -Milliseconds 250
        Write-Output ("sent chat: " + $Arg1)
    }

    'diag' {
        if (-not (Test-Path $outLog)) { throw ('no run log at ' + $outLog + ' - launch first') }
        $counts = Select-String -Path $outLog -Pattern 'diag\] counts:' | Select-Object -Last 1
        if ($counts) { $counts.Line } else { Write-Output 'no counts yet'; exit 1 }
    }

    'stop' {
        # Kill only the game process (devlaunch/knot), never the Gradle daemon.
        # With more than one dev client running (a second agent session, say), pass
        # -GamePid so this does not take out somebody else's run.
        $procs = Get-GameProcs
        if ($GamePid -gt 0) { $procs = $procs | Where-Object { $_.Pid -eq $GamePid } }
        if (($procs | Measure-Object).Count -eq 0) { Write-Output 'no matching dev client'; exit 1 }
        foreach ($p in $procs) {
            Stop-Process -Id $p.Pid -Force
            Write-Output ('stopped game pid ' + $p.Pid)
        }
    }
}
