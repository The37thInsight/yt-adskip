package io.github.the37thinsight.ytadskip

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    companion object {
        const val PREFS = "settings"
        const val NIGHT_START = "22:00"
        const val NIGHT_END = "06:30"
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RulesStore.load(this)

        status = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, 32) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(status)
        }
        fun button(label: String, onClick: () -> Unit) =
            col.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { onClick() } })

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        col.addView(TextView(this).apply {
            text = "Nattläge: sänk volymen mjukt i stället för att stänga av (HH:MM, samma tid = av)"
            setPadding(0, 16, 0, 0)
        })
        val startField = EditText(this).apply { setText(prefs.getString("nightStart", NIGHT_START)); hint = "från" }
        val endField = EditText(this).apply { setText(prefs.getString("nightEnd", NIGHT_END)); hint = "till" }
        col.addView(LinearLayout(this).apply {
            addView(startField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(endField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        button("Spara nattläge") {
            val s = startField.text.toString().trim()
            val e = endField.text.toString().trim()
            if (Night.parse(s) == null || Night.parse(e) == null) {
                toast("Skriv tiderna som HH:MM, t.ex. 22:00")
            } else {
                prefs.edit().putString("nightStart", s).putString("nightEnd", e).apply()
                toast("Nattläge $s–$e sparat")
                render()
            }
        }

        button("Slå på tjänsten (Tillgänglighet)") { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        button("Appinfo → ⋮ → Tillåt begränsade inställningar") {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        button("Batterioptimering → Tillåt för appen") { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        button("Hämta regler nu") {
            toast("Hämtar…")
            RulesStore.refresh(this, force = true) { msg -> runOnUiThread { toast(msg); render() } }
        }
        button("Kopiera felsökningsdump") {
            val dump = AdSkipService.debugDump()
            if (dump.isEmpty()) {
                toast("Ingen dump än. Öppna YouTube (gärna under en reklam) och kom tillbaka.")
            } else {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("yt-adskip", dump))
                toast("Kopierad")
            }
        }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val r = RulesStore.current
        val at = RulesStore.fetchedAt(this).takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "aldrig"
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        status.text = buildString {
            appendLine("Tjänsten: ${if (AdSkipService.running) "PÅ" else "AV"}")
            val p = getSharedPreferences(PREFS, MODE_PRIVATE)
            appendLine("Nattläge: ${p.getString("nightStart", NIGHT_START)}–${p.getString("nightEnd", NIGHT_END)} (sänker mjukt, stänger inte av)")
            appendLine("Regler: v${r?.version}  (senaste hämtning: $at)")
            RulesStore.lastStatus?.let { appendLine(it) }
            appendLine("App: v$version")
            appendLine()
            appendLine("Är tjänsten gråmarkerad under Tillgänglighet? Öppna Appinfo, tryck ⋮ uppe till höger och välj \"Tillåt begränsade inställningar\". Försök sedan igen.")
        }
    }

    private fun open(i: Intent) = try { startActivity(i) } catch (e: Exception) { toast("Gick inte att öppna: ${e.message}") }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
