package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class DownloadsExtrasTest {
    private fun item(key: String, size: Long, order: Int, watched: Boolean = false) = CleanupItem(key, "T $key", size, order, watched)
    private val mb = 1024L * 1024L
    private val items = listOf(
        item("a", 400 * mb, 0, watched = true),
        item("b", 900 * mb, 1),
        item("c", 100 * mb, 2, watched = true),
        item("d", 600 * mb, 3),
        item("zero", 0, 4, watched = true),
    )

    @Test
    fun watchedRemovesEveryWatchedDownloadWithSize() {
        assertEquals(listOf("a", "c"), DownloadCleanupLogic.plan(items, CleanupStrategy.Watched, 0).map { it.key })
        assertEquals(500 * mb, DownloadCleanupLogic.bytes(DownloadCleanupLogic.plan(items, CleanupStrategy.Watched, 0)))
    }

    @Test
    fun oldestAndLargestStopOnceTheTargetIsReached() {
        // Oldest first: a (400) + b (900) = 1300 >= 1000 stops there.
        assertEquals(listOf("a", "b"), DownloadCleanupLogic.plan(items, CleanupStrategy.Oldest, 1000 * mb).map { it.key })
        // Largest first: b (900) then d (600).
        assertEquals(listOf("b", "d"), DownloadCleanupLogic.plan(items, CleanupStrategy.Largest, 1000 * mb).map { it.key })
        // A target bigger than everything takes everything with a size.
        assertEquals(4, DownloadCleanupLogic.plan(items, CleanupStrategy.Oldest, 100_000 * mb).size)
        assertTrue(DownloadCleanupLogic.plan(items, CleanupStrategy.Oldest, 0).isEmpty())
        assertTrue(DownloadCleanupLogic.plan(emptyList(), CleanupStrategy.Largest, mb).isEmpty())
        assertEquals(1024L * mb, DownloadCleanupLogic.targetBytes(1024))
    }

    @Test
    fun shuffleKeepsEveryItemAndIsRepeatable() {
        val list = (1..20).toList()
        val a = DownloadPlayLogic.shuffled(list, 42L)
        assertEquals(list.toSet(), a.toSet())
        assertEquals(20, a.size)
        assertEquals(a, DownloadPlayLogic.shuffled(list, 42L))
    }

    @Test
    fun csvExportQuotesAndDefusesFormulas() {
        assertEquals("plain", WatchInsightsLogic.csvCell("plain"))
        assertEquals("\"a,b\"", WatchInsightsLogic.csvCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", WatchInsightsLogic.csvCell("say \"hi\""))
        assertEquals("'=SUM(A1)", WatchInsightsLogic.csvCell("=SUM(A1)"))
        val utc = TimeZone.getTimeZone("UTC")
        val s = WatchSession("vid1", "Title, with comma", "Chan", 1_700_000_000_000L, 90)
        val csv = WatchInsightsLogic.toCsv(listOf(s), utc)
        val lines = csv.trim().lines()
        assertEquals("date,time,title,channel,seconds,video_url", lines[0])
        assertEquals("2023-11-14,22:13,\"Title, with comma\",Chan,90,https://www.youtube.com/watch?v=vid1", lines[1])
        // Only the newest rows are kept, oldest first.
        val many = (1..5).map { WatchSession("v$it", "T$it", "C", it * 1000L, 5) }
        assertEquals(listOf("v4", "v5"), WatchInsightsLogic.toCsv(many, utc, maxRows = 2).trim().lines().drop(1).map { it.substringAfterLast("v=") })
    }
}
