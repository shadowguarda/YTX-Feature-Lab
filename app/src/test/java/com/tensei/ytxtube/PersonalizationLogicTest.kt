package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PersonalizationLogicTest {
    @Test fun motionScalesAndNeverGoesNegative() {
        assertEquals(340, PersonalizationLogic.motionMs(500, 0.68f))
        assertEquals(0, PersonalizationLogic.motionMs(500, 0f))
        assertEquals(0, PersonalizationLogic.motionMs(500, -1f))
    }

    @Test fun defaultSheetIsOpaqueAndLowerValuesGetClearer() {
        assertEquals(1f, PersonalizationLogic.sheetContainerAlpha(0.68f), 0.001f)
        assertEquals(0f, PersonalizationLogic.sheetContainerAlpha(0.9f) - 1f, 0.001f)
        assertEquals(0.35f, PersonalizationLogic.sheetContainerAlpha(0.01f), 0.001f)
        assertEquals(0, PersonalizationLogic.transparencyPercent(0.68f))
    }

    @Test fun transparencyPercentRoundTrips() {
        for (pct in listOf(0, 20, 40, 65)) {
            val a = PersonalizationLogic.surfaceAlphaFromPercent(pct)
            assertEquals(pct, PersonalizationLogic.transparencyPercent(a))
        }
    }

    @Test fun cornerIsClamped() {
        assertEquals(12, PersonalizationLogic.sheetCorner(0))
        assertEquals(42, PersonalizationLogic.sheetCorner(99))
        assertEquals(28, PersonalizationLogic.sheetCorner(28))
    }

    @Test fun hexParsing() {
        assertEquals(0xFF7C3AED.toInt(), PersonalizationLogic.hexToArgb("#7c3aed"))
        assertEquals(0xFF7C3AED.toInt(), PersonalizationLogic.hexToArgb("7C3AED"))
        assertNull(PersonalizationLogic.hexToArgb("#12"))
        assertNull(PersonalizationLogic.hexToArgb("zzzzzz"))
    }
}
