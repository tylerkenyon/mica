package dev.technix.mica.internal;

import com.mojang.blaze3d.platform.InputConstants;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;

import java.util.HashMap;
import java.util.Map;


/**
 * Feeds Minecraft's mouse and keyboard events into Dear ImGui. Backend-independent, and
 * Minecraft-version-independent: keys and buttons are translated through Minecraft's own
 * {@link InputConstants}, which carry GLFW codes on 26.2 and SDL scancodes on 26.3, so the
 * same table serves both.
 */
public final class ImGuiInputRouter {

    private static final Map<Integer, Integer> KEYS = buildKeyMap();

    private ImGuiInputRouter() {
    }

    public static void onMouseMove(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                                   double xpos, double ypos) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        ImGui.getIO().addMousePosEvent((float) xpos, (float) ypos);
    }

    public static void onMouseButton(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                                     int button, boolean pressed) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        int imGuiButton = toImGuiButton(button);
        if (imGuiButton >= 0) {
            ImGui.getIO().addMouseButtonEvent(imGuiButton, pressed);
        }
    }

    public static void onMouseScroll(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                                     double xOffset, double yOffset) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        ImGui.getIO().addMouseWheelEvent((float) xOffset, (float) yOffset);
    }

    public static void onKey(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                             int key, int action) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        int imGuiKey = toImGuiKey(key);
        if (imGuiKey != ImGuiKey.None) {
            ImGui.getIO().addKeyEvent(imGuiKey, action != InputConstants.RELEASE);
        }
    }

    public static void onChar(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer, int codepoint) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        ImGuiIO io = ImGui.getIO();
        if (codepoint > 0 && codepoint <= Character.MAX_CODE_POINT) {
            io.addInputCharactersUTF8(new String(Character.toChars(codepoint)));
        }
    }

    /** Minecraft mouse button ({@code MouseButtonInfo.button()}) to ImGui, or -1. */
    static int toImGuiButton(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            return ImGuiMouseButton.Left;
        }
        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            return ImGuiMouseButton.Right;
        }
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
            return ImGuiMouseButton.Middle;
        }
        if (button == InputConstants.MOUSE_BUTTON_4) {
            return 3;
        }
        if (button == InputConstants.MOUSE_BUTTON_5) {
            return 4;
        }
        return -1;
    }

    /** Minecraft key code ({@code KeyEvent.key()}) to {@link ImGuiKey}, or {@code ImGuiKey.None}. */
    static int toImGuiKey(int key) {
        return KEYS.getOrDefault(key, ImGuiKey.None);
    }

    private static Map<Integer, Integer> buildKeyMap() {
        Map<Integer, Integer> keys = new HashMap<>();
        keys.put(InputConstants.KEY_TAB, ImGuiKey.Tab);
        keys.put(InputConstants.KEY_LEFT, ImGuiKey.LeftArrow);
        keys.put(InputConstants.KEY_RIGHT, ImGuiKey.RightArrow);
        keys.put(InputConstants.KEY_UP, ImGuiKey.UpArrow);
        keys.put(InputConstants.KEY_DOWN, ImGuiKey.DownArrow);
        keys.put(InputConstants.KEY_PAGEUP, ImGuiKey.PageUp);
        keys.put(InputConstants.KEY_PAGEDOWN, ImGuiKey.PageDown);
        keys.put(InputConstants.KEY_HOME, ImGuiKey.Home);
        keys.put(InputConstants.KEY_END, ImGuiKey.End);
        keys.put(InputConstants.KEY_INSERT, ImGuiKey.Insert);
        keys.put(InputConstants.KEY_DELETE, ImGuiKey.Delete);
        keys.put(InputConstants.KEY_BACKSPACE, ImGuiKey.Backspace);
        keys.put(InputConstants.KEY_SPACE, ImGuiKey.Space);
        keys.put(InputConstants.KEY_RETURN, ImGuiKey.Enter);
        keys.put(InputConstants.KEY_NUMPADENTER, ImGuiKey.KeypadEnter);
        keys.put(InputConstants.KEY_ESCAPE, ImGuiKey.Escape);
        keys.put(InputConstants.KEY_APOSTROPHE, ImGuiKey.Apostrophe);
        keys.put(InputConstants.KEY_COMMA, ImGuiKey.Comma);
        keys.put(InputConstants.KEY_MINUS, ImGuiKey.Minus);
        keys.put(InputConstants.KEY_PERIOD, ImGuiKey.Period);
        keys.put(InputConstants.KEY_SLASH, ImGuiKey.Slash);
        keys.put(InputConstants.KEY_SEMICOLON, ImGuiKey.Semicolon);
        keys.put(InputConstants.KEY_EQUALS, ImGuiKey.Equal);
        keys.put(InputConstants.KEY_LBRACKET, ImGuiKey.LeftBracket);
        keys.put(InputConstants.KEY_BACKSLASH, ImGuiKey.Backslash);
        keys.put(InputConstants.KEY_RBRACKET, ImGuiKey.RightBracket);
        keys.put(InputConstants.KEY_GRAVE, ImGuiKey.GraveAccent);
        keys.put(InputConstants.KEY_CAPSLOCK, ImGuiKey.CapsLock);
        keys.put(InputConstants.KEY_SCROLLLOCK, ImGuiKey.ScrollLock);
        keys.put(InputConstants.KEY_NUMLOCK, ImGuiKey.NumLock);
        keys.put(InputConstants.KEY_PRINTSCREEN, ImGuiKey.PrintScreen);
        keys.put(InputConstants.KEY_PAUSE, ImGuiKey.Pause);
        // Modifiers: ImGui derives Ctrl/Shift/Alt state (and shortcuts such as Ctrl+A/C/V in
        // text fields) from these key events.
        keys.put(InputConstants.KEY_LCONTROL, ImGuiKey.LeftCtrl);
        keys.put(InputConstants.KEY_RCONTROL, ImGuiKey.RightCtrl);
        keys.put(InputConstants.KEY_LSHIFT, ImGuiKey.LeftShift);
        keys.put(InputConstants.KEY_RSHIFT, ImGuiKey.RightShift);
        keys.put(InputConstants.KEY_LALT, ImGuiKey.LeftAlt);
        keys.put(InputConstants.KEY_RALT, ImGuiKey.RightAlt);

        // Letters, digits, F-keys and the keypad are listed one by one: their codes are
        // contiguous in GLFW but not in SDL (SDL puts 0 after 9), so no arithmetic.
        int[] letters = {InputConstants.KEY_A, InputConstants.KEY_B, InputConstants.KEY_C,
                InputConstants.KEY_D, InputConstants.KEY_E, InputConstants.KEY_F, InputConstants.KEY_G,
                InputConstants.KEY_H, InputConstants.KEY_I, InputConstants.KEY_J, InputConstants.KEY_K,
                InputConstants.KEY_L, InputConstants.KEY_M, InputConstants.KEY_N, InputConstants.KEY_O,
                InputConstants.KEY_P, InputConstants.KEY_Q, InputConstants.KEY_R, InputConstants.KEY_S,
                InputConstants.KEY_T, InputConstants.KEY_U, InputConstants.KEY_V, InputConstants.KEY_W,
                InputConstants.KEY_X, InputConstants.KEY_Y, InputConstants.KEY_Z};
        for (int index = 0; index < letters.length; index++) {
            keys.put(letters[index], ImGuiKey.A + index);
        }
        int[] digits = {InputConstants.KEY_0, InputConstants.KEY_1, InputConstants.KEY_2,
                InputConstants.KEY_3, InputConstants.KEY_4, InputConstants.KEY_5, InputConstants.KEY_6,
                InputConstants.KEY_7, InputConstants.KEY_8, InputConstants.KEY_9};
        int[] keypad = {InputConstants.KEY_NUMPAD0, InputConstants.KEY_NUMPAD1,
                InputConstants.KEY_NUMPAD2, InputConstants.KEY_NUMPAD3, InputConstants.KEY_NUMPAD4,
                InputConstants.KEY_NUMPAD5, InputConstants.KEY_NUMPAD6, InputConstants.KEY_NUMPAD7,
                InputConstants.KEY_NUMPAD8, InputConstants.KEY_NUMPAD9};
        for (int index = 0; index < digits.length; index++) {
            keys.put(digits[index], ImGuiKey._0 + index);
            keys.put(keypad[index], ImGuiKey.Keypad0 + index);
        }
        int[] functionKeys = {InputConstants.KEY_F1, InputConstants.KEY_F2, InputConstants.KEY_F3,
                InputConstants.KEY_F4, InputConstants.KEY_F5, InputConstants.KEY_F6, InputConstants.KEY_F7,
                InputConstants.KEY_F8, InputConstants.KEY_F9, InputConstants.KEY_F10,
                InputConstants.KEY_F11, InputConstants.KEY_F12};
        for (int index = 0; index < functionKeys.length; index++) {
            keys.put(functionKeys[index], ImGuiKey.F1 + index);
        }
        return Map.copyOf(keys);
    }
}
