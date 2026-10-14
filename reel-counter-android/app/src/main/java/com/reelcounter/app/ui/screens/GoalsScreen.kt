package com.reelcounter.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.core.Format
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.nameRes
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Callout
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.FooterCaption
import com.reelcounter.app.ui.components.IconSquareButton
import com.reelcounter.app.ui.components.PageHeader
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GoalsScreen(
    state: UiState,
    onNavigate: (String) -> Unit,
    onSave: (Boolean, Int, Map<Platform, Boolean>, Map<Platform, Int>) -> Unit,
    onReminders: (Boolean) -> Unit,
    onThreshold: (Int, Boolean) -> Unit,
    onQuiet: (Boolean, Int, Int) -> Unit,
    onSaved: () -> Unit,
    onTipSeen: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    var combinedEnabled by remember(prefs.combinedGoalEnabled) { mutableStateOf(prefs.combinedGoalEnabled) }
    var combined by remember(prefs.combinedGoal) { mutableIntStateOf(prefs.combinedGoal) }
    val enabled = remember(prefs.platformGoalEnabled) { mutableStateMapOf<Platform, Boolean>().apply { putAll(prefs.platformGoalEnabled) } }
    val goals = remember(prefs.platformGoals) { mutableStateMapOf<Platform, Int>().apply { putAll(prefs.platformGoals) } }
    val detected = state.today?.total ?: 0

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onReminders(granted)
    }

    ScreenColumn {
        PageHeader(stringResource(R.string.goals_title), stringResource(R.string.goals_caption)) {
            IconSquareButton(ReelIcons.Bell, stringResource(R.string.floating_title), { onNavigate(Routes.OVERLAY) })
        }
        if ("goals" !in state.prefs.tipsSeen) {
            com.reelcounter.app.ui.components.TipBanner(stringResource(R.string.tip_goals)) { onTipSeen("goals") }
        }

        Card(background = colors.mint, bordered = false, padding = androidx.compose.foundation.layout.PaddingValues(21.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.combined_limit).uppercase(), style = ReelType.smallBold, color = colors.mintInk)
                    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 9.dp)) {
                        Text(if (combinedEnabled) Format.number(combined) else stringResource(R.string.off), style = ReelType.sectionHeading.copy(fontSize = 42.sp), color = colors.ink)
                        if (combinedEnabled) Text(" " + stringResource(R.string.videos_unit), style = ReelType.small, color = colors.mintInk, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                ProgressRing(
                    if (combinedEnabled) detected.toFloat() / combined else null, stringResource(R.string.used),
                    if (combinedEnabled) "${Format.percent(detected.toFloat() / combined)}%" else "—", size = 93.dp, stroke = 11.dp,
                )
            }
            Slider(
                value = combined.toFloat(),
                onValueChange = { combined = (it / 10).toInt() * 10 },
                valueRange = 20f..500f,
                enabled = combinedEnabled,
                modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = ctx.getString(R.string.combined_limit) },
                colors = SliderDefaults.colors(thumbColor = colors.goalFill, activeTrackColor = colors.goalFill, inactiveTrackColor = colors.paper),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.combined_goal_toggle), style = ReelType.small, color = colors.mintInk, modifier = Modifier.weight(1f))
                ReelSwitch(combinedEnabled, { combinedEnabled = it }, stringResource(R.string.combined_goal_toggle))
            }
        }

        SectionTitle(stringResource(R.string.limits_by_platform)) { Text(stringResource(R.string.adjust_anytime), style = ReelType.smallBold, color = colors.muted) }
        Card {
            Platform.entries.forEachIndexed { i, p ->
                val name = stringResource(p.nameRes())
                SettingRow(
                    name,
                    stringResource(R.string.today_detected, state.today?.detected?.get(p) ?: 0),
                    leading = { PlatformBadge(p, 36.dp) },
                    divider = i < Platform.entries.lastIndex,
                ) {
                    if (enabled[p] == true) {
                        GoalInput(goals[p] ?: 1, name) { goals[p] = it }
                        Spacer(Modifier.width(8.dp))
                    }
                    ReelSwitch(enabled[p] == true, { enabled[p] = it }, stringResource(R.string.cd_platform_goal, name))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ReelButton(stringResource(R.string.save_goals), {
            onSave(combinedEnabled, combined, enabled.toMap(), goals.toMap())
            onSaved()
        }, kind = ButtonKind.SECONDARY, icon = ReelIcons.Check)
        Text(stringResource(R.string.goal_confirmed_only), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, modifier = Modifier.padding(top = 8.dp))

        SectionTitle(stringResource(R.string.checkins_title))
        Card {
            SettingRow(
                stringResource(R.string.reminders_master),
                stringResource(if (state.system.notificationsAllowed || !prefs.remindersEnabled) R.string.reminders_master_desc else R.string.reminders_blocked),
            ) {
                ReelSwitch(prefs.remindersEnabled, { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !state.system.notificationsAllowed) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onReminders(on)
                    }
                }, stringResource(R.string.reminders_master))
            }
            listOf(50 to prefs.threshold50, 75 to prefs.threshold75, 100 to prefs.threshold100).forEach { (pct, on) ->
                SettingRow(
                    stringResource(R.string.threshold_title, pct),
                    stringResource(
                        when (pct) {
                            50 -> R.string.threshold_50_desc
                            75 -> R.string.threshold_75_desc
                            else -> R.string.threshold_100_desc
                        },
                    ),
                ) { ReelSwitch(on, { onThreshold(pct, it) }, stringResource(R.string.threshold_title, pct), enabled = prefs.remindersEnabled) }
            }
            SettingRow(stringResource(R.string.quiet_hours), stringResource(R.string.quiet_hours_desc), divider = prefs.quietHoursEnabled) {
                ReelSwitch(prefs.quietHoursEnabled, { onQuiet(it, prefs.quietStartMinutes, prefs.quietEndMinutes) }, stringResource(R.string.quiet_hours))
            }
            if (prefs.quietHoursEnabled) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeChip(Modifier.weight(1f), stringResource(R.string.quiet_from), prefs.quietStartMinutes) { onQuiet(true, it, prefs.quietEndMinutes) }
                    TimeChip(Modifier.weight(1f), stringResource(R.string.quiet_to), prefs.quietEndMinutes) { onQuiet(true, prefs.quietStartMinutes, it) }
                }
            }
        }
        if (prefs.remindersEnabled && !state.system.notificationsAllowed) {
            Callout(stringResource(R.string.notifications_off_callout), ReelIcons.Alert, warm = true)
            ReelButton(stringResource(R.string.open_notification_settings), {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }, kind = ButtonKind.GHOST)
        }
        Callout(stringResource(R.string.reminder_cap_note))
        SettingRow(stringResource(R.string.floating_title), stringResource(R.string.floating_row_desc), leading = { com.reelcounter.app.ui.components.RowIcon(ReelIcons.Bell) }, onClick = { onNavigate(Routes.OVERLAY) }, divider = false) {
            Pill(stringResource(if (prefs.overlayEnabled) R.string.status_on else R.string.status_off), if (prefs.overlayEnabled) PillKind.OK else PillKind.NEUTRAL)
        }
        FooterCaption(stringResource(R.string.goals_footer))
    }
}

