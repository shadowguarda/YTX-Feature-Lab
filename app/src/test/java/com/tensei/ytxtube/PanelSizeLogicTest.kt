package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSizeLogicTest {
    @Test fun clampKeepsSizesInRange() {
        assertEquals(PanelSizeLogic.MIN_W, PanelSizeLogic.clampW(0.1f), 0f)
        assertEquals(PanelSizeLogic.MAX_W, PanelSizeLogic.clampW(3f), 0f)
        assertEquals(PanelSizeLogic.MIN_H, PanelSizeLogic.clampH(0f), 0f)
        assertEquals(PanelSizeLogic.MAX_H, PanelSizeLogic.clampH(2f), 0f)
    }

    @Test fun draggingRightAndUpGrowsThePanel() {
        val (w, h) = PanelSizeLogic.resize(0.7f, 0.5f, dxPx = 50f, dyPx = -100f, screenWPx = 1000f, screenHPx = 2000f)
        assertEquals(0.8f, w, 0.001f)
        assertEquals(0.55f, h, 0.001f)
        val (w2, h2) = PanelSizeLogic.resize(0.7f, 0.5f, dxPx = -50f, dyPx = 100f, screenWPx = 1000f, screenHPx = 2000f)
        assertEquals(0.6f.coerceAtLeast(PanelSizeLogic.MIN_W), w2, 0.001f)
        assertEquals(0.45f, h2, 0.001f)
    }

    @Test fun resizeNeverLeavesTheLimitsOrDividesByZero() {
        assertEquals(1f to PanelSizeLogic.MAX_H, PanelSizeLogic.resize(1f, 0.9f, 9999f, -9999f, 1000f, 2000f))
        assertEquals(0.5f to 0.5f, PanelSizeLogic.resize(0.5f, 0.5f, 10f, 10f, 0f, 0f))
    }

    @Test fun tapCyclesThroughThePresets() {
        val p = PanelSizeLogic.PRESETS
        assertEquals(p[1], PanelSizeLogic.nextPreset(p[0].first, p[0].second))
        assertEquals(p[2], PanelSizeLogic.nextPreset(p[1].first, p[1].second))
        assertEquals(p[0], PanelSizeLogic.nextPreset(p[2].first, p[2].second))
        assertEquals(p[1], PanelSizeLogic.nextPreset(0.79f, 0.52f))
    }
}
