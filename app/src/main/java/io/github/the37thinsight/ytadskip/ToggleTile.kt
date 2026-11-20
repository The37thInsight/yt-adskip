package io.github.the37thinsight.ytadskip

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/** Quick Settings tile: tap to turn YT Reklamtyst on or off. */
class ToggleTile : TileService() {
    override fun onStartListening() = show(ServiceSwitch.isOn(this))

    override fun onClick() {
        val on = !ServiceSwitch.isOn(this)
        if (ServiceSwitch.set(this, on)) {
            show(on)
        } else {
            // No WRITE_SECURE_SETTINGS: fall back to the system screen.
            Toast.makeText(this, "Saknar behörighet, slå av/på under Tillgänglighet", Toast.LENGTH_LONG).show()
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(i)
            }
        }
    }

    private fun show(on: Boolean) {
        val t = qsTile ?: return
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "På" else "Av"
        t.updateTile()
    }
}
