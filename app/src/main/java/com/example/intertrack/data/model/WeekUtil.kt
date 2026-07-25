package com.example.intertrack.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Weekly internship period math (record-only; no time-zone-perfect calendar needed for a demo).
 * Week 1 = [start, start+6 days]; each following week is the next 7 days. All values in millis.
 */
object WeekUtil {
    const val DAY_MS = 86_400_000L
    const val WEEK_MS = 7 * DAY_MS

    /** Number of weekly report periods that cover the inclusive [start, end] range (min 1). */
    fun requiredReports(startMs: Long, endMs: Long): Int {
        if (endMs <= 0L || startMs <= 0L || endMs < startMs) return 0
        val days = ((endMs - startMs) / DAY_MS) + 1
        return Math.ceil(days / 7.0).toInt().coerceAtLeast(1)
    }

    /** 1-based week number that contains `now`; 0 when `now` is before the start. */
    fun currentWeek(startMs: Long, nowMs: Long): Int {
        if (startMs <= 0L || nowMs < startMs) return 0
        return ((nowMs - startMs) / WEEK_MS).toInt() + 1
    }

    fun periodStart(startMs: Long, week: Int): Long = startMs + (week - 1) * WEEK_MS
    /** Last day of the week (the 7th day), used as the display deadline. */
    fun periodEnd(startMs: Long, week: Int): Long = periodStart(startMs, week) + 6 * DAY_MS
    /** Exclusive end of the week (start of the next week) — used for late detection. */
    private fun weekEndExclusive(startMs: Long, week: Int): Long = periodStart(startMs, week) + WEEK_MS

    /** A submission for `week` is late once `now` reaches the next week. */
    fun isLate(startMs: Long, week: Int, nowMs: Long): Boolean = nowMs >= weekEndExclusive(startMs, week)

    fun formatDate(ms: Long?): String =
        if (ms != null && ms > 0L) SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(ms)) else "—"
}
