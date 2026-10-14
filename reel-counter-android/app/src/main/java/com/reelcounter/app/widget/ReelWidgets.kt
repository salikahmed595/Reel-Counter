package com.reelcounter.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.reelcounter.app.MainActivity
import com.reelcounter.app.R
import com.reelcounter.app.ReelCounterApp
import com.reelcounter.app.core.Format
import com.reelcounter.app.core.Localized
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.Coverage
import com.reelcounter.app.data.prefs.WidgetMetric
import com.reelcounter.app.data.prefs.WidgetTheme
import com.reelcounter.app.tracking.TrackingHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

enum class WidgetLayout { SMALL, MEDIUM, LARGE }

private data class Palette(val bg: Color, val ink: Color, val sub: Color, val track: Color, val fill: Color, val bar: Color)

private fun palette(theme: WidgetTheme) = when (theme) {
    WidgetTheme.SAGE -> Palette(Color(0xFFE0F0E5), Color(0xFF243A2D), Color(0xFF41654F), Color(0x99FFFFFF), Color(0xFF4F8A68), Color(0xFFB9D9C4))
    WidgetTheme.LAVENDER -> Palette(Color(0xFFEEE7FA), Color(0xFF2F2742), Color(0xFF5A4C78), Color(0x99FFFFFF), Color(0xFF78669D), Color(0xFFD3C7EA))
    WidgetTheme.IVORY -> Palette(Color(0xFFFFFFFF), Color(0xFF23302B), Color(0xFF52635A), Color(0xFFE9ECE8), Color(0xFF4F8A68), Color(0xFFD9ECE0))
    WidgetTheme.NIGHT -> Palette(Color(0xFF273C33), Color(0xFFECFFF0), Color(0xFFB9D6C3), Color(0x33FFFFFF), Color(0xFF8FD1A8), Color(0xFF4C6B5C))
}

abstract class ReelWidgetBase(private val layout: WidgetLayout?) : GlanceAppWidget() {
    override val sizeMode: SizeMode = if (layout == null) SizeMode.Exact else SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = runCatching { GlanceAppWidgetManager(context).getAppWidgetId(id) }.getOrNull()
        val data = WidgetDataLoader.load(context, appWidgetId)
        provideContent { WidgetContent(data, layout) }
    }
}

class SmallWidget : ReelWidgetBase(WidgetLayout.SMALL)
class MediumWidget : ReelWidgetBase(WidgetLayout.MEDIUM)
class LargeWidget : ReelWidgetBase(WidgetLayout.LARGE)
class CustomWidget : ReelWidgetBase(null)

abstract class ReelWidgetReceiverBase : GlanceAppWidgetReceiver() {
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val repo = (context.applicationContext as ReelCounterApp).container.prefsRepo
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { appWidgetIds.forEach { repo.deleteWidgetConfig(it) } }
    }
}

class SmallWidgetReceiver : ReelWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = SmallWidget() }
class MediumWidgetReceiver : ReelWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = MediumWidget() }
class LargeWidgetReceiver : ReelWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = LargeWidget() }
class CustomWidgetReceiver : ReelWidgetReceiverBase() { override val glanceAppWidget: GlanceAppWidget = CustomWidget() }

