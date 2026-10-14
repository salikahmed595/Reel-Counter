package com.reelcounter.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Callout
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.FooterCaption
import com.reelcounter.app.ui.components.GoalPresetPicker
import com.reelcounter.app.ui.components.NumberBadge
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ReelSwitch
import com.reelcounter.app.ui.components.RowIcon
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SettingRow
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType

/** PAICONS Self-Development lockup: app mark + wordmark, matching paicons.com. */
@Composable
fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF10120F)), contentAlignment = Alignment.Center) {
            Icon(
                androidx.compose.ui.res.painterResource(R.drawable.ic_launcher_foreground), null,
                Modifier.size(58.dp), tint = Color.Unspecified,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("PAICONS", style = ReelType.label.copy(fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.03).em), color = colors.ink)
            Text(
                stringResource(R.string.brand_tag),
                style = ReelType.smallBold.copy(fontSize = 9.sp, letterSpacing = 0.14.em),
                color = if (colors.dark) Color(0xFFB9F464) else Color(0xFF4E7A12),
            )
        }
    }
}

@Composable
private fun StepDots(active: Int, total: Int = 3) {
    Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(Modifier.width(21.dp).height(5.dp).clip(RoundedCornerShape(5.dp)).background(if (i <= active) colors.goalFill else colors.line))
        }
    }
}

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onPrivacy: () -> Unit) {
    var showHow by remember { mutableStateOf(false) }
    ScreenColumn(bottomBar = false) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandMark()
            Spacer(Modifier.weight(1f))
            Pill(stringResource(R.string.welcome_pill))
        }
        // Decorative hero (illustration only, not data).
        val illustration = stringResource(R.string.cd_welcome_illustration)
        Box(
            Modifier.fillMaxWidth().padding(top = 18.dp).height(250.dp).clip(RoundedCornerShape(33.dp))
                .background(colors.mintSoft)
                .background(Brush.radialGradient(listOf(colors.mint, Color.Transparent), radius = 500f))
                .semantics { contentDescription = illustration },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(250.dp).clip(CircleShape).border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape))
            Column(
                Modifier.rotate(-8f).shadow(18.dp, RoundedCornerShape(43.dp)).size(150.dp).clip(RoundedCornerShape(43.dp)).background(colors.paper),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(ReelIcons.Leaf, null, Modifier.size(54.dp), tint = colors.mintDeep)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.welcome_center_caption), style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted)
            }
            Text(
                stringResource(R.string.welcome_chip_present), style = ReelType.smallBold.copy(fontSize = 10.sp), color = colors.ink,
                modifier = Modifier.align(Alignment.TopStart).offset(10.dp, 50.dp).rotate(-9f).shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)).background(colors.paper).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Text(
                stringResource(R.string.welcome_chip_mindful), style = ReelType.smallBold.copy(fontSize = 10.sp), color = colors.ink,
                modifier = Modifier.align(Alignment.BottomEnd).offset((-8).dp, (-34).dp).rotate(8f).shadow(8.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp)).background(colors.paper).padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Text(stringResource(R.string.welcome_headline), style = ReelType.display, color = colors.ink, modifier = Modifier.padding(top = 22.dp))
        Text(stringResource(R.string.welcome_tagline), style = ReelType.label.copy(fontSize = 15.sp), color = colors.mintInk, modifier = Modifier.padding(top = 8.dp))
        Text(stringResource(R.string.welcome_body), style = ReelType.body, color = colors.muted, modifier = Modifier.padding(top = 10.dp, bottom = 22.dp))
        ReelButton(stringResource(R.string.welcome_get_started), onGetStarted, trailingArrow = true)
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.welcome_how_it_works), { showHow = true }, kind = ButtonKind.GHOST)
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.welcome_privacy_details), onPrivacy, kind = ButtonKind.GHOST, icon = ReelIcons.Lock)
        FooterCaption(stringResource(R.string.welcome_footer))
    }
    if (showHow) HowTrackingWorksDialog { showHow = false }
}

@Composable
fun HowTrackingWorksDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_got_it)) } },
        title = { Text(stringResource(R.string.how_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.how_p1), style = ReelType.caption)
                Text(stringResource(R.string.how_p2), style = ReelType.caption)
                Text(stringResource(R.string.how_p3), style = ReelType.caption)
                Text(stringResource(R.string.how_p4), style = ReelType.caption)
            }
        },
        containerColor = colors.paper,
        titleContentColor = colors.ink,
        textContentColor = colors.muted,
    )
}

/**
 * S02: prominent in-app Accessibility disclosure, separate from the privacy policy, with explicit
 * affirmative and negative actions (PRD §3 S02, §13). OS settings open only after "I agree".
 */
