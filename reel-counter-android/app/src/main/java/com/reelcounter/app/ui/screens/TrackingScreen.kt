package com.reelcounter.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reelcounter.app.R
import com.reelcounter.app.core.Platform
import com.reelcounter.app.core.nameRes
import com.reelcounter.app.tracking.PlatformStatus
import com.reelcounter.app.tracking.TrackingHealth
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Callout
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.PlatformBadge
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ReelSwitch
import com.reelcounter.app.ui.components.RowIcon
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.SettingRow
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType

@Composable
fun TrackingScreen(
    state: UiState,
    onBack: (() -> Unit)?,
    onNavigate: (String) -> Unit,
    onGlobalPause: (Boolean) -> Unit,
    onPlatformPause: (Platform, Boolean) -> Unit,
    onTipSeen: (String) -> Unit = {},
) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    val active = state.health == TrackingHealth.ACTIVE || state.health == TrackingHealth.LIMITED
    var testStartedAt by rememberSaveable { mutableLongStateOf(0L) }
    var openTip by rememberSaveable { mutableStateOf(-1) }

    ScreenColumn {
        PageHeader(stringResource(R.string.tracking_title), stringResource(R.string.tracking_caption), onBack = onBack)
        if ("tracking" !in state.prefs.tipsSeen) {
            com.reelcounter.app.ui.components.TipBanner(stringResource(R.string.tip_tracking)) { onTipSeen("tracking") }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (active) colors.mint else colors.peach).padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(colors.paper.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(if (prefs.globalPaused) ReelIcons.Pause else ReelIcons.Activity, null, Modifier.size(22.dp), tint = if (active) colors.mintInk else colors.peachInk)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(healthLabel(state.health), style = ReelType.label, color = colors.ink)
                Text(
                    stringResource(
                        when (state.health) {
                            TrackingHealth.ACTIVE -> R.string.health_active_desc
                            TrackingHealth.LIMITED -> R.string.health_limited_desc
                            TrackingHealth.PAUSED -> R.string.health_paused_desc
                            TrackingHealth.PERMISSION_OFF -> R.string.health_permission_off_desc
                            TrackingHealth.DETECTION_ISSUE -> R.string.health_detection_issue_desc
                            TrackingHealth.CONSENT_NEEDED -> R.string.health_consent_needed_desc
                        },
                    ),
                    style = ReelType.small, color = colors.muted,
                )
            }
        }

        if (!state.system.serviceEnabled && prefs.hasValidConsent) RestrictedSettingsHelp()
        Card(Modifier.padding(top = 13.dp)) {
            SettingRow(stringResource(R.string.overall_tracking), stringResource(R.string.overall_tracking_desc), divider = false) {
                ReelSwitch(!prefs.globalPaused, { onGlobalPause(!it) }, stringResource(R.string.overall_tracking))
            }
        }

        SectionTitle(stringResource(R.string.tracking_by_platform))
        Card {
            Platform.entries.forEachIndexed { i, p ->
                val ph = state.platformHealth.firstOrNull { it.platform == p }
                val status = when (ph?.status) {
                    PlatformStatus.ACTIVE_VERIFIED -> stringResource(R.string.pstatus_verified)
                    PlatformStatus.ACTIVE_EXPERIMENTAL -> stringResource(R.string.pstatus_experimental)
                    PlatformStatus.USER_PAUSED -> stringResource(R.string.platform_paused)
                    PlatformStatus.SERVICE_DISABLED -> stringResource(R.string.platform_service_off)
                    PlatformStatus.NOT_INSTALLED -> stringResource(R.string.platform_not_installed)
                    PlatformStatus.UNAVAILABLE, null -> stringResource(R.string.platform_unavailable)
                }
                val version = ph?.appVersion?.let { " · " + stringResource(R.string.app_version_unverified, it) } ?: ""
                SettingRow(stringResource(p.nameRes()), status + version, leading = { PlatformBadge(p, 35.dp) }, divider = i < Platform.entries.lastIndex) {
                    ReelSwitch(p !in prefs.pausedPlatforms, { onPlatformPause(p, !it) }, stringResource(p.nameRes()), enabled = !prefs.globalPaused)
                }
            }
        }

        SectionTitle(stringResource(R.string.detection_status))
        Card {
            SettingRow(stringResource(R.string.perm_accessibility_title), null, onClick = {
                if (prefs.hasValidConsent) ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else onNavigate(Routes.SETUP)
            }) {
                Pill(stringResource(if (state.system.serviceEnabled) R.string.status_on else R.string.status_off), if (state.system.serviceEnabled) PillKind.OK else PillKind.WARN)
            }
            SettingRow(stringResource(R.string.service_running), null) {
                Pill(stringResource(if (state.serviceConnected) R.string.status_connected else R.string.status_not_connected), if (state.serviceConnected) PillKind.OK else PillKind.WARN)
            }
            SettingRow(stringResource(R.string.in_app_consent), null, onClick = { onNavigate(Routes.SETUP) }) {
                Pill(stringResource(if (prefs.hasValidConsent) R.string.consent_given else R.string.consent_missing), if (prefs.hasValidConsent) PillKind.OK else PillKind.WARN)
            }
            SettingRow(stringResource(R.string.supported_interfaces), stringResource(R.string.supported_interfaces_desc)) {
                Pill(stringResource(R.string.not_validated), PillKind.WARN)
            }
            val last = state.lastEventAt.values.maxOrNull()
            SettingRow(stringResource(R.string.last_detected_event), null, divider = false) {
                Text(
                    last?.let { DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() }
                        ?: stringResource(R.string.none_this_session),
                    style = ReelType.small, color = colors.muted,
                )
            }
        }

        // Private on-device check: nothing leaves the phone; only watches for a confirmed transition.
        SectionTitle(stringResource(R.string.detection_check_title))
        Card {
            val detectedPlatform = state.lastEventAt.entries.filter { it.value >= testStartedAt && testStartedAt > 0 }.maxByOrNull { it.value }?.key
            Text(
                when {
                    testStartedAt == 0L -> stringResource(R.string.detection_check_desc)
                    detectedPlatform != null -> stringResource(R.string.detection_check_ok, stringResource(detectedPlatform.nameRes()))
                    else -> stringResource(R.string.detection_check_waiting)
                },
                style = ReelType.small, color = if (detectedPlatform != null) colors.mintInk else colors.muted,
            )
            Spacer(Modifier.height(12.dp))
            ReelButton(
                stringResource(if (testStartedAt == 0L) R.string.detection_check_start else R.string.detection_check_restart),
                { testStartedAt = System.currentTimeMillis() },
                kind = ButtonKind.SECONDARY, icon = ReelIcons.Refresh,
                enabled = state.system.serviceEnabled && prefs.hasValidConsent,
            )
        }

        SectionTitle(stringResource(R.string.troubleshooting))
        val tips = listOf(
            R.string.tip_permission_title to R.string.tip_permission_body,
            R.string.tip_battery_title to oemBatteryTip(),
            R.string.tip_app_changed_title to R.string.tip_app_changed_body,
            R.string.tip_force_stop_title to R.string.tip_force_stop_body,
            R.string.tip_support_title to R.string.tip_support_body,
        )
        Card {
            tips.forEachIndexed { i, (t, b) ->
                Column(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { openTip = if (openTip == i) -1 else i }.padding(vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RowIcon(ReelIcons.Help, colors.muted)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(t), style = ReelType.label, color = colors.ink, modifier = Modifier.weight(1f))
                        Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
                    }
                    if (openTip == i) Text(stringResource(b), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 8.dp, start = 30.dp))
                }
                if (i < tips.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            }
        }
        Spacer(Modifier.height(12.dp))
        ReelButton(stringResource(R.string.open_battery_settings), {
            runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                .onFailure { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
        }, kind = ButtonKind.GHOST, icon = ReelIcons.Battery)
        Callout(stringResource(R.string.why_counting_stops), warm = true)
    }
}

private fun oemBatteryTip(): Int = when (Build.MANUFACTURER.lowercase()) {
    "samsung" -> R.string.tip_battery_samsung
    "xiaomi", "redmi", "poco" -> R.string.tip_battery_xiaomi
    "oppo", "realme", "oneplus" -> R.string.tip_battery_oppo
    "vivo" -> R.string.tip_battery_vivo
    else -> R.string.tip_battery_generic
}
