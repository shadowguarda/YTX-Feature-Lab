package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BannerLogicTest {
    private fun b(id: Long, title: String = "t$id") = Banner(id, BannerKind.Info, title, "m")

    @Test fun duplicatesInsideTheWindowAreDropped() {
        val (l1, s1) = BannerLogic.add(emptyList(), b(1, "Same"), emptyMap(), 1000L)
        val (l2, s2) = BannerLogic.add(l1, b(2, "Same"), s1, 2500L)
        assertEquals(1, l2.size)
        val (l3, _) = BannerLogic.add(l2, b(3, "Same"), s2, 3200L)
        assertEquals(listOf(1L, 3L), l3.map { it.id })
    }

    @Test fun onlyTheNewestThreeStay() {
        var list = emptyList<Banner>()
        var stamps = emptyMap<String, Long>()
        for (i in 1L..5L) { val r = BannerLogic.add(list, b(i), stamps, i * 10_000L); list = r.first; stamps = r.second }
        assertEquals(listOf(3L, 4L, 5L), list.map { it.id })
        assertEquals(listOf(3L, 5L), BannerLogic.remove(list, 4L).map { it.id })
    }

    @Test fun downloadEventsOnlyForActiveToFinalTransitions() {
        val prev = mapOf(
            "a" to DownloadStatus.Downloading, "b" to DownloadStatus.Processing, "c" to DownloadStatus.Downloading,
            "d" to DownloadStatus.Complete, "e" to DownloadStatus.Paused,
        )
        val now = listOf(
            Triple("a", "A", DownloadStatus.Complete), Triple("b", "B", DownloadStatus.Failed), Triple("c", "C", DownloadStatus.Paused),
            Triple("d", "D", DownloadStatus.Complete), Triple("e", "E", DownloadStatus.Complete), Triple("new", "N", DownloadStatus.Complete),
        )
        val events = BannerLogic.downloadEvents(prev, now, mapOf("b" to "No space"))
        assertEquals(listOf("a", "b"), events.map { it.key })
        assertTrue(events[0].completed)
        assertEquals("No space", events[1].error)
    }

    @Test fun errorsStayLongerThanSuccess() {
        assertTrue(BannerKind.Error.showMs > BannerKind.Success.showMs)
    }
}
