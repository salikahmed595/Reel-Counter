package com.reelcounter.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.core.Format
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.nameRes
import com.reelcounter.app.data.Comparison
import com.reelcounter.app.data.Coverage
import com.reelcounter.app.data.StatsCalculator
import com.reelcounter.app.tracking.PlatformStatus
import com.reelcounter.app.tracking.TrackingHealth
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.Bar
import com.reelcounter.app.ui.components.BarChart
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.FooterCaption
import com.reelcounter.app.ui.components.GoalBar
import com.reelcounter.app.ui.components.IconSquareButton
import com.reelcounter.app.ui.components.LinkButton
import com.reelcounter.app.ui.components.LiveDot
import com.reelcounter.app.ui.components.MetricTile
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.PlatformBadge
import com.reelcounter.app.ui.components.ProgressRing
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ReelSwitch
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.SettingRow
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun healthLabel(h: TrackingHealth): String = stringResource(
    when (h) {
        TrackingHealth.ACTIVE -> R.string.health_active
        TrackingHealth.PAUSED -> R.string.health_paused
        TrackingHealth.LIMITED -> R.string.health_limited
        TrackingHealth.PERMISSION_OFF -> R.string.health_permission_off
        TrackingHealth.DETECTION_ISSUE -> R.string.health_detection_issue
        TrackingHealth.CONSENT_NEEDED -> R.string.health_consent_needed
    },
)

@Composable
fun healthColor(h: TrackingHealth): Color = when (h) {
    TrackingHealth.ACTIVE -> Color(0xFF4FA572)
    TrackingHealth.LIMITED -> Color(0xFFC9A13B)
    TrackingHealth.PAUSED -> Color(0xFF8E9C94)
    else -> Color(0xFFD9785A)
}

