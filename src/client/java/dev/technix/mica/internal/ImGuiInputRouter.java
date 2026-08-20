package dev.technix.mica.internal;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiKey;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ImGuiInputRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger("mica");

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
        ImGui.getIO().addMouseButtonEvent(button, pressed);
    }

    public static void onMouseScroll(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                                     double xOffset, double yOffset) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        ImGui.getIO().addMouseWheelEvent((float) xOffset, (float) yOffset);
    }

    public static void onKey(@org.jetbrains.annotations.Nullable ImGuiRenderer renderer,
                             int key, int scancode, int glfwAction) {
        if (renderer == null || !renderer.isEnabled()) {
            return;
        }
        boolean down = glfwAction != GLFW.GLFW_RELEASE;
        ImGui.getIO().addKeyEvent(toImGuiKey(key), down);


        ImGui.getIO().setKeyEventNativeData(toImGuiKey(key), key, scancode);
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

    private static int toImGuiKey(int glfwKey) {


        return switch (glfwKey) {
            case GLFW.GLFW_KEY_TAB -> ImGuiKey.Tab;
            case GLFW.GLFW_KEY_LEFT -> ImGuiKey.LeftArrow;
            case GLFW.GLFW_KEY_RIGHT -> ImGuiKey.RightArrow;
            case GLFW.GLFW_KEY_UP -> ImGuiKey.UpArrow;
            case GLFW.GLFW_KEY_DOWN -> ImGuiKey.DownArrow;
            case GLFW.GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp;
            case GLFW.GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown;
            case GLFW.GLFW_KEY_HOME -> ImGuiKey.Home;
            case GLFW.GLFW_KEY_END -> ImGuiKey.End;
            case GLFW.GLFW_KEY_INSERT -> ImGuiKey.Insert;
            case GLFW.GLFW_KEY_DELETE -> ImGuiKey.Delete;
            case GLFW.GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace;
            case GLFW.GLFW_KEY_SPACE -> ImGuiKey.Space;
            case GLFW.GLFW_KEY_ENTER -> ImGuiKey.Enter;
            case GLFW.GLFW_KEY_ESCAPE -> ImGuiKey.Escape;
            case GLFW.GLFW_KEY_APOSTROPHE -> ImGuiKey.Apostrophe;
            case GLFW.GLFW_KEY_COMMA -> ImGuiKey.Comma;
            case GLFW.GLFW_KEY_MINUS -> ImGuiKey.Minus;
            case GLFW.GLFW_KEY_PERIOD -> ImGuiKey.Period;
            case GLFW.GLFW_KEY_SLASH -> ImGuiKey.Slash;
            case GLFW.GLFW_KEY_SEMICOLON -> ImGuiKey.Semicolon;
            case GLFW.GLFW_KEY_EQUAL -> ImGuiKey.Equal;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> ImGuiKey.LeftBracket;
            case GLFW.GLFW_KEY_BACKSLASH -> ImGuiKey.Backslash;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> ImGuiKey.RightBracket;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> ImGuiKey.GraveAccent;
            case GLFW.GLFW_KEY_CAPS_LOCK -> ImGuiKey.CapsLock;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> ImGuiKey.ScrollLock;
            case GLFW.GLFW_KEY_NUM_LOCK -> ImGuiKey.NumLock;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> ImGuiKey.PrintScreen;
            case GLFW.GLFW_KEY_PAUSE -> ImGuiKey.Pause;
            default -> {
                if (glfwKey >= GLFW.GLFW_KEY_KP_0 && glfwKey <= GLFW.GLFW_KEY_KP_9) {
                    yield ImGuiKey.Keypad0 + (glfwKey - GLFW.GLFW_KEY_KP_0);
                }
                if (glfwKey >= GLFW.GLFW_KEY_KP_DECIMAL && glfwKey <= GLFW.GLFW_KEY_KP_EQUAL) {
                    yield ImGuiKey.KeypadDecimal + (glfwKey - GLFW.GLFW_KEY_KP_DECIMAL);
                }
                if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) {
                    yield ImGuiKey._0 + (glfwKey - GLFW.GLFW_KEY_0);
                }
                if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) {
                    yield ImGuiKey.A + (glfwKey - GLFW.GLFW_KEY_A);
                }
                if (glfwKey >= GLFW.GLFW_KEY_F1 && glfwKey <= GLFW.GLFW_KEY_F12) {
                    yield ImGuiKey.F1 + (glfwKey - GLFW.GLFW_KEY_F1);
                }
                yield ImGuiKey.None;
            }
        };
    }

    }
