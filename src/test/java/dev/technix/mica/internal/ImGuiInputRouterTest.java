package dev.technix.mica.internal;

import com.mojang.blaze3d.platform.InputConstants;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseButton;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against the {@link InputConstants} of the Minecraft version being built: GLFW key codes
 * on 26.2, SDL scancodes on 26.3. The same expectations must hold for both.
 */
class ImGuiInputRouterTest {

    @Test
    void lettersDigitsAndFunctionKeysMapInOrder() {
        int[] letters = {InputConstants.KEY_A, InputConstants.KEY_M, InputConstants.KEY_Z};
        assertEquals(ImGuiKey.A, ImGuiInputRouter.toImGuiKey(letters[0]));
        assertEquals(ImGuiKey.A + 12, ImGuiInputRouter.toImGuiKey(letters[1]));
        assertEquals(ImGuiKey.A + 25, ImGuiInputRouter.toImGuiKey(letters[2]));
        assertEquals(ImGuiKey._0, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_0));
        assertEquals(ImGuiKey._0 + 9, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_9));
        assertEquals(ImGuiKey.Keypad0 + 7, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_NUMPAD7));
        assertEquals(ImGuiKey.F1 + 11, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_F12));
    }

    @Test
    void editingAndModifierKeysMap() {
        assertEquals(ImGuiKey.Enter, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_RETURN));
        assertEquals(ImGuiKey.Backspace, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_BACKSPACE));
        assertEquals(ImGuiKey.LeftArrow, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_LEFT));
        assertEquals(ImGuiKey.LeftCtrl, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_LCONTROL));
        assertEquals(ImGuiKey.RightShift, ImGuiInputRouter.toImGuiKey(InputConstants.KEY_RSHIFT));
        assertEquals(ImGuiKey.None, ImGuiInputRouter.toImGuiKey(-12345));
    }

    @Test
    void everyMappedMinecraftKeyIsDistinct() {
        int[] keys = {InputConstants.KEY_A, InputConstants.KEY_Z, InputConstants.KEY_0,
                InputConstants.KEY_9, InputConstants.KEY_NUMPAD0, InputConstants.KEY_F1,
                InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_ESCAPE,
                InputConstants.KEY_TAB, InputConstants.KEY_SPACE, InputConstants.KEY_LCONTROL};
        Set<Integer> imGuiKeys = new HashSet<>();
        for (int key : keys) {
            assertTrue(imGuiKeys.add(ImGuiInputRouter.toImGuiKey(key)), "duplicate mapping for " + key);
        }
    }

    @Test
    void mouseButtonsFollowImGuiOrder() {
        assertEquals(ImGuiMouseButton.Left, ImGuiInputRouter.toImGuiButton(InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals(ImGuiMouseButton.Right, ImGuiInputRouter.toImGuiButton(InputConstants.MOUSE_BUTTON_RIGHT));
        assertEquals(ImGuiMouseButton.Middle, ImGuiInputRouter.toImGuiButton(InputConstants.MOUSE_BUTTON_MIDDLE));
        assertEquals(-1, ImGuiInputRouter.toImGuiButton(-7));
    }
}