@Composable
fun comparisonText(c: Comparison): String = when (c) {
    is Comparison.Delta -> {
        val pct = (abs(c.ratio) * 100).roundToInt()
        if (c.ratio <= 0) stringResource(R.string.delta_fewer, pct) else stringResource(R.string.delta_more, pct)
    }
    Comparison.NoComparableData -> stringResource(R.string.comparison_no_data)
    Comparison.Unavailable -> stringResource(R.string.comparison_unavailable)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    onNavigate: (String) -> Unit,
    onGlobalPause: (Boolean) -> Unit,
    onPlatformPause: (Platform, Boolean) -> Unit,
    onQuickGoal: (Int) -> Unit = {},
    onDismissGuide: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val today = state.today
    val prefs = state.prefs
    var showPause by remember { mutableStateOf(false) }
    var showEstimateInfo by remember { mutableStateOf(false) }
    var showGoalPicker by remember { mutableStateOf(false) }
    val guideVisible = !prefs.guideDismissed

    ScreenColumn {
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    (today?.date ?: java.time.LocalDate.now()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d", locale)) + "  ✦",
                    style = ReelType.caption, color = colors.muted,
                )
                Text(stringResource(R.string.home_headline), style = ReelType.headline.copy(fontSize = 26.sp, lineHeight = 30.sp), color = colors.ink, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).semantics { heading() })
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { onNavigate(Routes.TRACKING) }.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LiveDot(healthColor(state.health))
                    Spacer(Modifier.width(6.dp))
                    Text(healthLabel(state.health), style = ReelType.smallBold, color = colors.muted)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconSquareButton(ReelIcons.Pause, stringResource(R.string.cd_pause_controls), { showPause = true })
                IconSquareButton(ReelIcons.Settings, stringResource(R.string.cd_settings), { onNavigate(Routes.SETTINGS) })
            }
        }

        // First-run guide: always shows the single next step until setup is complete.
        if (guideVisible) {
            GettingStarted(state, onNavigate, { showGoalPicker = true }, onDismissGuide)
        }

        // Limited-mode banner: dashboard stays usable without permissions (AC07).
        if (!guideVisible && state.health == TrackingHealth.PERMISSION_OFF || state.health == TrackingHealth.CONSENT_NEEDED || state.health == TrackingHealth.DETECTION_ISSUE) {
            Card(Modifier.padding(top = 18.dp), background = colors.peach, bordered = false) {
                Text(
                    stringResource(if (state.health == TrackingHealth.DETECTION_ISSUE) R.string.banner_issue_title else R.string.banner_off_title),
                    style = ReelType.label, color = colors.peachInk,
                )
                Text(
                    stringResource(if (state.health == TrackingHealth.DETECTION_ISSUE) R.string.banner_issue_body else R.string.banner_off_body),
                    style = ReelType.small, color = colors.peachInk, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                ReelButton(
                    stringResource(if (state.health == TrackingHealth.DETECTION_ISSUE) R.string.action_troubleshoot else R.string.action_enable_tracking),
                    { onNavigate(if (state.health == TrackingHealth.DETECTION_ISSUE) Routes.TRACKING else Routes.SETUP) },
                    trailingArrow = true,
                )
            }
        }

        // Hero: confirmed detections only.
        val detected = today?.total ?: 0
        val goal = prefs.combinedGoalOrNull
        val progress = StatsCalculator.goalProgress(detected, goal)
        val observingNow = state.serviceConnected && (state.health == TrackingHealth.ACTIVE || state.health == TrackingHealth.LIMITED)
        val unobserved = today != null && today.coverage == Coverage.NONE && detected == 0 && !observingNow
        Box(
            Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 13.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(colors.heroStart, colors.heroEnd)))
                .heightIn(min = 228.dp),
        ) {
            Box(Modifier.align(Alignment.TopEnd).offset(46.dp, (-80).dp).size(190.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape))
            Column(Modifier.padding(23.dp)) {
                Text("✳ " + stringResource(R.string.hero_eyebrow).uppercase(locale), style = ReelType.smallBold, color = colors.mintInk)
                Text(
                    if (unobserved) "—" else Format.number(detected, locale),
                    style = ReelType.hero, color = colors.ink, modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    stringResource(
                        when {
                            unobserved -> R.string.hero_unobserved
                            detected == 0 -> R.string.hero_none_yet
                            else -> R.string.hero_label
                        },
                    ),
                    style = ReelType.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.mintInk,
                )
                if (goal != null && progress != null) {
                    GoalBar(progress, Modifier.padding(top = 22.dp, bottom = 9.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            if (progress > 1f) stringResource(R.string.hero_over_goal, detected - goal) else stringResource(R.string.hero_pct_of_limit, Format.percent(progress)),
                            style = ReelType.smallBold, color = colors.mintInk, modifier = Modifier.weight(1f),
                        )
                        Text(stringResource(R.string.hero_goal, goal), style = ReelType.smallBold, color = colors.mintInk)
                    }
                    // Threshold markers placed at their true positions on the bar.
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        listOf(50, 75, 100).forEach { t ->
                            val reached = progress * 100 >= t
                            Box(Modifier.fillMaxWidth(t / 100f)) {
                                Text(
                                    "$t%", style = ReelType.small.copy(fontSize = 10.sp),
                                    color = if (reached) colors.mintInk else colors.faint, modifier = Modifier.align(Alignment.CenterEnd),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Estimate + feed time
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val est = today?.estimate(null)
            MetricTile(
                Modifier.weight(1f), colors.lav, ReelIcons.Activity, null,
                value = est?.let { Format.approx(ctx, it.value) } ?: "—",
                label = if (est != null) stringResource(R.string.estimate_label) else stringResource(R.string.estimate_unavailable),
                onInfo = { showEstimateInfo = true },
            )
            MetricTile(
                Modifier.weight(1f), colors.peach, ReelIcons.Clock, null,
                value = Format.duration(ctx, today?.feedMs(null) ?: 0),
                label = stringResource(if (today?.feedIncomplete == true) R.string.feed_time_label_incomplete else R.string.feed_time_label),
            )
        }
        if (prefs.showAppForegroundTime) {
            val fg = state.system.appForegroundToday
            Spacer(Modifier.height(12.dp))
            MetricTile(
                Modifier.fillMaxWidth(), colors.sky, ReelIcons.Smartphone, stringResource(R.string.app_time_title),
                value = fg?.let { Format.duration(ctx, it.values.sum()) } ?: "—",
                label = stringResource(if (fg == null) R.string.app_time_unavailable else R.string.app_time_label),
            )
        }

        // By platform
        SectionTitle(stringResource(R.string.by_platform)) { LinkButton(stringResource(R.string.see_details)) { onNavigate(Routes.INSIGHTS) } }
        Platform.entries.forEach { p ->
            val ph = state.platformHealth.firstOrNull { it.platform == p }
            PlatformRow(p, today?.detected?.get(p) ?: 0, today?.estimates?.get(p)?.value, prefs.goalFor(p), ph?.status) { onNavigate(Routes.TRACKING) }
        }

        // 7-day trend
        SectionTitle(stringResource(R.string.trend_title)) { LinkButton(stringResource(R.string.see_details)) { onNavigate(Routes.INSIGHTS) } }
        Card {
            val last7 = state.days.takeLast(7)
            val yesterday = state.yesterday
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vs_yesterday), style = ReelType.small, color = colors.muted)
                    Text(
                        comparisonText(StatsCalculator.compare(detected, yesterday?.total, yesterday != null && yesterday.coverage != Coverage.NONE)),
                        style = ReelType.label, color = colors.ink,
                    )
                }
                if (last7.any { it.coverage == Coverage.NONE }) Pill(stringResource(R.string.has_gaps), PillKind.NEUTRAL)
            }
            Spacer(Modifier.height(10.dp))
            BarChart(
                last7.mapIndexed { i, d ->
                    Bar(
                        d.total.toFloat(), d.date.format(DateTimeFormatter.ofPattern("EEEEE", locale)),
                        noData = d.coverage == Coverage.NONE && d.total == 0, partial = d.coverage == Coverage.PARTIAL, highlight = i == last7.lastIndex,
                    )
                },
                height = 90.dp,
                description = stringResource(R.string.cd_7day_chart),
            )
        }

        // Goal ring
        SectionTitle(stringResource(R.string.progress_title)) { LinkButton(stringResource(R.string.edit_goal)) { onNavigate(Routes.GOALS) } }
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(progress, stringResource(R.string.of_limit), progress?.let { "${Format.percent(it)}%" } ?: "—")
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (goal == null) stringResource(R.string.no_combined_goal) else stringResource(R.string.transitions_left, (goal - detected).coerceAtLeast(0)),
                        style = ReelType.label, color = colors.ink,
                    )
                    Text(stringResource(R.string.progress_body), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
                    LinkButton(stringResource(R.string.adjust_goals)) { onNavigate(Routes.GOALS) }
                }
            }
        }
        FooterCaption(stringResource(R.string.home_footer))
    }

    if (showPause) {
        ModalBottomSheet(onDismissRequest = { showPause = false }, containerColor = colors.paper) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                Text(stringResource(R.string.pause_sheet_title), style = ReelType.sectionTitle, color = colors.ink)
                Text(stringResource(R.string.pause_sheet_body), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                SettingRow(stringResource(R.string.overall_tracking), stringResource(R.string.overall_tracking_desc)) {
                    ReelSwitch(!prefs.globalPaused, { onGlobalPause(!it) }, stringResource(R.string.overall_tracking))
                }
                Platform.entries.forEachIndexed { i, p ->
                    SettingRow(stringResource(p.nameRes()), null, leading = { PlatformBadge(p, 34.dp) }, divider = i < Platform.entries.lastIndex) {
                        ReelSwitch(p !in prefs.pausedPlatforms, { onPlatformPause(p, !it) }, stringResource(p.nameRes()), enabled = !prefs.globalPaused)
                    }
                }
            }
        }
    }
    if (showGoalPicker) {
        var pick by remember { mutableStateOf(if (prefs.goalConfirmed) prefs.combinedGoal else 100) }
        ModalBottomSheet(onDismissRequest = { showGoalPicker = false }, containerColor = colors.paper) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
                Text(stringResource(R.string.setup_goal_title), style = ReelType.sectionTitle, color = colors.ink)
                Text(stringResource(R.string.setup_goal_body), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                com.reelcounter.app.ui.components.GoalPresetPicker(pick) { pick = it }
                Spacer(Modifier.height(16.dp))
                ReelButton(stringResource(R.string.save_goals), { onQuickGoal(pick); showGoalPicker = false }, icon = ReelIcons.Check)
            }
        }
    }
    if (showEstimateInfo) {
        AlertDialog(
            onDismissRequest = { showEstimateInfo = false },
            confirmButton = { TextButton({ showEstimateInfo = false }) { Text(stringResource(R.string.action_got_it)) } },
            title = { Text(stringResource(R.string.estimate_info_title)) },
            text = { Text(stringResource(R.string.estimate_info_body)) },
            containerColor = colors.paper, titleContentColor = colors.ink, textContentColor = colors.muted,
        )
    }
}

