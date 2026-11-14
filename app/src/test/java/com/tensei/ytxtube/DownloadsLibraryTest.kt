package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsLibraryTest {
    private fun e(key: String, title: String, channel: String = "Ch", size: Long = 0, dur: Long = 0, order: Int = 0) =
        DownloadEntry(key, title, channel, size, dur, order)

    private val list = listOf(
        e("a", "banana bread", "Cook", 300, 60, 0),
        e("b", "Apple pie", "cook", 100, 600, 1),
        e("c", "Kotlin flows", "Code", 900, 300, 2),
    )

    @Test
    fun search_needsEveryWordInTitleOrChannel() {
        assertTrue(DownloadsViewLogic.matches(list[2], "KOTLIN code"))
        assertFalse(DownloadsViewLogic.matches(list[2], "kotlin cook"))
        assertTrue(DownloadsViewLogic.matches(list[0], "   "))
        assertEquals(listOf("b", "a"), DownloadsViewLogic.filterAndSort(list, "cook", DownloadSort.Recent).map { it.key })
    }

    @Test
    fun sortModes() {
        fun keys(s: DownloadSort) = DownloadsViewLogic.filterAndSort(list, "", s).map { it.key }
        assertEquals(listOf("c", "b", "a"), keys(DownloadSort.Recent))
        assertEquals(listOf("b", "a", "c"), keys(DownloadSort.Title))
        // Channel: Code, then cook (case-insensitive) by title: Apple pie, banana bread.
        assertEquals(listOf("c", "b", "a"), keys(DownloadSort.Channel))
        assertEquals(listOf("c", "a", "b"), keys(DownloadSort.Largest))
        assertEquals(listOf("b", "c", "a"), keys(DownloadSort.Longest))
    }

    @Test
    fun grouping_totalsAndSummary() {
        val groups = DownloadsViewLogic.groupByChannel(list + e("d", "x", "", 0, 0, 3))
        assertEquals(listOf("Code", "Cook", "cook", "Unknown channel"), groups.map { it.first })
        assertEquals(1300L, DownloadsViewLogic.totalBytes(list))
        assertEquals(0L, DownloadsViewLogic.totalBytes(listOf(e("z", "t", size = -1))))
        assertEquals("1 download · 0 MB", DownloadsViewLogic.summary(1, 0))
        assertEquals("3 downloads · 1.0 MB", DownloadsViewLogic.summary(3, 1024 * 1024))
    }

    @Test
    fun fractionsSelectionAndParsing() {
        assertEquals(0f, DownloadsViewLogic.usedFraction(100, -1), 0f)
        assertEquals(0.25f, DownloadsViewLogic.usedFraction(100, 300), 0.0001f)
        assertEquals(0f, DownloadsViewLogic.usedFraction(0, 0), 0f)
        assertEquals(setOf("a"), DownloadsViewLogic.toggle(emptySet(), "a"))
        assertEquals(emptySet<String>(), DownloadsViewLogic.toggle(setOf("a"), "a"))
        assertEquals(setOf("a"), DownloadsViewLogic.retain(setOf("a", "gone"), listOf("a", "b")))
        assertEquals(DownloadSort.Largest, DownloadsViewLogic.parseSort("Largest"))
        assertEquals(DownloadSort.Recent, DownloadsViewLogic.parseSort("nope"))
        assertEquals(DownloadDensity.Compact, DownloadsViewLogic.parseDensity("Compact"))
        assertEquals(DownloadDensity.Comfortable, DownloadsViewLogic.parseDensity(null))
    }
}
