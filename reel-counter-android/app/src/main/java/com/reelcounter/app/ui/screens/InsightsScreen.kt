package com.reelcounter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.core.Format
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.nameRes
import com.reelcounter.app.data.Coverage
import com.reelcounter.app.data.DayStats
import com.reelcounter.app.data.EstimateRange
import com.reelcounter.app.data.StatsCalculator
import com.reelcounter.app.data.Streak
import com.reelcounter.app.data.db.TransitionInstant
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.Bar
import com.reelcounter.app.ui.components.BarChart
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.ChipRow
import com.reelcounter.app.ui.components.Donut
import com.reelcounter.app.ui.components.EmptyArt
import com.reelcounter.app.ui.components.IconSquareButton
import com.reelcounter.app.ui.components.MetricTile
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType
import com.reelcounter.app.ui.theme.platformColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class Period(val days: Int) { TODAY(1), WEEK(7), MONTH(30) }

@Composable
fun InsightsScreen(
    state: UiState,
    transitionsToday: List<TransitionInstant>,
    transitions30d: List<TransitionInstant>,
    onNavigate: (String) -> Unit,
    onTipSeen: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    var periodIdx by rememberSaveable { mutableIntStateOf(1) }
    var filterIdx by rememberSaveable { mutableIntStateOf(0) }
    val period = Period.entries[periodIdx]
    val filter: Platform? = if (filterIdx == 0) null else Platform.entries[filterIdx - 1]

    ScreenColumn {
        PageHeader(stringResource(R.string.insights_title), stringResource(R.string.insights_caption)) {
            IconSquareButton(ReelIcons.Info, stringResource(R.string.cd_about_data), { onNavigate(Routes.PRIVACY) })
        }
        if ("insights" !in state.prefs.tipsSeen) {
            com.reelcounter.app.ui.components.TipBanner(stringResource(R.string.tip_insights)) { onTipSeen("insights") }
        }
        if (!state.hasAnyData) {
            EmptyArt(ReelIcons.Chart)
            Text(stringResource(R.string.insights_empty), style = ReelType.caption, color = colors.muted, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            ReelButton(stringResource(R.string.check_tracking_status), { onNavigate(Routes.TRACKING) }, trailingArrow = true)
            return@ScreenColumn
        }
        ChipRow(listOf(stringResource(R.string.period_today), stringResource(R.string.period_7d), stringResource(R.string.period_30d)), periodIdx, { periodIdx = it })
        ChipRow(
            listOf(stringResource(R.string.filter_all)) + Platform.entries.map { it.abbr },
            filterIdx, { filterIdx = it },
            Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
        )

        val days = state.days
        val range = days.takeLast(period.days)
        val prior = days.dropLast(period.days).takeLast(period.days)
        val total = range.sumOf { it.detected(filter) }
        val priorAvailable = prior.size == period.days && prior.none { it.coverage == Coverage.NONE }
        val comparison = StatsCalculator.compare(total, if (priorAvailable) prior.sumOf { it.detected(filter) } else null, priorAvailable)
        val observedDays = range.count { it.coverage != Coverage.NONE }

        // Main chart card
        Card(Modifier.padding(top = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.hero_eyebrow), style = ReelType.smallBold, color = colors.muted)
                    Text(Format.number(total, locale), style = ReelType.metric, color = colors.ink)
                    Text(
                        stringResource(
                            when (period) {
                                Period.TODAY -> R.string.period_today_so_far
                                Period.WEEK -> R.string.period_past_7
                                Period.MONTH -> R.string.period_past_30
                            },
                        ),
                        style = ReelType.small, color = colors.muted,
                    )
                }
                Pill(comparisonText(comparison), if (comparison is com.reelcounter.app.data.Comparison.Delta) PillKind.OK else PillKind.NEUTRAL)
            }
            Spacer(Modifier.height(16.dp))
            if (period == Period.TODAY) {
                val zone = ZoneId.systemDefault()
                val buckets = IntArray(12)
                transitionsToday.filter { filter == null || it.platform == filter.key }.forEach {
                    val h = Instant.ofEpochMilli(it.occurredAtUtcMs).atZone(runCatching { ZoneId.of(it.zoneIdAtEvent) }.getOrDefault(zone)).hour
                    buckets[h / 2]++
                }
                val nowHour = java.time.LocalTime.now().hour
                BarChart(
                    buckets.mapIndexed { i, v -> Bar(v.toFloat(), if (i % 2 == 0) "${i * 2}" else "", highlight = i == nowHour / 2) },
                    description = stringResource(R.string.cd_hourly_chart),
                )
                Text(stringResource(R.string.hourly_caption), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, modifier = Modifier.padding(top = 6.dp))
            } else {
                val fmt = DateTimeFormatter.ofPattern(if (period == Period.WEEK) "EEEEE" else "d", locale)
                BarChart(
                    range.mapIndexed { i, d ->
                        Bar(
                            d.detected(filter).toFloat(),
                            if (period == Period.WEEK || i % 5 == 0 || i == range.lastIndex) d.date.format(fmt) else "",
                            noData = d.coverage == Coverage.NONE && d.detected(filter) == 0,
                            partial = d.coverage == Coverage.PARTIAL,
                            highlight = i == range.lastIndex,
                        )
                    },
                    description = stringResource(R.string.cd_daily_chart),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.coverage_line, observedDays, range.size),
                style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted,
            )
            if (period == Period.MONTH) Text(stringResource(R.string.retention_note), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint)
        }

        // Time
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val feed = range.sumOf { it.feedMs(filter) }
            MetricTile(
                Modifier.weight(1f), colors.mintSoft, ReelIcons.Clock, stringResource(R.string.feed_time_short),
                Format.duration(ctx, feed),
                stringResource(if (range.any { it.feedIncomplete }) R.string.feed_observed_incomplete else R.string.feed_observed),
            )
            val fg = if (period == Period.TODAY) state.system.appForegroundToday?.let { m -> if (filter == null) m.values.sum() else m[filter] ?: 0 } else if (period == Period.WEEK && filter == null) state.system.appForeground7d else null
            MetricTile(
                Modifier.weight(1f), colors.sky, ReelIcons.Smartphone, stringResource(R.string.app_usage_short),
                fg?.let { Format.duration(ctx, it) } ?: "—",
                stringResource(if (fg == null) R.string.app_time_unavailable_short else R.string.app_time_label_short),
            )
        }

        // Platform share
        if (filter == null) {
            SectionTitle(stringResource(R.string.platform_breakdown)) { Text(stringResource(R.string.detected_only), style = ReelType.smallBold, color = colors.muted) }
            Card {
                val counts = Platform.entries.associateWith { p -> range.sumOf { it.detected[p] ?: 0 } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Donut(Platform.entries.map { counts.getValue(it).toFloat() to platformColors(it, colors.dark).chart }, Format.number(total, locale))
                    Spacer(Modifier.width(17.dp))
                    Column(Modifier.weight(1f)) {
                        Platform.entries.forEach { p ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(platformColors(p, colors.dark).chart))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(p.nameRes()), style = ReelType.small, color = colors.muted, modifier = Modifier.weight(1f))
                                Text(if (total > 0) "${counts.getValue(p) * 100 / total}%" else "—", style = ReelType.smallBold, color = colors.ink)
                            }
                        }
                    }
                }
            }
        }

        // Sessions
        SectionTitle(stringResource(R.string.sessions_title))
        Card {
            val sessions = range.sumOf { d -> if (filter == null) d.sessions.values.sum() else d.sessions[filter] ?: 0 }
            val feed = range.sumOf { it.feedMs(filter) }
            val rate = StatsCalculator.sessionRate(total, feed)
            Row {
                StatCell(Modifier.weight(1f), Format.number(sessions), stringResource(R.string.sessions_count))
                StatCell(Modifier.weight(1f), if (sessions > 0) Format.number(total / sessions) else "—", stringResource(R.string.per_session))
                StatCell(Modifier.weight(1f), rate?.let { String.format(locale, "%.1f", it) } ?: "—", stringResource(R.string.per_feed_minute))
            }
            Text(stringResource(R.string.sessions_note), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, modifier = Modifier.padding(top = 10.dp))
        }

        // Estimates — kept visibly separate
        SectionTitle(stringResource(R.string.estimated_activity)) { Pill(stringResource(R.string.not_verified), PillKind.WARN) }
        Card {
            val est: EstimateRange? = range.mapNotNull { it.estimate(filter) }.reduceOrNull { a, b -> a + b }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(est?.let { Format.approx(ctx, it.value) } ?: "—", style = ReelType.sectionHeading.copy(fontSize = 31.sp), color = colors.ink)
                    Text(
                        if (est != null) (Format.range(ctx, est.low, est.high) ?: stringResource(R.string.estimate_gaps)) else stringResource(R.string.estimate_unavailable_long),
                        style = ReelType.small, color = colors.muted,
                    )
                }
                Box(Modifier.size(47.dp).clip(RoundedCornerShape(16.dp)).background(colors.lav), contentAlignment = Alignment.Center) {
                    Icon(ReelIcons.Activity, null, Modifier.size(22.dp), tint = colors.lavInk)
                }
            }
            Box(Modifier.fillMaxWidth().padding(vertical = 13.dp).height(1.dp).background(colors.line))
            Text(stringResource(R.string.estimate_explainer), style = ReelType.small, color = colors.muted)
        }

        // Streak (retained window only)
        SectionTitle(stringResource(R.string.streak_title))
        Card {
            val goal = if (filter == null) state.prefs.combinedGoalOrNull else state.prefs.goalFor(filter)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when (val s = StatsCalculator.underLimitStreak(days, goal, filter)) {
                        is Streak.Days -> {
                            Text(stringResource(R.string.streak_days, s.count) + " ✦", style = ReelType.metric.copy(fontSize = 26.sp), color = colors.ink)
                            Text(stringResource(R.string.streak_under_limit), style = ReelType.small, color = colors.muted)
                        }
                        Streak.Unverifiable -> {
                            Text(stringResource(R.string.streak_unverifiable), style = ReelType.label, color = colors.ink)
                            Text(stringResource(R.string.streak_unverifiable_desc), style = ReelType.small, color = colors.muted)
                        }
                        Streak.NoGoal -> Text(stringResource(R.string.streak_no_goal), style = ReelType.small, color = colors.muted)
                    }
                }
                Pill(stringResource(R.string.keep_going))
            }
            Text(stringResource(R.string.streak_note), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, modifier = Modifier.padding(top = 10.dp))
        }

        // Pro: deeper analysis. Free keeps all meaningful basics above.
        SectionTitle(stringResource(R.string.pro_insights_title)) { Pill(stringResource(R.string.pro_badge), PillKind.PRO) }
        if (state.isPro) {
            ProHourlyPattern(transitions30d, filter, days)
            Spacer(Modifier.height(12.dp))
            ProPeriodComparison(days)
        } else {
            Card(background = colors.lav, bordered = false) {
                Text(stringResource(R.string.pro_insights_locked_title), style = ReelType.label, color = colors.lavInk)
                Text(stringResource(R.string.pro_insights_locked_body), style = ReelType.small, color = colors.lavInk, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                ReelButton(stringResource(R.string.explore_pro), { onNavigate(Routes.PRO) }, kind = ButtonKind.GHOST, icon = ReelIcons.Sparkles)
            }
        }
    }
}