@Composable
private fun GoalInput(value: Int, name: String, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val desc = stringResource(R.string.cd_goal_input, name)
    BasicTextField(
        value = text,
        onValueChange = { s ->
            val digits = s.filter { it.isDigit() }.take(4)
            text = digits
            digits.toIntOrNull()?.let { if (it >= 1) onChange(it) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = ReelType.label.copy(fontSize = 15.sp, color = colors.ink, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.ink),
        modifier = Modifier.width(62.dp).clip(RoundedCornerShape(10.dp)).background(colors.chip).padding(vertical = 9.dp, horizontal = 4.dp)
            .semantics { contentDescription = desc },
    )
}

@Composable
private fun TimeChip(modifier: Modifier, label: String, minutes: Int, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val t = LocalTime.of(minutes / 60, minutes % 60)
    val formatted = t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(colors.chip)
            .clickable(role = Role.Button) {
                TimePickerDialog(ctx, { _, h, m -> onPick(h * 60 + m) }, t.hour, t.minute, DateFormat.is24HourFormat(ctx)).show()
            }
            .padding(12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label $formatted" },
    ) {
        Text(label, style = ReelType.small, color = colors.muted)
        Text(formatted, style = ReelType.label, color = colors.ink, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun OverlayScreen(state: UiState, onBack: () -> Unit, onEnable: (Boolean) -> Unit, onCooldown: (Int) -> Unit, onPreview: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    var askedPermission by remember { mutableStateOf(false) }
    // When the user returns from the system screen with permission granted, finish enabling.
    LaunchedEffect(state.system.overlayAllowed) {
        if (askedPermission && state.system.overlayAllowed) {
            onEnable(true)
            askedPermission = false
        }
    }
    ScreenColumn {
        PageHeader(stringResource(R.string.floating_title), stringResource(R.string.floating_caption), onBack = onBack)
        Box(
            Modifier.fillMaxWidth().padding(vertical = 14.dp).height(210.dp).clip(RoundedCornerShape(24.dp)).background(colors.chip),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(122.dp, 182.dp).clip(RoundedCornerShape(18.dp)).background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF4D5C52), androidx.compose.ui.graphics.Color(0xFF2C382F))),
                ),
                contentAlignment = Alignment.Center,
            ) { androidx.compose.material3.Icon(ReelIcons.Play, null, Modifier.size(26.dp), tint = androidx.compose.ui.graphics.Color(0xFFD7E1D2)) }
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 10.dp).clip(RoundedCornerShape(20.dp))
                    .background(androidx.compose.ui.graphics.Color(0xFFE7F5E9)).padding(horizontal = 15.dp, vertical = 13.dp),
            ) {
                Text("🌿 " + stringResource(R.string.overlay_title_pct, 75), style = ReelType.smallBold, color = androidx.compose.ui.graphics.Color(0xFF2F5E45))
                Text(stringResource(R.string.overlay_sub), style = ReelType.small.copy(fontSize = 10.sp), color = androidx.compose.ui.graphics.Color(0xFF4F7360))
            }
        }
        Card {
            SettingRow(stringResource(R.string.floating_enable), stringResource(R.string.floating_enable_desc)) {
                ReelSwitch(prefs.overlayEnabled, { on ->
                    if (on && !state.system.overlayAllowed) {
                        askedPermission = true
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                    } else {
                        onEnable(on)
                    }
                }, stringResource(R.string.floating_enable))
            }
            SettingRow(stringResource(R.string.floating_style), stringResource(R.string.floating_style_desc)) { Pill(stringResource(R.string.gentle)) }
            SettingRow(stringResource(R.string.cooldown), stringResource(R.string.cooldown_desc), divider = false)
            com.reelcounter.app.ui.components.Segmented(
                listOf(15, 30, 60).map { stringResource(R.string.duration_m, it) },
                listOf(15, 30, 60).indexOf(prefs.overlayCooldownMinutes).coerceAtLeast(0),
            ) { onCooldown(listOf(15, 30, 60)[it]) }
        }
        Callout(stringResource(R.string.floating_permission_note))
        ReelButton(
            stringResource(R.string.floating_preview), onPreview, trailingArrow = true,
            enabled = state.system.overlayAllowed,
        )
        if (!state.system.overlayAllowed) {
            Text(stringResource(R.string.floating_preview_needs_permission), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
