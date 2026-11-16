package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemeAccentLogicTest {
    @Test
    fun textColourFollowsAccentBrightness() {
        assertEquals(true, ThemeAccentLogic.onAccentIsLight("#7C4DFF"))   // dark-ish purple: white text
        assertEquals(false, ThemeAccentLogic.onAccentIsLight("#FFEB3B"))  // bright yellow: dark text
        assertNull(ThemeAccentLogic.onAccentIsLight("purple"))
    }

    @Test
    fun blendMixesTowardsTheAccent() {
        assertEquals("#000000", ThemeAccentLogic.blendHex("#000000", "#FFFFFF", 0f))
        assertEquals("#FFFFFF", ThemeAccentLogic.blendHex("#000000", "#FFFFFF", 1f))
        assertEquals("#7F7F7F", ThemeAccentLogic.blendHex("#000000", "#FFFFFF", 0.5f))
        assertEquals("#102030", ThemeAccentLogic.blendHex("#102030", "bad", 0.5f))
        assertEquals("#FFFFFF", ThemeAccentLogic.blendHex("#000000", "#FFFFFF", 9f))
    }

    @Test
    fun typedColoursAreCleanedOrRejected() {
        assertEquals("#7C4DFF", ThemeAccentLogic.normalizeHex(" 7c4dff "))
        assertEquals("#00E5FF", ThemeAccentLogic.normalizeHex("#00e5ff"))
        assertNull(ThemeAccentLogic.normalizeHex("#12345"))
        assertNull(ThemeAccentLogic.normalizeHex("#GGGGGG"))
        assertNull(ThemeAccentLogic.normalizeHex(""))
    }
}