@Composable
private fun StatCell(modifier: Modifier, value: String, label: String) {
    Column(modifier) {
        Text(value, style = ReelType.metric.copy(fontSize = 22.sp), color = colors.ink)
        Text(label, style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted)
    }
}

@Composable
private fun ProHourlyPattern(all: List<TransitionInstant>, filter: Platform?, days: List<DayStats>) {
    val observed = days.count { it.coverage != Coverage.NONE }.coerceAtLeast(1)
    val zone = ZoneId.systemDefault()
    val hours = FloatArray(24)
    all.filter { filter == null || it.platform == filter.key }.forEach {
        hours[Instant.ofEpochMilli(it.occurredAtUtcMs).atZone(runCatching { ZoneId.of(it.zoneIdAtEvent) }.getOrDefault(zone)).hour] += 1f
    }
    val peak = hours.indices.maxByOrNull { hours[it] } ?: 0
    Card {
        Text(stringResource(R.string.pro_hourly_title), style = ReelType.label, color = colors.ink)
        Text(stringResource(R.string.pro_hourly_desc, observed), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(bottom = 12.dp))
        BarChart(
            (0 until 24).map { h -> Bar(hours[h] / observed, if (h % 6 == 0) "$h" else "", highlight = h == peak) },
            height = 100.dp, description = stringResource(R.string.cd_hourly_chart),
        )
        if (hours.sum() > 0) Text(stringResource(R.string.pro_peak_hour, peak, (peak + 1) % 24), style = ReelType.smallBold, color = colors.mintInk, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ProPeriodComparison(days: List<DayStats>) {
    val thisWeek = days.takeLast(7)
    val lastWeek = days.dropLast(7).takeLast(7)
    val comparable = lastWeek.size == 7 && lastWeek.none { it.coverage == Coverage.NONE }
    Card {
        Text(stringResource(R.string.pro_compare_title), style = ReelType.label, color = colors.ink)
        Text(stringResource(R.string.pro_compare_desc), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(bottom = 8.dp))
        Platform.entries.forEach { p ->
            val now = thisWeek.sumOf { it.detected[p] ?: 0 }
            val prev = lastWeek.sumOf { it.detected[p] ?: 0 }
            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(p.nameRes()), style = ReelType.small, color = colors.ink, modifier = Modifier.weight(1f))
                Text("${Format.number(prev)} → ${Format.number(now)}", style = ReelType.smallBold, color = colors.ink)
                Spacer(Modifier.width(10.dp))
                Pill(comparisonText(StatsCalculator.compare(now, prev, comparable)), PillKind.NEUTRAL)
            }
        }
    }
}
