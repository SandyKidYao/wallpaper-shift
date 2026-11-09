package com.wallpapershift.app.schedule

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri

/**
 * Background limits that Android skins add on top of stock Android. We send the user to the right page,
 * and read the result back where the phone reveals it ([detect]); elsewhere we can only remember that
 * the user said it's done ([confirm]).
 *
 * Verified on vivo OriginOS 6 (Android 16), where both steps are required for on-time switching:
 *  - [Step.Autostart] off: alarms fire, but the system won't start our killed process for them
 *    (broadcast history: "skip for auto start"), and the alarm chain stops.
 *  - [Step.BackgroundPower] at its default "智能控制": about a minute after leaving the foreground the app
 *    is frozen and its alarms are withheld ("Reason=frozen") until the app is opened again.
 *  - [Step.SleepStandby] 「睡眠待机优化」 (on by default): inside the sleep window vivo predicts for the user
 *    (e.g. 23:41–07:05), idle apps are frozen even with background power allowed, so a switch in the
 *    night or early morning (05:00, sunrise) is withheld the same way. Seen as nightly "Reason=frozen"
 *    removals of our alarm between 23:30 and 06:10 on a phone with both other steps done.
 */
class BackgroundSettings(private val context: Context) {
    enum class Step { Autostart, BackgroundPower, SleepStandby }

    private val prefs = context.getSharedPreferences("reliability", Context.MODE_PRIVATE)

    /** The steps this phone needs, each with the settings page that handles it. */
    fun steps(): Map<Step, Intent> = buildMap {
        autostartIntent()?.let { put(Step.Autostart, it) }
        // vivo guards its own power page with a system permission; app info → 电量 leads there.
        if (isVivo) {
            put(Step.BackgroundPower, appDetailsIntent())
            // Only the phone-wide switch exists; there is no per-app exemption from it.
            if (vivoSleepStandby() != null) put(Step.SleepStandby, vivoBatteryIntent())
        }
    }

    /**
     * Whether the phone reports [step] as done (for [Step.SleepStandby]: the optimization is off), or null
     * when it doesn't say. Read on every return to
     * the home screen, so a row disappears as soon as the user comes back from the settings page.
     *
     * On vivo (verified on OriginOS 6) 「自启动」 is a row in the permission manager's table,
     * currentstate 0 = allowed, 1 = blocked. 「后台耗电」 can't be read: the power app's providers need
     * system permissions, and neither the battery-optimization whitelist nor the standby bucket follows
     * it (the app stayed whitelisted and EXEMPTED under 「智能控制」), so that step stays user-confirmed.
     * 「睡眠待机优化」 is the system setting `power_sleep_mode_enabled` (1 = on, verified on OriginOS 6).
     */
    fun detect(step: Step): Boolean? = when {
        step == Step.Autostart && isVivo -> vivoAutostart()
        step == Step.SleepStandby && isVivo -> vivoSleepStandby()?.not()
        else -> null
    }

    fun isConfirmed(step: Step): Boolean = prefs.getBoolean(key(step), false)

    fun confirm(step: Step) = prefs.edit { putBoolean(key(step), true) }

    private fun key(step: Step) = "confirmed.${step.name}"

    private val isVivo get() = Build.MANUFACTURER.equals("vivo", ignoreCase = true)

    /** Null when the table can't be read (another OS version, or a permission) or has no row for us yet. */
    private fun vivoAutostart(): Boolean? = try {
        context.contentResolver.query(VIVO_AUTOSTART, arrayOf("currentstate"), "pkgname = ?", arrayOf(context.packageName), null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) == 0 else null }
    } catch (error: RuntimeException) {
        Log.w(TAG, "vivo autostart state unreadable", error)
        null
    }

    /** Whether vivo's 「睡眠待机优化」 is on; null on phones without the setting or when it can't be read. */
    private fun vivoSleepStandby(): Boolean? = try {
        Settings.System.getString(context.contentResolver, VIVO_SLEEP_STANDBY)?.toIntOrNull()?.let { it != 0 }
    } catch (error: SecurityException) {
        Log.w(TAG, "vivo sleep standby state unreadable", error)
        null
    }

    /** The switch sits under 电池 → 省电管理, a page with no entry of its own; the battery page is the closest. */
    private fun vivoBatteryIntent(): Intent = listOf(
        component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
        Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
    ).firstOrNull { context.packageManager.resolveActivity(it, 0)?.activityInfo?.exported == true }
        ?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ?: appDetailsIntent()

    private fun autostartIntent(): Intent? = AUTOSTART_PAGES.firstOrNull { candidate ->
        context.packageManager.resolveActivity(candidate, 0)?.activityInfo?.exported == true
    }?.let { Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

    private fun appDetailsIntent() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private companion object {
        const val TAG = "BackgroundSettings"
        const val VIVO_SLEEP_STANDBY = "power_sleep_mode_enabled"
        val VIVO_AUTOSTART = "content://com.vivo.permissionmanager.provider.permission/bg_start_up_apps".toUri()

        fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

        val AUTOSTART_PAGES = listOf(
            // vivo / iQOO — verified on OriginOS 6 (Android 16).
            Intent("com.iqoo.secure.BGSTARTUPMANAGER"),
            component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            // Entry points commonly used for other skins; not yet verified on a real device here.
            component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
            component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        )
    }
}
