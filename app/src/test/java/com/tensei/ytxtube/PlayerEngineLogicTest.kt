package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEngineLogicTest {
    @Test fun parseDefaultsToClassic() {
        assertEquals(PlayerEngineMode.Style, PlayerEngineLogic.parse("Style"))
        assertEquals(PlayerEngineMode.Classic, PlayerEngineLogic.parse("nope"))
        assertEquals(PlayerEngineMode.Classic, PlayerEngineLogic.parse(null))
    }

    @Test fun everyPriorityHasARecommendation() {
        for (p in PlayerPriority.entries) {
            val r = PlayerEngineLogic.recommend(p)
            assertTrue(r.reason.isNotBlank())
            if (r.mode == PlayerEngineMode.Classic) assertNull(r.style) else assertNotNull(r.style)
        }
        assertEquals(PlayerStylePreset.OledPure, PlayerEngineLogic.recommend(PlayerPriority.Battery).style)
        assertEquals(PlayerEngineMode.Classic, PlayerEngineLogic.recommend(PlayerPriority.Features).mode)
    }

    @Test fun summaryNamesTheLookOnlyForStyle() {
        assertEquals("YTX Classic", PlayerEngineLogic.summary(PlayerEngineMode.Classic, PlayerStylePreset.OledPure))
        assertEquals("YTX Style · OLED Pure", PlayerEngineLogic.summary(PlayerEngineMode.Style, PlayerStylePreset.OledPure))
    }

    @Test fun playersHaveNamesAndDescriptions() {
        for (m in PlayerEngineMode.entries) {
            assertTrue(m.title.isNotBlank() && m.tagline.isNotBlank() && m.description.length > 40)
        }
        assertEquals(10, PlayerStylePreset.entries.size)
    }
}
