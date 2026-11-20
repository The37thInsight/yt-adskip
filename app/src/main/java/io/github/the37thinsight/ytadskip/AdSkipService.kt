package io.github.the37thinsight.ytadskip

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.DateFormat
import java.util.Date

class AdSkipService : AccessibilityService() {

    companion object {
        const val YT = "com.google.android.youtube"
        private const val TAG = "YtAdSkip" // only state changes and clicks, never screen contents
        private const val TICK_MS = 500L
        private const val SNAPSHOT_EVERY_MS = 2000L
        private const val SNAPSHOT_MAX_LINES = 300

        @Volatile var running = false
            private set

        // Last few YouTube screens, in memory only, for the "copy debug dump" button.
        private val snapshots = ArrayDeque<String>()
        fun debugDump(): String = synchronized(snapshots) { snapshots.joinToString("\n") }
    }

    private lateinit var audio: AudioManager
    private val state = AdState()
    private val handler = Handler(Looper.getMainLooper())
    private var lastSnapshotAt = 0L

    // While we hold the sound muted, rescan actively: an ad may sit still without firing events.
    private val tick = object : Runnable {
        override fun run() {
            rescan()
            if (state.mutedByUs) handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onServiceConnected() {
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        RulesStore.load(this)
        RulesStore.refresh(this, force = false)
        running = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != YT) return // other apps: not read
        rescan()
    }

    private fun rescan() {
        val now = SystemClock.uptimeMillis()
        val root = rootInActiveWindow
        val actions = when (root?.packageName?.toString()) {
            YT -> scan(root, now)
            null, "com.android.systemui" -> state.onNoView(now)
            else -> state.onLeft()
        }
        apply(actions)
    }

    private fun scan(root: AccessibilityNodeInfo, now: Long): List<Action> {
        val r = RulesStore.current ?: return emptyList()
        state.unmuteDelayMs = r.unmuteDelayMs
        state.staleMs = r.staleMs

        // With playerViewIds set, only the player counts: "Sponsrad" cards in the feed must not mute videos.
        // No player on screen (feed, mini-player) = no ad.
        val scope = if (r.playerViewIds.isEmpty()) root else r.playerViewIds.firstNotNullOfOrNull { id ->
            root.findAccessibilityNodeInfosByViewId(Match.fullViewId(YT, id)).firstOrNull()
        }
        val ad = scope?.let { find(it, r.adViewIds, r.adTexts) }
        val skip = scope?.let { find(it, r.skipViewIds, r.skipTexts) }

        if (now - lastSnapshotAt >= SNAPSHOT_EVERY_MS) {
            lastSnapshotAt = now
            snapshot(root, ad != null, skip != null)
        }

        val actions = state.onScan(now, ad != null, skip != null, isSoundReduced())
        if (!state.mutedByUs && duckTarget >= 0 && Action.UNMUTE !in actions) {
            Log.i(TAG, "USER TOOK OVER volume")   // user changed the volume mid-ad: forget our duck
            duckTarget = -1
            savedVolume = -1
        }
        if (Action.CLICK_SKIP in actions && skip != null) click(skip)
        return actions
    }

    private fun find(scope: AccessibilityNodeInfo, ids: List<String>, texts: List<String>): AccessibilityNodeInfo? {
        for (id in ids) {
            scope.findAccessibilityNodeInfosByViewId(Match.fullViewId(YT, id))
                .firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        for (t in texts) {
            scope.findAccessibilityNodeInfosByText(t).firstOrNull {
                it.isVisibleToUser && (Match.text(it.text, texts) || Match.text(it.contentDescription, texts))
            }?.let { return it }
        }
        return null
    }

    private fun apply(actions: List<Action>) {
        for (a in actions) when (a) {
            Action.MUTE -> {
                reduce()
                handler.removeCallbacks(tick)
                handler.postDelayed(tick, TICK_MS)
            }
            Action.UNMUTE -> {
                restore()
                handler.removeCallbacks(tick)
            }
            Action.CLICK_SKIP -> Unit // done in scan, where the node is at hand
        }
    }

    // --- Day: mute. Night: fade down to a low level, later fade back up to the saved volume. ---
    private val music = AudioManager.STREAM_MUSIC
    private var duckTarget = -1      // >= 0 while ducked at night
    private var savedVolume = -1     // volume to return to after a duck
    private var fadingDown = false
    private var fadeUpTarget = -1    // >= 0 while fading back up
    private var fade: Runnable? = null

    private fun isNightNow(): Boolean {
        val p = getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val start = Night.parse(p.getString("nightStart", MainActivity.NIGHT_START)!!) ?: return false
        val end = Night.parse(p.getString("nightEnd", MainActivity.NIGHT_END)!!) ?: return false
        val c = java.util.Calendar.getInstance()
        return Night.isNight(c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE), start, end)
    }

    private fun reduce() {
        val r = RulesStore.current ?: return
        if (!isNightNow()) {
            Log.i(TAG, "MUTE")
            audio.adjustStreamVolume(music, AudioManager.ADJUST_MUTE, 0)
            return
        }
        // A new ad during fade-up: go back down, but remember the original volume, not the half-way one.
        if (savedVolume < 0) savedVolume = if (fadeUpTarget >= 0) fadeUpTarget else audio.getStreamVolume(music)
        duckTarget = Night.duckLevel(audio.getStreamMaxVolume(music), r.nightLevelPercent)
        Log.i(TAG, "DUCK $savedVolume -> $duckTarget")
        if (audio.getStreamVolume(music) > duckTarget) fadeTo(duckTarget, r.fadeDownMs, up = false)
    }

    private fun restore() {
        if (duckTarget < 0) {
            Log.i(TAG, "UNMUTE")
            audio.adjustStreamVolume(music, AudioManager.ADJUST_UNMUTE, 0)
            return
        }
        val target = savedVolume
        duckTarget = -1
        savedVolume = -1
        Log.i(TAG, "FADE UP -> $target")
        fadeTo(target, RulesStore.current?.fadeUpMs ?: 6000, up = true)
    }

    private fun fadeTo(target: Int, ms: Long, up: Boolean) {
        fade?.let { handler.removeCallbacks(it) }
        val start = audio.getStreamVolume(music)
        if (start == target) return endFade()
        fadingDown = !up
        fadeUpTarget = if (up) target else -1
        val dir = if (target > start) 1 else -1
        val stepMs = (ms / Math.abs(target - start)).coerceAtLeast(30)
        fade = object : Runnable {
            var v = start
            override fun run() {
                v += dir
                audio.setStreamVolume(music, v, 0)
                if (v != target) handler.postDelayed(this, stepMs) else endFade()
            }
        }.also { handler.post(it) }
    }

    private fun endFade() {
        fade?.let { handler.removeCallbacks(it) }
        fade = null
        fadingDown = false
        fadeUpTarget = -1
    }

    /** Is the sound currently lowered (by us or by the user)? Raising the volume mid-ad makes this false = override. */
    private fun isSoundReduced(): Boolean {
        if (fadingDown) return true
        if (audio.isStreamMute(music) || audio.getStreamVolume(music) == 0) return true
        return duckTarget >= 0 && audio.getStreamVolume(music) <= duckTarget
    }

    private fun click(node: AccessibilityNodeInfo) {
        val label = "'${node.text ?: node.contentDescription}' id=${node.viewIdResourceName}"
        var n: AccessibilityNodeInfo? = node
        while (n != null && !n.isClickable) n = n.parent
        if (n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
            Log.i(TAG, "CLICK action $label")
            return
        }
        Log.i(TAG, "CLICK gesture $label")
        // Fallback: a real tap on the button's centre.
        val b = Rect().also { node.getBoundsInScreen(it) }
        if (b.isEmpty) return
        val path = Path().apply { moveTo(b.exactCenterX(), b.exactCenterY()) }
        dispatchGesture(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build(),
            null, null,
        )
    }

    private fun snapshot(root: AccessibilityNodeInfo, ad: Boolean, skip: Boolean) {
        val sb = StringBuilder("--- ${DateFormat.getTimeInstance().format(Date())}  reklam=$ad  hoppa=$skip  regler=v${RulesStore.current?.version}\n")
        var lines = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || lines >= SNAPSHOT_MAX_LINES || depth > 40) return
            val id = n.viewIdResourceName?.substringAfter(":id/")
            val text = (n.text ?: n.contentDescription)?.toString()?.replace('\n', ' ')
            if (id != null || !text.isNullOrBlank()) {
                val b = Rect().also { n.getBoundsInScreen(it) }
                sb.append("  ".repeat(minOf(depth, 12))).append(id ?: "-").append(" | ")
                    .append(text?.take(60) ?: "").append(" | ").append(b.toShortString())
                    .append(if (n.isClickable) " | klickbar" else "").append('\n')
                lines++
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        synchronized(snapshots) {
            snapshots.addLast(sb.toString())
            while (snapshots.size > 3) snapshots.removeFirst()
        }
    }

    private fun stop() {
        apply(state.onLeft())
        // The service is going away: finish any fade-up at once instead of leaving the volume low.
        if (fadeUpTarget >= 0) audio.setStreamVolume(music, fadeUpTarget, 0)
        endFade()
        handler.removeCallbacks(tick)
        running = false
    }

    override fun onInterrupt() = apply(state.onLeft())

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }
}