@Composable
private fun WidgetContent(data: WidgetData, fixed: WidgetLayout?) {
    val ctx = Localized.context(LocalContext.current)
    val size: DpSize = LocalSize.current
    val layout = fixed ?: when {
        size.width < 180.dp -> WidgetLayout.SMALL
        size.height >= 220.dp -> WidgetLayout.LARGE
        else -> WidgetLayout.MEDIUM
    }
    val p = palette(data.config.theme)
    val ink = ColorProvider(p.ink)
    val sub = ColorProvider(p.sub)
    val (value, label) = metricText(ctx, data)

    Column(
        modifier = GlanceModifier.fillMaxSize().background(p.bg).cornerRadius(24.dp).padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(ctx.getString(R.string.widget_brand), style = TextStyle(color = sub, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            if (layout != WidgetLayout.SMALL) {
                val scope = data.config.platform?.abbr ?: DateTimeFormatter.ofPattern("EEE d MMM").format(java.time.LocalDate.now())
                Text(scope.uppercase(), style = TextStyle(color = sub, fontSize = 10.sp, fontWeight = FontWeight.Bold))
            }
        }
        Text(value, style = TextStyle(color = ink, fontSize = if (layout == WidgetLayout.SMALL) 40.sp else 46.sp, fontWeight = FontWeight.Bold))
        Text(label, style = TextStyle(color = sub, fontSize = 11.sp, fontWeight = FontWeight.Medium))

        if (layout != WidgetLayout.SMALL) {
            data.progress?.let { prog ->
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = prog.coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                    color = ColorProvider(p.fill),
                    backgroundColor = ColorProvider(p.track),
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Text(ctx.getString(R.string.widget_pct_goal, Format.percent(prog)), style = TextStyle(color = sub, fontSize = 10.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(comparison(ctx, data), style = TextStyle(color = sub, fontSize = 10.sp))
                }
            }
            data.estimate?.let {
                Text(ctx.getString(R.string.widget_estimate_line, it.value.roundToInt()), style = TextStyle(color = sub, fontSize = 10.sp))
            }
        }

        if (layout == WidgetLayout.LARGE) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Platform.entries.forEachIndexed { i, pl ->
                    if (i > 0) Spacer(GlanceModifier.defaultWeight())
                    Text("${pl.abbr} ${data.perPlatform[pl] ?: 0}", style = TextStyle(color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            val max = (data.last7.maxOfOrNull { it.first } ?: 0).coerceAtLeast(1)
            Row(modifier = GlanceModifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.Bottom) {
                data.last7.forEachIndexed { i, (v, cov) ->
                    if (i > 0) Spacer(GlanceModifier.width(6.dp))
                    val h = if (cov == Coverage.NONE) 3 else (4 + 36f * v / max).roundToInt()
                    Box(
                        GlanceModifier.defaultWeight().height(h.dp).cornerRadius(4.dp)
                            .background(if (i == data.last7.lastIndex) p.fill else if (cov == Coverage.NONE) p.track else p.bar),
                    ) {}
                }
            }
        }

        Spacer(GlanceModifier.defaultWeight())
        Text(statusLine(ctx, data), style = TextStyle(color = sub, fontSize = 9.sp))
    }
}

private fun metricText(ctx: Context, d: WidgetData): Pair<String, String> = when (d.config.metric) {
    WidgetMetric.DETECTED_TODAY -> Format.number(d.detectedToday) to ctx.getString(R.string.widget_label_detected)
    WidgetMetric.REMAINING -> (d.remaining?.let { Format.number(it) } ?: "—") to ctx.getString(R.string.widget_label_remaining)
    WidgetMetric.FEED_TIME -> Format.duration(ctx, d.feedMs) to ctx.getString(R.string.widget_label_feed_time)
    WidgetMetric.LAST_7_DAYS -> Format.number(d.last7Total) to ctx.getString(R.string.widget_label_7d)
}

private fun comparison(ctx: Context, d: WidgetData): String {
    val y = d.yesterday ?: return ctx.getString(R.string.comparison_unavailable_short)
    return ctx.getString(R.string.widget_vs_yesterday, Format.number(y))
}

/** Never implies real-time: shows when this snapshot was rendered, or why tracking is not running. */
private fun statusLine(ctx: Context, d: WidgetData): String = when (d.health) {
    TrackingHealth.PERMISSION_OFF, TrackingHealth.CONSENT_NEEDED -> ctx.getString(R.string.widget_status_off)
    TrackingHealth.PAUSED -> ctx.getString(R.string.widget_status_paused)
    else -> ctx.getString(
        R.string.widget_updated_at,
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(d.updatedAtMs).atZone(ZoneId.systemDefault())),
    )
}
