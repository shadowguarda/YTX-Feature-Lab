package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmartIslandLogicTest {
    @Test fun gesturesRoundTripAndKeepDefaultsForJunk() {
        val custom = SmartIslandLogic.defaultGestures().toMutableMap().apply { put(IslandGesture.Tap, IslandAction.Next) }
        assertEquals(custom, SmartIslandLogic.parseGestures(SmartIslandLogic.serializeGestures(custom)))
        assertEquals(SmartIslandLogic.defaultGestures(), SmartIslandLogic.parseGestures("nonsense,Tap:Nope"))
        assertEquals(SmartIslandLogic.defaultGestures(), SmartIslandLogic.parseGestures(null))
    }

    @Test fun directionPicksDominantAxisAndIgnoresShortDrags() {
        assertNull(SmartIslandLogic.direction(5f, 5f, 40f))
        assertEquals(IslandGesture.SwipeLeft, SmartIslandLogic.direction(-80f, 10f, 40f))
        assertEquals(IslandGesture.SwipeRight, SmartIslandLogic.direction(80f, -10f, 40f))
        assertEquals(IslandGesture.SwipeUp, SmartIslandLogic.direction(10f, -80f, 40f))
        assertEquals(IslandGesture.SwipeDown, SmartIslandLogic.direction(10f, 80f, 40f))
    }

    @Test fun animationLengthScalesWithSpeed() {
        assertEquals(300, SmartIslandLogic.animMs(300, IslandSpeed.Normal))
        assertEquals(0, SmartIslandLogic.animMs(300, IslandSpeed.Off))
    }

    @Test fun eventsTakeTurns() {
        assertNull(SmartIslandLogic.pickEvent(emptyList(), 3))
        val both = listOf(IslandKind.Media, IslandKind.Download)
        assertEquals(IslandKind.Media, SmartIslandLogic.pickEvent(both, 0))
        assertEquals(IslandKind.Download, SmartIslandLogic.pickEvent(both, 1))
        assertEquals(IslandKind.Download, SmartIslandLogic.pickEvent(both, -1))
    }

    @Test fun downloadSummaryCombinesRunningOnly() {
        assertNull(SmartIslandLogic.downloadSummary(listOf(DownloadSnap(false, 10, 100, 0))))
        val s = SmartIslandLogic.downloadSummary(
            listOf(DownloadSnap(true, 50, 100, 1000), DownloadSnap(true, 150, 300, 1000), DownloadSnap(false, 1, 1, 0)),
        )!!
        assertEquals(2, s.count)
        assertEquals(0.5f, s.fraction, 0.001f)
        assertEquals(2000L, s.speedBps)
    }
}
