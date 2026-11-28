package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelSpeedLogicTest {
    private val eps = 0.0001f

    @Test
    fun cleanClampsAndSnapsToFiveHundredths() {
        assertEquals(1.25f, ChannelSpeedLogic.clean(1.26f)!!, eps)
        assertEquals(0.25f, ChannelSpeedLogic.clean(0.01f)!!, eps)
        assertEquals(4.0f, ChannelSpeedLogic.clean(9f)!!, eps)
        assertNull(ChannelSpeedLogic.clean(Float.NaN))
        assertNull(ChannelSpeedLogic.clean(Float.POSITIVE_INFINITY))
    }

    @Test
    fun resolvePrefersChannelThenDefaultThenOne() {
        assertEquals(1.5f, ChannelSpeedLogic.resolve(1.5f, 1f), eps)
        assertEquals(1.25f, ChannelSpeedLogic.resolve(null, 1.25f), eps)
        assertEquals(1f, ChannelSpeedLogic.resolve(Float.NaN, Float.NaN), eps)
    }

    @Test
    fun rememberStoresAndSpeedEqualToDefaultForgets() {
        val saved = ChannelSpeedLogic.remember(emptyMap(), "UC1", 1.5f)
        assertEquals(1.5f, saved["UC1"]!!, eps)
        val forgotten = ChannelSpeedLogic.remember(saved, "UC1", 1f)
        assertFalse(forgotten.containsKey("UC1"))
    }

    @Test
    fun rememberIgnoresBadIdsAndSpeeds() {
        val start = mapOf("UC1" to 1.5f)
        assertEquals(start, ChannelSpeedLogic.remember(start, "bad;id", 2f))
        assertEquals(start, ChannelSpeedLogic.remember(start, "", 2f))
        assertEquals(start, ChannelSpeedLogic.remember(start, "UC2", Float.NaN))
    }

    @Test
    fun oldestEntriesAreDroppedAndRecentOnesKept() {
        var map: Map<String, Float> = emptyMap()
        for (i in 1..5) map = ChannelSpeedLogic.remember(map, "UC$i", 1.5f, maxEntries = 3)
        assertEquals(listOf("UC3", "UC4", "UC5"), map.keys.toList())
        map = ChannelSpeedLogic.remember(map, "UC3", 2f, maxEntries = 3)
        assertEquals("UC3", map.keys.last())
    }

    @Test
    fun encodeDecodeRoundTripAndSkipsMalformed() {
        val map = linkedMapOf("UCa" to 1.5f, "UCb" to 0.75f)
        assertEquals(map, ChannelSpeedLogic.decode(ChannelSpeedLogic.encode(map)))
        val decoded = ChannelSpeedLogic.decode("UCa=1.5;junk;=2;UCb=abc;UCc=2")
        assertEquals(listOf("UCa", "UCc"), decoded.keys.toList())
        assertTrue(ChannelSpeedLogic.decode(null).isEmpty())
        assertTrue(ChannelSpeedLogic.decode("").isEmpty())
    }
}
