package io.github.the37thinsight.ytadskip

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Turns the accessibility service on and off from the Quick Settings tile, so it can be off while
 * apps like BankID refuse to run next to an accessibility service. Writing the setting needs
 * WRITE_SECURE_SETTINGS, granted once over adb (see README).
 */
object ServiceSwitch {
    private const val KEY = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES

    fun contains(list: String?, comp: String): Boolean =
        list.orEmpty().split(':').any { it.equals(comp, ignoreCase = true) }

    fun withService(list: String?, comp: String, on: Boolean): String {
        val rest = list.orEmpty().split(':').filter { it.isNotEmpty() && !it.equals(comp, ignoreCase = true) }
        return (if (on) rest + comp else rest).joinToString(":")
    }

    private fun comp(ctx: Context) = ComponentName(ctx, AdSkipService::class.java).flattenToString()

    fun isOn(ctx: Context): Boolean = contains(Settings.Secure.getString(ctx.contentResolver, KEY), comp(ctx))

    /** Returns false when the permission is missing. */
    fun set(ctx: Context, on: Boolean): Boolean = try {
        val cr = ctx.contentResolver
        Settings.Secure.putString(cr, KEY, withService(Settings.Secure.getString(cr, KEY), comp(ctx), on))
        if (on) Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        true
    } catch (e: SecurityException) {
        false
    }
}