@Composable
fun PlatformRow(p: Platform, count: Int, estimate: Double?, goal: Int?, status: PlatformStatus?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val sub = when (status) {
        PlatformStatus.USER_PAUSED -> stringResource(R.string.platform_paused)
        PlatformStatus.NOT_INSTALLED -> stringResource(R.string.platform_not_installed)
        PlatformStatus.SERVICE_DISABLED -> stringResource(R.string.platform_service_off)
        PlatformStatus.UNAVAILABLE -> stringResource(R.string.platform_unavailable)
        else -> if (goal != null) stringResource(R.string.platform_sub_goal, goal) else stringResource(R.string.platform_sub)
    }
    val name = stringResource(p.nameRes())
    Row(
        Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(18.dp)).background(colors.paper)
            .border(1.dp, colors.line, RoundedCornerShape(18.dp)).clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name, $count, $sub" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlatformBadge(p)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = ReelType.label, color = colors.ink)
            Text(sub, style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted, modifier = Modifier.padding(top = 3.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Format.number(count), style = ReelType.metric.copy(fontSize = 18.sp), color = colors.ink)
            if (estimate != null) Text(Format.approx(ctx, estimate) + " " + stringResource(R.string.est_short), style = ReelType.small.copy(fontSize = 10.sp), color = colors.lavInk)
        }
        Spacer(Modifier.width(6.dp))
        Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
    }
}

