package kr.safestop

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 월별 누적값을 "2026-10:drive_s" 같은 키로 SharedPreferences에 둔다
class Stats(private val prefs: SharedPreferences) {
    private fun key(kind: String, now: Long) = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(now)) + ":" + kind

    fun add(kind: String, n: Long = 1, now: Long = System.currentTimeMillis()) =
        prefs.edit().putLong(key(kind, now), get(kind, now) + n).apply()

    fun get(kind: String, now: Long = System.currentTimeMillis()) = prefs.getLong(key(kind, now), 0)

    fun summary(c: Copy, now: Long = System.currentTimeMillis()): String? {
        val driveMin = get(DRIVE_S, now) / 60
        if (driveMin == 0L && get(DETECT, now) == 0L) return null
        return c.month.format(driveMin / 60, driveMin % 60, get(BREAK, now), get(DETECT, now))
    }

    companion object {
        const val DRIVE_S = "drive_s"
        const val DETECT = "detect"
        const val BREAK = "break"
    }
}
