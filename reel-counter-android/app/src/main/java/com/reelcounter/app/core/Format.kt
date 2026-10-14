package com.reelcounter.app.core

import android.content.Context
import com.reelcounter.app.R
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    fun number(n: Int, locale: Locale = Locale.getDefault()): String = NumberFormat.getIntegerInstance(locale).format(n)

    fun duration(context: Context, ms: Long): String {
        val totalMin = (ms / 60_000L).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) context.getString(R.string.duration_h_m, h, m) else context.getString(R.string.duration_m, m)
    }

    fun percent(ratio: Float): Int = (ratio * 100f).roundToInt()

    fun approx(context: Context, v: Double): String = context.getString(R.string.approx_value, v.roundToInt())

    fun range(context: Context, low: Double?, high: Double?): String? =
        if (low == null || high == null) null else context.getString(R.string.estimate_range, low.roundToInt(), high.roundToInt())
}
