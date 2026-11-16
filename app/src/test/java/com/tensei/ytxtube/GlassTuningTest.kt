package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassTuningTest {
    @Test
    fun untouchedStudioValuesChangeNothing() {
        assertEquals(26f, GlassTuning.scale(26f, GlassTuning.DEFAULT_SHEET_BLUR, GlassTuning.DEFAULT_SHEET_BLUR), 0.0001f)
        assertEquals(14f, GlassTuning.scale(14f, 14f, GlassTuning.DEFAULT_REFRACTION_HEIGHT), 0.0001f)
    }

    @Test
    fun slidersScaleEachSurfaceFromItsOwnDesign() {
        // Player blur slider doubled: a 12 dp control becomes 24 dp, a 20 dp one 40 dp.
        assertEquals(24f, GlassTuning.scale(12f, 40f, GlassTuning.DEFAULT_PLAYER_BLUR), 0.0001f)
        assertEquals(40f, GlassTuning.scale(20f, 40f, GlassTuning.DEFAULT_PLAYER_BLUR), 0.0001f)
        // Zero switches the effect off; a bad baseline leaves the value alone.
        assertEquals(0f, GlassTuning.scale(18f, 0f, 20f), 0f)
        assertEquals(18f, GlassTuning.scale(18f, 5f, 0f), 0.0001f)
        assertEquals(0f, GlassTuning.ratio(-3f, 10f), 0f)
    }

    @Test
    fun fillAndBorderOpacity() {
        assertEquals(0.38f, GlassTuning.fillAlpha(0.38f, dark = true, alpha = 1f), 0.0001f)
        assertEquals(0.34f, GlassTuning.fillAlpha(0.38f, dark = false, alpha = 1f), 0.0001f)
        assertEquals(0.2356f, GlassTuning.fillAlpha(0.38f, dark = true, alpha = 0.62f), 0.0001f)
        assertEquals(1f, GlassTuning.fillAlpha(0.78f, dark = true, alpha = 2f), 0f)
        assertEquals(0.16f, GlassTuning.borderAlpha(GlassTuning.DEFAULT_BORDER_ALPHA), 0.0001f)
    }
}

class PlayerLayoutVisibilityTest {
    @Test
    fun hiddenOnlyWhenSwitchedOff_andMissingSlotsStayVisible() {
        val standard = PlayerLayoutLogic.defaultSlots(ControlLayoutPreset.Standard)
        assertEquals(true, PlayerLayoutLogic.isVisible(standard, PlayerControlAction.SETTINGS))
        val hidden = standard.map { if (it.action == PlayerControlAction.PIP) it.copy(visible = false) else it }
        assertEquals(false, PlayerLayoutLogic.isVisible(hidden, PlayerControlAction.PIP))
        // Compact does not list picture-in-picture at all: that must not hide the button.
        assertEquals(true, PlayerLayoutLogic.isVisible(PlayerLayoutLogic.defaultSlots(ControlLayoutPreset.Compact), PlayerControlAction.PIP))
    }
}
