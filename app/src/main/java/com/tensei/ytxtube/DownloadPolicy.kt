package com.tensei.ytxtube

import android.os.StatFs
import java.io.File
import java.util.Locale

/**
 * Small, side-effect-free rules the download engine consults.
 *
 * They live apart from [Downloads] so the decisions (retry limit, back-off, queue order, storage
 * headroom, wording) can be unit tested on the JVM without a device.
 */
internal object DownloadPolicy {
    /** Room kept free beyond the file itself, so a nearly-full phone is not pushed to zero. */
    const val HEADROOM_BYTES = 64L * 1024 * 1024

    private const val MAX_BACKOFF_MS = 30_000L
    private const val MIN_RELIABLE_SPEED_BPS = 16L * 1024

    /** Automatic retries a task gets, honouring the "auto retry" switch and the configured count. */
    fun retryLimit(enabled: Boolean, configured: Int): Int =
        if (enabled) configured.coerceIn(0, 10) else 0

    /** Exponential back-off: 1s, 2s, 4s ... capped at 30s. [attempt] starts at 1. */
    fun backoffMs(attempt: Int): Long {
        val shift = (attempt - 1).coerceIn(0, 5)
        return (1_000L shl shift).coerceAtMost(MAX_BACKOFF_MS)
    }

    /** Disk space a task needs: the parts, plus the merged file when video and audio are muxed. */
    fun bytesNeeded(totalBytes: Long, needsMux: Boolean): Long {
        if (totalBytes <= 0L) return 0L
        return (if (needsMux) totalBytes * 2 else totalBytes) + HEADROOM_BYTES
    }

    /** A readable message when [needed] does not fit in [free], otherwise null. */
    fun storageShortfall(free: Long, needed: Long): String? {
        if (needed <= 0L || free >= needed) return null
        return "Not enough storage: this needs ${formatBytes(needed)} but only " +
            "${formatBytes(free)} is free. Free up some space, then tap retry."
    }

    /** Free bytes on the volume holding [dir]; unknown volumes report "plenty" rather than block. */
    fun freeBytes(dir: File): Long {
        var probe: File? = dir
        while (probe != null && !probe.exists()) probe = probe.parentFile
        if (probe == null) return Long.MAX_VALUE
        return runCatching { StatFs(probe.path).availableBytes }.getOrDefault(Long.MAX_VALUE)
    }

    /**
     * Next task to start: highest priority first, and within a priority the one that has waited
     * longest. [tasks] is newest-first because new downloads are inserted at index 0.
     */
    fun nextQueued(tasks: List<DownloadTask>, weightOf: (DownloadTask) -> Int): DownloadTask? =
        tasks.asReversed().filter { it.status == DownloadStatus.Queued }.maxByOrNull(weightOf)

    /** Smooths the instantaneous speed so the readout does not jitter. */
    fun smoothSpeed(previous: Long, instant: Long): Long =
        if (previous <= 0L) instant else (previous * 7 + instant * 3) / 10

    /** Seconds remaining, or null when the speed is too low to give an honest estimate. */
    fun etaSeconds(remainingBytes: Long, speedBps: Long): Long? {
        if (remainingBytes <= 0L || speedBps < MIN_RELIABLE_SPEED_BPS) return null
        return remainingBytes / speedBps
    }

    fun formatEta(seconds: Long): String = when {
        seconds < 60L -> "${seconds}s"
        seconds < 3_600L -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3_600}h ${(seconds % 3_600) / 60}m"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "?"
        val mb = bytes / (1024.0 * 1024.0)
        return when {
            mb >= 1024.0 -> String.format(Locale.US, "%.1f GB", mb / 1024.0)
            mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        }
    }

    fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"
}
