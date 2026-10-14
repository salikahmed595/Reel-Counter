package com.reelcounter.app.ui.screens

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.core.Format
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.prefs.WidgetConfig
import com.reelcounter.app.data.prefs.WidgetMetric
import com.reelcounter.app.data.prefs.WidgetTheme
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.ChipRow
import com.reelcounter.app.ui.components.FooterCaption
import com.reelcounter.app.ui.components.GoalBar
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ReelSwitch
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.SettingRow
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType
import com.reelcounter.app.widget.CustomWidgetReceiver
import com.reelcounter.app.widget.LargeWidgetReceiver
import com.reelcounter.app.widget.MediumWidgetReceiver
import com.reelcounter.app.widget.SmallWidgetReceiver
import com.reelcounter.app.widget.WidgetLayout
import com.reelcounter.app.widget.WidgetPinnedReceiver
import java.time.format.DateTimeFormatter

private data class PreviewPalette(val bg: Color, val ink: Color, val sub: Color, val fill: Color)

private fun previewPalette(t: WidgetTheme) = when (t) {
    WidgetTheme.SAGE -> PreviewPalette(Color(0xFFE0F0E5), Color(0xFF243A2D), Color(0xFF41654F), Color(0xFF4F8A68))
    WidgetTheme.LAVENDER -> PreviewPalette(Color(0xFFEEE7FA), Color(0xFF2F2742), Color(0xFF5A4C78), Color(0xFF78669D))
    WidgetTheme.IVORY -> PreviewPalette(Color(0xFFFFFFFF), Color(0xFF23302B), Color(0xFF52635A), Color(0xFF4F8A68))
    WidgetTheme.NIGHT -> PreviewPalette(Color(0xFF273C33), Color(0xFFECFFF0), Color(0xFFB9D6C3), Color(0xFF8FD1A8))
}

@Composable
fun WidgetPreview(state: UiState, config: WidgetConfig, layout: WidgetLayout) {
    val ctx = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val p = previewPalette(config.theme)
    val today = state.today
    val filter = config.platform
    val detected = today?.detected(filter) ?: 0
    val goal = if (filter == null) state.prefs.combinedGoalOrNull else state.prefs.goalFor(filter)
    val (value, label) = when (config.metric) {
        WidgetMetric.DETECTED_TODAY -> Format.number(detected) to stringResource(R.string.widget_label_detected)
        WidgetMetric.REMAINING -> (goal?.let { Format.number((it - detected).coerceAtLeast(0)) } ?: "—") to stringResource(R.string.widget_label_remaining)
        WidgetMetric.FEED_TIME -> Format.duration(ctx, today?.feedMs(filter) ?: 0) to stringResource(R.string.widget_label_feed_time)
        WidgetMetric.LAST_7_DAYS -> Format.number(state.days.takeLast(7).sumOf { it.detected(filter) }) to stringResource(R.string.widget_label_7d)
    }
    val small = layout == WidgetLayout.SMALL
    val desc = stringResource(R.string.cd_widget_preview, value, label)
    Column(
        Modifier.then(if (small) Modifier.width(172.dp) else Modifier.fillMaxWidth()).heightIn(min = if (layout == WidgetLayout.LARGE) 214.dp else 158.dp)
            .clip(RoundedCornerShape(25.dp)).background(p.bg)
            .then(if (config.theme == WidgetTheme.IVORY) Modifier.border(1.dp, colors.line, RoundedCornerShape(25.dp)) else Modifier)
            .padding(18.dp).semantics(mergeDescendants = true) { contentDescription = desc },
    ) {
        Row {
            Text(stringResource(R.string.widget_brand), style = ReelType.smallBold.copy(fontSize = 10.sp), color = p.sub, modifier = Modifier.weight(1f))
            if (!small) Text((filter?.abbr ?: (today?.date ?: java.time.LocalDate.now()).format(DateTimeFormatter.ofPattern("EEE d MMM", locale))).uppercase(locale), style = ReelType.smallBold.copy(fontSize = 10.sp), color = p.sub)
        }
        Text(value, style = ReelType.hero.copy(fontSize = 50.sp, lineHeight = 58.sp), color = p.ink, modifier = Modifier.padding(top = 6.dp))
        Text(label, style = ReelType.smallBold.copy(fontSize = 10.sp), color = p.sub)
        if (!small && goal != null) {
            GoalBar(detected.toFloat() / goal, Modifier.padding(top = 14.dp, bottom = 8.dp), track = Color.White.copy(alpha = 0.55f), fill = p.fill, height = 6.dp)
            Text(stringResource(R.string.widget_pct_goal, Format.percent(detected.toFloat() / goal)), style = ReelType.smallBold.copy(fontSize = 10.sp), color = p.sub)
        }
        if (config.showEstimates && !small) {
            today?.estimate(filter)?.let { Text(stringResource(R.string.widget_estimate_line, it.value.toInt()), style = ReelType.small.copy(fontSize = 10.sp), color = p.sub) }
        }
        if (layout == WidgetLayout.LARGE) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Platform.entries.forEach { pl ->
                    Text("${pl.abbr} ${today?.detected?.get(pl) ?: 0}", style = ReelType.smallBold, color = p.ink)
                }
            }
        }
    }
}

