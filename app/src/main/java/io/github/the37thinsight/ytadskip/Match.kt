package io.github.the37thinsight.ytadskip

object Match {
    /**
     * True when the node's whole trimmed text starts with one of the patterns and the pattern
     * ends at a word boundary. "Hoppa över annonser" matches "Hoppa över"; "Advanced" does not
     * match "Ad"; "Annonsen var bra" does not match "Annons".
     */
    fun text(nodeText: CharSequence?, patterns: List<String>): Boolean {
        val t = nodeText?.toString()?.trim() ?: return false
        return patterns.any { p ->
            t.startsWith(p, ignoreCase = true) && (t.length == p.length || !t[p.length].isLetterOrDigit())
        }
    }

    /** rules.json may hold short ids ("skip_ad_button") or full ones ("pkg:id/x"). */
    fun fullViewId(pkg: String, id: String) = if (':' in id) id else "$pkg:id/$id"
}
