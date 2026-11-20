package io.github.the37thinsight.ytadskip

import org.json.JSONException
import org.json.JSONObject

data class Rules(
    val version: Int,
    val adTexts: List<String>,
    val skipTexts: List<String>,
    val adViewIds: List<String>,
    val skipViewIds: List<String>,
    val playerViewIds: List<String>,
    val unmuteDelayMs: Long,
    val staleMs: Long,
    val nightLevelPercent: Int = 8,
    val fadeDownMs: Long = 1500,
    val fadeUpMs: Long = 6000,
) {
    companion object {
        /** Returns null for anything unusable, so a broken push never replaces working rules. */
        fun parse(json: String): Rules? = try {
            val o = JSONObject(json)
            fun list(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return (0 until a.length()).mapNotNull { a.opt(it) as? String }.map { it.trim() }.filter { it.isNotEmpty() }
            }
            val r = Rules(
                version = o.getInt("version"),
                adTexts = list("adTexts"),
                skipTexts = list("skipTexts"),
                adViewIds = list("adViewIds"),
                skipViewIds = list("skipViewIds"),
                playerViewIds = list("playerViewIds"),
                unmuteDelayMs = o.optLong("unmuteDelayMs", 1000).coerceIn(0, 10_000),
                staleMs = o.optLong("staleMs", 8000).coerceIn(2_000, 60_000),
                nightLevelPercent = o.optInt("nightLevelPercent", 8).coerceIn(1, 50),
                fadeDownMs = o.optLong("fadeDownMs", 1500).coerceIn(0, 10_000),
                fadeUpMs = o.optLong("fadeUpMs", 6000).coerceIn(0, 30_000),
            )
            if ((r.adTexts + r.adViewIds).isEmpty() || (r.skipTexts + r.skipViewIds).isEmpty()) null else r
        } catch (e: JSONException) {
            null
        }
    }
}
