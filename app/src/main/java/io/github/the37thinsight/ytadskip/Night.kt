package io.github.the37thinsight.ytadskip

/** Night window: lower the volume gradually instead of muting. Times are minutes since midnight. */
object Night {
    fun parse(hhmm: String): Int? {
        val m = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(hhmm.trim()) ?: return null
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return if (h > 23 || min > 59) null else h * 60 + min
    }

    /** Handles windows that cross midnight (22:00–06:30). start == end means off. */
    fun isNight(minuteOfDay: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /** Volume index to duck to: a percentage of the stream's max, never 0 (silence is what wakes). */
    fun duckLevel(maxIndex: Int, percent: Int) = maxOf(1, maxIndex * percent / 100)
}