@Composable
fun WidgetConfigurator(config: WidgetConfig, isPro: Boolean, onChange: (WidgetConfig) -> Unit, onPro: () -> Unit) {
    SectionTitle(stringResource(R.string.widget_primary_number))
    Column(Modifier.clip(RoundedCornerShape(23.dp)).background(colors.paper).border(1.dp, colors.line, RoundedCornerShape(23.dp)).padding(horizontal = 18.dp)) {
        WidgetMetric.entries.forEachIndexed { i, m ->
            val sel = config.metric == m
            SettingRow(
                stringResource(
                    when (m) {
                        WidgetMetric.DETECTED_TODAY -> R.string.metric_detected_today
                        WidgetMetric.REMAINING -> R.string.metric_remaining
                        WidgetMetric.FEED_TIME -> R.string.metric_feed_time
                        WidgetMetric.LAST_7_DAYS -> R.string.metric_last_7
                    },
                ),
                null,
                onClick = { onChange(config.copy(metric = m)) },
                divider = i < WidgetMetric.entries.lastIndex,
            ) {
                Box(
                    Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, if (sel) colors.goalFill else colors.faint, RoundedCornerShape(10.dp))
                        .semantics { selected = sel },
                    contentAlignment = Alignment.Center,
                ) { if (sel) Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(colors.goalFill)) }
            }
        }
    }

    SectionTitle(stringResource(R.string.widget_scope))
    ChipRow(
        listOf(stringResource(R.string.filter_all)) + Platform.entries.map { it.abbr },
        config.platform?.let { Platform.entries.indexOf(it) + 1 } ?: 0,
        { onChange(config.copy(platform = if (it == 0) null else Platform.entries[it - 1])) },
        Modifier.horizontalScroll(rememberScrollState()),
    )

    SectionTitle(stringResource(R.string.widget_color_story)) { if (!isPro) Pill(stringResource(R.string.pro_more_themes), PillKind.PRO) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WidgetTheme.entries.forEach { t ->
            val locked = t.proOnly && !isPro
            val sel = config.theme == t
            val name = stringResource(
                when (t) {
                    WidgetTheme.SAGE -> R.string.theme_sage
                    WidgetTheme.LAVENDER -> R.string.theme_lavender
                    WidgetTheme.IVORY -> R.string.theme_ivory
                    WidgetTheme.NIGHT -> R.string.theme_night
                },
            )
            Box(
                Modifier.size(56.dp, 50.dp).clip(RoundedCornerShape(15.dp)).background(previewPalette(t).bg)
                    .border(if (sel) 3.dp else 1.dp, if (sel) colors.goalFill else colors.line, RoundedCornerShape(15.dp))
                    .clickable(role = Role.RadioButton) { if (locked) onPro() else onChange(config.copy(theme = t)) }
                    .semantics { contentDescription = name; selected = sel },
                contentAlignment = Alignment.Center,
            ) { if (locked) Icon(ReelIcons.Lock, null, Modifier.size(15.dp), tint = previewPalette(t).sub) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.clip(RoundedCornerShape(23.dp)).background(colors.paper).border(1.dp, colors.line, RoundedCornerShape(23.dp)).padding(horizontal = 18.dp)) {
        SettingRow(stringResource(R.string.widget_show_estimates), stringResource(R.string.widget_show_estimates_desc), divider = false) {
            ReelSwitch(config.showEstimates, { onChange(config.copy(showEstimates = it)) }, stringResource(R.string.widget_show_estimates))
        }
    }
}

@Composable
fun WidgetsScreen(state: UiState, onNavigate: (String) -> Unit, onTipSeen: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    var sizeIdx by rememberSaveable { mutableIntStateOf(1) }
    var configStr by rememberSaveable { mutableStateOf(WidgetConfig().encode()) }
    var showHowTo by rememberSaveable { mutableStateOf(false) }
    val config = WidgetConfig.decode(configStr)
    val sizes = listOf(WidgetLayout.SMALL, WidgetLayout.MEDIUM, WidgetLayout.LARGE, null)

    ScreenColumn {
        PageHeader(stringResource(R.string.widgets_title), stringResource(R.string.widgets_caption))
        if ("widgets" !in state.prefs.tipsSeen) {
            com.reelcounter.app.ui.components.TipBanner(stringResource(R.string.tip_widgets)) { onTipSeen("widgets") }
        }
        Text(stringResource(R.string.live_preview).uppercase(), style = ReelType.smallBold, color = colors.muted, modifier = Modifier.padding(bottom = 12.dp))
        WidgetPreview(state, config, sizes[sizeIdx] ?: WidgetLayout.MEDIUM)

        SectionTitle(stringResource(R.string.choose_size)) { Text(stringResource(R.string.glance_matters), style = ReelType.smallBold, color = colors.muted) }
        val options = listOf(
            R.string.size_small to R.string.size_small_desc,
            R.string.size_medium to R.string.size_medium_desc,
            R.string.size_large to R.string.size_large_desc,
            R.string.size_custom to R.string.size_custom_desc,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            options.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEachIndexed { col, (n, d) ->
                        val i = row * 2 + col
                        val sel = i == sizeIdx
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(colors.paper)
                                .border(if (sel) 2.dp else 1.dp, if (sel) colors.goalFill else colors.line, RoundedCornerShape(18.dp))
                                .clickable(role = Role.RadioButton) { sizeIdx = i }.semantics { selected = sel }.padding(14.dp),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(listOf(0.46f, 0.95f, 0.75f, 0.88f)[i]).height(if (i >= 2) 50.dp else 38.dp)
                                    .clip(RoundedCornerShape(8.dp)).background(if (i == 3) colors.lav else colors.mint),
                            )
                            Text(stringResource(n), style = ReelType.smallBold, color = colors.ink, modifier = Modifier.padding(top = 9.dp))
                            Text(stringResource(d), style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted)
                        }
                    }
                }
            }
        }

        WidgetConfigurator(config, state.isPro, { configStr = it.encode() }, { onNavigate(Routes.PRO) })

        Spacer(Modifier.height(16.dp))
        ReelButton(stringResource(R.string.add_widget), {
            val receiver = when (sizes[sizeIdx]) {
                WidgetLayout.SMALL -> SmallWidgetReceiver::class.java
                WidgetLayout.MEDIUM -> MediumWidgetReceiver::class.java
                WidgetLayout.LARGE -> LargeWidgetReceiver::class.java
                null -> CustomWidgetReceiver::class.java
            }
            val mgr = AppWidgetManager.getInstance(ctx)
            if (mgr.isRequestPinAppWidgetSupported) {
                val callback = PendingIntent.getBroadcast(
                    ctx, sizeIdx,
                    Intent(ctx, WidgetPinnedReceiver::class.java).putExtra(WidgetPinnedReceiver.EXTRA_CONFIG, config.encode()),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                mgr.requestPinAppWidget(ComponentName(ctx, receiver), null, callback)
            } else {
                showHowTo = true
            }
        }, icon = ReelIcons.Grid)
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.widget_how_to_add), { showHowTo = true }, kind = com.reelcounter.app.ui.components.ButtonKind.GHOST)
        FooterCaption(stringResource(R.string.widget_update_note))
    }
    if (showHowTo) {
        AlertDialog(
            onDismissRequest = { showHowTo = false },
            confirmButton = { TextButton({ showHowTo = false }) { Text(stringResource(R.string.action_got_it)) } },
            title = { Text(stringResource(R.string.widget_how_to_title)) },
            text = { Text(stringResource(R.string.widget_how_to_body)) },
            containerColor = colors.paper, titleContentColor = colors.ink, textContentColor = colors.muted,
        )
    }
}
