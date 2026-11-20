package com.tensei.ytxtube

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceProfileLogicTest {
    private val sample = InterfaceProfile(
        name = "Night",
        accentHex = "#112233",
        homeCardStyle = FeedCardStyle.Grid.name,
        navIndicator = NavIndicatorStyle.GlowingDot.name,
        playerEngine = PlayerEngineMode.Style.name,
        playerStyle = PlayerStylePreset.OledPure.name,
        sheetRadiusDp = 34,
    )

    @Test fun jsonRoundTrips() {
        val back = InterfaceProfileLogic.fromJson(InterfaceProfileLogic.toJson(sample))
        assertTrue(back.errors.isEmpty())
        assertEquals(sample, back.profile)
    }

    @Test fun rejectsWrongTypeBadJsonAndMissingName() {
        assertNull(InterfaceProfileLogic.fromJson("{oops").profile)
        assertNull(InterfaceProfileLogic.fromJson("""{"type":"other","version":1,"name":"x"}""").profile)
        assertNull(InterfaceProfileLogic.fromJson("""{"type":"ytx-interface-profile","version":99,"name":"x"}""").profile)
        assertNull(InterfaceProfileLogic.fromJson("""{"type":"ytx-interface-profile","version":1,"name":"  "}""").profile)
    }

    @Test fun clampsNumbersAndReportsUnknownChoices() {
        val json = JSONObject(InterfaceProfileLogic.toJson(sample))
            .put("sheetRadiusDp", 500).put("playerBlurDp", -9).put("autoHideSeconds", 99)
            .put("homeCardStyle", "Hexagons").put("primaryGlowHex", "nope")
        val r = InterfaceProfileLogic.fromJson(json.toString())
        val p = r.profile!!
        assertEquals(42, p.sheetRadiusDp)
        assertEquals(0, p.playerBlurDp)
        assertEquals(10, p.autoHideSeconds)
        assertEquals(FeedCardStyle.Comfortable.name, p.homeCardStyle)
        assertEquals("#7C3AED", p.primaryGlowHex)
        assertTrue(r.notes.size >= 2)
    }

    @Test fun uniqueNameAddsNumbersIgnoringCase() {
        val list = listOf(sample, sample.copy(name = "night 2"))
        assertEquals("Day", InterfaceProfileLogic.uniqueName(list, " Day "))
        assertEquals("NIGHT 3", InterfaceProfileLogic.uniqueName(list, "NIGHT"))
        assertEquals("My profile", InterfaceProfileLogic.uniqueName(emptyList(), "   "))
    }

    @Test fun upsertReplacesSameNameAndCapsTheList() {
        val one = InterfaceProfileLogic.upsert(emptyList(), sample)!!
        val replaced = InterfaceProfileLogic.upsert(one, sample.copy(name = "NIGHT", sheetRadiusDp = 20))!!
        assertEquals(1, replaced.size)
        assertEquals(20, replaced.single().sheetRadiusDp)
        val full = (1..InterfaceProfileLogic.MAX_PROFILES).map { sample.copy(name = "p$it") }
        assertNull(InterfaceProfileLogic.upsert(full, sample.copy(name = "extra")))
        assertNotNull(InterfaceProfileLogic.upsert(full, sample.copy(name = "P3")))
    }

    @Test fun removeAndListRoundTrip() {
        val list = listOf(sample, sample.copy(name = "Day"))
        assertEquals(listOf("Day"), InterfaceProfileLogic.remove(list, "night").map { it.name })
        assertEquals(list, InterfaceProfileLogic.parseList(InterfaceProfileLogic.serializeList(list)))
        assertTrue(InterfaceProfileLogic.parseList("garbage").isEmpty())
        assertFalse(InterfaceProfileLogic.parseList(null).isNotEmpty())
    }
}
