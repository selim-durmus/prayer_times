package com.tuttoposto.prayertimes.data.models

/**
 * What the countdown is measuring right now.
 * - [Phase.ENDS]: inside a prayer window, counting down to its end (e.g. Fajr → sunrise).
 * - [Phase.STARTS]: between windows, counting down to the next prayer's start.
 */
data class PrayerCountdown(
    val prayerName: String,
    val phase: Phase,
    val targetMillis: Long,
    val windowStartMillis: Long,
    val isFallback: Boolean
) {
    enum class Phase { ENDS, STARTS }

    fun remainingMillis(now: Long): Long = (targetMillis - now).coerceAtLeast(0)

    fun progress(now: Long): Float {
        val window = targetMillis - windowStartMillis
        if (window <= 0) return 0f
        return ((now - windowStartMillis).toFloat() / window).coerceIn(0f, 1f)
    }
}

/** Shared by the widgets and the Today screen so both always agree. */
object PrayerTimeline {
    private const val MINUTE = 60_000L

    fun countdown(days: List<PrayerTimesCache>, now: Long): PrayerCountdown? {
        val windows = days.flatMap { day -> day.prayers.map { it to day.isFallback } }
            .sortedBy { it.first.startTimeMillis }
        if (windows.isEmpty()) return null

        windows.firstOrNull { (p, _) -> now >= p.startTimeMillis && now < p.endTimeMillis }?.let { (p, fallback) ->
            return PrayerCountdown(p.name, PrayerCountdown.Phase.ENDS, p.endTimeMillis, p.startTimeMillis, fallback)
        }

        val (next, fallback) = windows.firstOrNull { it.first.startTimeMillis > now } ?: return null
        // Progress runs from the previous window's end (sunrise, Islamic midnight, ...), not its start.
        val previousEnd = windows.map { it.first.endTimeMillis }.filter { it <= now }.maxOrNull()
            ?: (next.startTimeMillis - 6 * 60 * MINUTE).coerceAtMost(now)
        return PrayerCountdown(next.name, PrayerCountdown.Phase.STARTS, next.startTimeMillis, previousEnd, fallback)
    }

    /**
     * Whole minutes, rounded up: with 2h 14m 30s left this shows "2h 15m", and it reaches "0m"
     * exactly at the prayer time instead of a minute early.
     */
    fun formatMinutes(remainingMillis: Long): String {
        val totalMinutes = (remainingMillis.coerceAtLeast(0) + MINUTE - 1) / MINUTE
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /** H:MM:SS (or M:SS under an hour), for the live in-app countdown. */
    fun formatSeconds(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0) + 999) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** The next wall-clock minute boundary, when a minute-resolution countdown changes. */
    fun nextMinuteBoundary(now: Long): Long = (now / MINUTE + 1) * MINUTE
}