@Composable
fun SetupScreen(
    state: UiState,
    onBack: () -> Unit,
    onConsent: (Boolean) -> Unit,
    onShowAppTime: (Boolean) -> Unit,
    onPickGoal: (Int) -> Unit,
    onFinish: () -> Unit,
) {
    val ctx = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(if (state.prefs.hasValidConsent) 1 else 0) }
    var goal by rememberSaveable { mutableIntStateOf(if (state.prefs.goalConfirmed) state.prefs.combinedGoal else 100) }
    // key(step): every step starts scrolled to the top so its title and progress are visible.
    androidx.compose.runtime.key(step) { ScreenColumn(bottomBar = false) {
        PageHeader(stringResource(R.string.setup_title), stringResource(R.string.setup_step_of, step + 1, 3), onBack = { if (step > 0) step-- else onBack() })
        StepDots(step, 3)
        when (step) {
            0 -> {
                Box(Modifier.padding(vertical = 18.dp).size(60.dp).clip(RoundedCornerShape(21.dp)).background(colors.mint), contentAlignment = Alignment.Center) {
                    Icon(ReelIcons.Shield, null, Modifier.size(26.dp), tint = colors.mintInk)
                }
                Text(stringResource(R.string.disclosure_title), style = ReelType.headline.copy(fontSize = 22.sp), color = colors.ink)
                Text(stringResource(R.string.disclosure_intro), style = ReelType.caption, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                Card(Modifier.padding(top = 14.dp)) {
                    SettingRow(stringResource(R.string.disclosure_observe_title), stringResource(R.string.disclosure_observe_desc), leading = { RowIcon(ReelIcons.Eye) })
                    SettingRow(stringResource(R.string.disclosure_store_title), stringResource(R.string.disclosure_store_desc), leading = { RowIcon(ReelIcons.Check) })
                    SettingRow(stringResource(R.string.disclosure_never_title), stringResource(R.string.disclosure_never_desc), leading = { RowIcon(ReelIcons.Lock) })
                    SettingRow(stringResource(R.string.disclosure_share_title), stringResource(R.string.disclosure_share_desc), leading = { RowIcon(ReelIcons.Shield) })
                    SettingRow(stringResource(R.string.disclosure_local_title), stringResource(R.string.disclosure_local_desc), leading = { RowIcon(ReelIcons.Smartphone) })
                    SettingRow(stringResource(R.string.disclosure_purpose_title), stringResource(R.string.disclosure_purpose_desc), leading = { RowIcon(ReelIcons.Heart) }, divider = false)
                }
                Callout(stringResource(R.string.disclosure_limits), ReelIcons.Alert, warm = true)
                ReelButton(stringResource(R.string.disclosure_agree), { onConsent(true); step = 1 }, trailingArrow = true)
                Spacer(Modifier.height(9.dp))
                ReelButton(stringResource(R.string.disclosure_decline), { onConsent(false); step = 2 }, kind = ButtonKind.GHOST)
                FooterCaption(stringResource(R.string.disclosure_revoke_note))
            }
            1 -> {
                Text(stringResource(R.string.setup_enable_title), style = ReelType.headline.copy(fontSize = 22.sp), color = colors.ink, modifier = Modifier.padding(top = 22.dp))
                if (state.system.serviceEnabled) {
                    Card(Modifier.padding(top = 14.dp), background = colors.mint, bordered = false) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            NumberBadge(1, done = true)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(R.string.setup_enabled_title), style = ReelType.label, color = colors.mintInk)
                                Text(stringResource(R.string.setup_enabled_body), style = ReelType.small, color = colors.mintInk)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ReelButton(stringResource(R.string.setup_next_goal), { step = 2 }, trailingArrow = true)
                } else {
                    Text(stringResource(R.string.setup_enable_intro), style = ReelType.caption, color = colors.muted, modifier = Modifier.padding(top = 8.dp))
                    Card(Modifier.padding(top = 14.dp)) {
                        listOf(R.string.enable_step_1, R.string.enable_step_2, R.string.enable_step_3, R.string.enable_step_4, R.string.enable_step_5)
                            .forEachIndexed { i, res ->
                                Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    NumberBadge(i + 1, done = false)
                                    Spacer(Modifier.width(12.dp))
                                    Text(stringResource(res), style = ReelType.body.copy(fontSize = 14.sp), color = colors.ink)
                                }
                            }
                    }
                    Spacer(Modifier.height(16.dp))
                    ReelButton(stringResource(R.string.setup_open_settings), { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, icon = ReelIcons.Settings)
                    RestrictedSettingsHelp()
                    Spacer(Modifier.height(9.dp))
                    ReelButton(stringResource(R.string.setup_later), { step = 2 }, kind = ButtonKind.GHOST)
                }
                SectionTitle(stringResource(R.string.setup_optional_title))
                Card {
                    SettingRow(stringResource(R.string.perm_usage_title), stringResource(R.string.perm_usage_desc), leading = { RowIcon(ReelIcons.Clock) }, divider = false) {
                        ReelSwitch(state.prefs.showAppForegroundTime, { on ->
                            onShowAppTime(on)
                            if (on && !state.system.usageAccess) ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }, stringResource(R.string.perm_usage_title))
                    }
                }
            }
            else -> {
                Text(stringResource(R.string.setup_goal_title), style = ReelType.headline.copy(fontSize = 22.sp), color = colors.ink, modifier = Modifier.padding(top = 22.dp))
                Text(stringResource(R.string.setup_goal_body), style = ReelType.caption, color = colors.muted, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                GoalPresetPicker(goal) { goal = it }
                Callout(stringResource(R.string.setup_goal_hint))
                ReelButton(stringResource(R.string.setup_finish), { onPickGoal(goal); onFinish() }, trailingArrow = true)
            }
        }
    } }
}

/**
 * Android 13+ blocks enabling Accessibility for apps installed outside an app store until the user
 * allows "restricted settings" in App info. Shown only in the website (APK) build.
 */
@Composable
fun RestrictedSettingsHelp() {
    if (com.reelcounter.app.BuildConfig.DISTRIBUTION != "website" || android.os.Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    Card(Modifier.padding(top = 12.dp), background = colors.peach, bordered = false) {
        Text(stringResource(R.string.restricted_settings_title), style = ReelType.label, color = colors.peachInk)
        Text(stringResource(R.string.restricted_settings_body), style = ReelType.small, color = colors.peachInk, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        ReelButton(stringResource(R.string.open_app_info), {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${ctx.packageName}")))
        }, kind = ButtonKind.GHOST)
    }
}
