package com.tensei.ytxtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadPolicyTest {

    private fun task(id: String, status: DownloadStatus = DownloadStatus.Queued) = DownloadTask(
        videoId = id,
        title = id,
        channel = "",
        thumbnail = null,
        durationSec = 0L,
        height = 720,
        fileName = "$id.mp4",
        status = status,
    )

    @Test
    fun retryLimitHonoursTheSwitchAndIsBounded() {
        assertEquals(0, DownloadPolicy.retryLimit(false, 3))
        assertEquals(3, DownloadPolicy.retryLimit(true, 3))
        assertEquals(10, DownloadPolicy.retryLimit(true, 99))
        assertEquals(0, DownloadPolicy.retryLimit(true, -2))
    }

    @Test
    fun backoffDoublesAndIsCapped() {
        assertEquals(1_000L, DownloadPolicy.backoffMs(1))
        assertEquals(2_000L, DownloadPolicy.backoffMs(2))
        assertEquals(4_000L, DownloadPolicy.backoffMs(3))
        assertEquals(16_000L, DownloadPolicy.backoffMs(5))
        assertEquals(30_000L, DownloadPolicy.backoffMs(6))
        assertEquals(30_000L, DownloadPolicy.backoffMs(99))
        assertEquals(1_000L, DownloadPolicy.backoffMs(0))
    }

    @Test
    fun bytesNeededDoublesWhenMuxing() {
        val hundredMb = 100L * 1024 * 1024
        assertEquals(0L, DownloadPolicy.bytesNeeded(0L, false))
        assertEquals(0L, DownloadPolicy.bytesNeeded(-1L, true))
        assertEquals(hundredMb + DownloadPolicy.HEADROOM_BYTES, DownloadPolicy.bytesNeeded(hundredMb, false))
        assertEquals(hundredMb * 2 + DownloadPolicy.HEADROOM_BYTES, DownloadPolicy.bytesNeeded(hundredMb, true))
    }

    @Test
    fun storageShortfallOnlyReportsWhenItDoesNotFit() {
        assertNull(DownloadPolicy.storageShortfall(free = 500L, needed = 0L))
        assertNull(DownloadPolicy.storageShortfall(free = 500L, needed = 500L))
        val message = DownloadPolicy.storageShortfall(free = 10L * 1024 * 1024, needed = 200L * 1024 * 1024)
        assertNotNull(message)
        assertTrue(message!!.startsWith("Not enough storage"))
    }

    @Test
    fun bytesAreFormattedForHumans() {
        assertEquals("?", DownloadPolicy.formatBytes(-1L))
        assertEquals("100 KB", DownloadPolicy.formatBytes(100L * 1024))
        assertEquals("512 MB", DownloadPolicy.formatBytes(512L * 1024 * 1024))
        assertEquals("1.5 GB", DownloadPolicy.formatBytes(1536L * 1024 * 1024))
        assertEquals("2 MB/s", DownloadPolicy.formatSpeed(2L * 1024 * 1024))
    }

    @Test
    fun etaIsOnlyGivenWhenTheSpeedIsReliable() {
        assertNull(DownloadPolicy.etaSeconds(10L * 1024 * 1024, 0L))
        assertNull(DownloadPolicy.etaSeconds(10L * 1024 * 1024, 1_000L))
        assertNull(DownloadPolicy.etaSeconds(0L, 1024L * 1024))
        assertEquals(10L, DownloadPolicy.etaSeconds(10L * 1024 * 1024, 1024L * 1024))
    }

    @Test
    fun etaIsFormattedByMagnitude() {
        assertEquals("45s", DownloadPolicy.formatEta(45L))
        assertEquals("1m 15s", DownloadPolicy.formatEta(75L))
        assertEquals("1h 2m", DownloadPolicy.formatEta(3_720L))
    }

    @Test
    fun smoothedSpeedBlendsTowardsTheNewSample() {
        assertEquals(1_000L, DownloadPolicy.smoothSpeed(0L, 1_000L))
        assertEquals(1_300L, DownloadPolicy.smoothSpeed(1_000L, 2_000L))
    }

    @Test
    fun oldestQueuedTaskStartsFirst() {
        // New downloads are inserted at index 0, so "a" was queued first.
        val tasks = listOf(task("c"), task("b"), task("a"))
        assertEquals("a", DownloadPolicy.nextQueued(tasks) { 2 }?.videoId)
    }

    @Test
    fun higherPriorityJumpsTheQueue() {
        val tasks = listOf(task("c"), task("b"), task("a"))
        val next = DownloadPolicy.nextQueued(tasks) { if (it.videoId == "b") 3 else 2 }
        assertEquals("b", next?.videoId)
    }

    @Test
    fun onlyQueuedTasksAreCandidates() {
        val tasks = listOf(
            task("c", DownloadStatus.Paused),
            task("b", DownloadStatus.Downloading),
            task("a", DownloadStatus.Complete),
        )
        assertNull(DownloadPolicy.nextQueued(tasks) { 2 })
    }
}
