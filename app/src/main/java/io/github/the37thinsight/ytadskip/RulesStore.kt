package io.github.the37thinsight.ytadskip

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/** Bundled rules, overridden by a newer valid rules.json fetched from the repo at most once a day. */
object RulesStore {
    private const val RULES_URL =
        "https://raw.githubusercontent.com/The37thInsight/yt-adskip/main/app/src/main/assets/rules.json"
    private const val DAY_MS = 24 * 3600 * 1000L
    private const val MAX_BYTES = 64 * 1024

    @Volatile var current: Rules? = null
        private set
    @Volatile var lastStatus: String? = null
        private set
    @Volatile private var fetching = false

    fun load(ctx: Context): Rules {
        current?.let { return it }
        val bundled = Rules.parse(ctx.assets.open("rules.json").bufferedReader().use { it.readText() })
            ?: error("bundled rules.json is invalid")
        val cached = cacheFile(ctx).takeIf { it.exists() }?.readText()?.let(Rules::parse)
        return (if (cached != null && cached.version >= bundled.version) cached else bundled).also { current = it }
    }

    fun fetchedAt(ctx: Context) = prefs(ctx).getLong("fetchedAt", 0)

    fun refresh(ctx: Context, force: Boolean, done: (String) -> Unit = {}) {
        val app = ctx.applicationContext
        load(app)
        if (fetching || (!force && System.currentTimeMillis() - fetchedAt(app) < DAY_MS)) return
        fetching = true
        thread(name = "rules-fetch") {
            val msg = try {
                val body = download()
                val r = Rules.parse(body) ?: error("ogiltig rules.json, behåller v${current?.version}")
                val cur = current
                if (cur != null && r.version < cur.version) error("v${r.version} är äldre än v${cur.version}")
                cacheFile(app).writeText(body)
                current = r
                "Regler v${r.version} hämtade"
            } catch (e: Exception) {
                "Kunde inte hämta regler: ${e.message}"
            } finally {
                prefs(app).edit().putLong("fetchedAt", System.currentTimeMillis()).apply()
                fetching = false
            }
            lastStatus = msg
            done(msg)
        }
    }

    private fun download(): String {
        val c = URL(RULES_URL).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 5000
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > MAX_BYTES) error("för stor fil")
            return bytes.decodeToString()
        } finally {
            c.disconnect()
        }
    }

    private fun cacheFile(ctx: Context) = File(ctx.filesDir, "rules.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rules", Context.MODE_PRIVATE)
}
