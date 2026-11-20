package io.github.the37thinsight.ytadskip

enum class Action { MUTE, UNMUTE, CLICK_SKIP }

/**
 * Pure decision logic, no Android types, so it can be unit-tested.
 * Only unmutes sound it muted itself (mutedByUs).
 */
class AdState(
    var unmuteDelayMs: Long = 1000,
    var staleMs: Long = 8000,
    private val clickGapMs: Long = 1500,
) {
    var mutedByUs = false
        private set
    private var userOverride = false
    private var mutedAt = 0L
    private var lastAdSeen = 0L
    private var lastClick = Long.MIN_VALUE / 2

    /** YouTube is in front and its screen was scanned. */
    fun onScan(now: Long, adVisible: Boolean, skipVisible: Boolean, streamAlreadyMuted: Boolean): List<Action> {
        val out = mutableListOf<Action>()
        // User turned the sound back on (volume key) while we held it muted: respect that for this ad.
        // Grace period: AudioManager may not report the mute on the very next scan.
        if (mutedByUs && !streamAlreadyMuted && now - mutedAt >= 1000) {
            mutedByUs = false
            userOverride = true
        }
        if (adVisible || skipVisible) {
            lastAdSeen = now
            if (!mutedByUs && !userOverride && !streamAlreadyMuted) {
                mutedByUs = true
                mutedAt = now
                out += Action.MUTE
            }
        } else if (now - lastAdSeen >= unmuteDelayMs) {
            userOverride = false
            if (mutedByUs) out += release()
        }
        if (skipVisible && now - lastClick >= clickGapMs) {
            lastClick = now
            out += Action.CLICK_SKIP
        }
        return out
    }

    /** The screen can't be read (screen off, notification shade). Unmute only once the ad signal is stale. */
    fun onNoView(now: Long): List<Action> =
        if (mutedByUs && now - lastAdSeen >= staleMs) listOf(release()) else emptyList()

    /** Another app is in front, or the service stops. */
    fun onLeft(): List<Action> = if (mutedByUs) listOf(release()) else emptyList()

    private fun release(): Action {
        mutedByUs = false
        return Action.UNMUTE
    }
}