@Composable
private fun GettingStarted(state: UiState, onNavigate: (String) -> Unit, onPickGoal: () -> Unit, onDismiss: () -> Unit) {
    val done = state.guideStepsDone
    val allDone = done == 3
    Card(Modifier.padding(top = 18.dp), background = if (allDone) colors.mint else colors.paper) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (allDone) R.string.guide_all_set else R.string.guide_title),
                style = ReelType.sectionTitle, color = colors.ink, modifier = Modifier.weight(1f),
            )
            Pill(stringResource(R.string.guide_progress, done, 3), if (allDone) PillKind.OK else PillKind.NEUTRAL)
        }
        if (allDone) {
            Text(stringResource(R.string.guide_all_set_body), style = ReelType.small, color = colors.mintInk, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
            ReelButton(stringResource(R.string.guide_close), onDismiss, kind = ButtonKind.GHOST)
            return@Card
        }
        Text(stringResource(R.string.guide_subtitle), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        com.reelcounter.app.ui.components.GuideStep(
            1, stringResource(R.string.guide_step1_title), stringResource(R.string.guide_step1_desc), state.trackingOn,
            stringResource(R.string.guide_step1_action),
        ) { onNavigate(Routes.SETUP) }
        com.reelcounter.app.ui.components.GuideStep(
            2, stringResource(R.string.guide_step2_title), stringResource(R.string.guide_step2_desc), state.prefs.goalConfirmed,
            stringResource(R.string.guide_step2_action), onPickGoal,
        )
        com.reelcounter.app.ui.components.GuideStep(
            3, stringResource(R.string.guide_step3_title), stringResource(R.string.guide_step3_desc), state.hasDetectedEver,
            if (state.trackingOn) stringResource(R.string.guide_step3_action) else null,
        ) { onNavigate(Routes.TRACKING) }
        com.reelcounter.app.ui.components.GuideStep(
            4, stringResource(R.string.guide_step4_title), stringResource(R.string.guide_step4_desc), state.system.widgetCount > 0,
            stringResource(R.string.guide_step4_action),
        ) { onNavigate(Routes.WIDGETS) }
        Text(
            stringResource(R.string.guide_hide), style = ReelType.smallBold, color = colors.muted,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onDismiss).padding(vertical = 6.dp),
        )
    }
}
