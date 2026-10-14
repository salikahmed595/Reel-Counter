package com.reelcounter.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.BuildConfig
import com.reelcounter.app.R
import com.reelcounter.app.billing.BillingManager
import com.reelcounter.app.billing.BillingStatus
import com.reelcounter.app.billing.PlanInfo
import com.reelcounter.app.billing.ProductIds
import com.reelcounter.app.data.prefs.EntitlementState
import com.reelcounter.app.data.prefs.ThemeMode
import com.reelcounter.app.data.prefs.UpdateMode
import com.reelcounter.app.ui.UiState
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.Callout
import com.reelcounter.app.ui.components.Card
import com.reelcounter.app.ui.components.ChipRow
import com.reelcounter.app.ui.components.EmptyArt
import com.reelcounter.app.ui.components.FooterCaption
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.Pill
import com.reelcounter.app.ui.components.PillKind
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.ReelSwitch
import com.reelcounter.app.ui.components.RowIcon
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.components.SectionTitle
import com.reelcounter.app.ui.components.Segmented
import com.reelcounter.app.ui.components.SettingRow
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.ReelType

@Composable
private fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onConfirm(); onDismiss() }) { Text(confirm, color = colors.peachInk) } },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(title) },
        text = { Text(body) },
        containerColor = colors.paper, titleContentColor = colors.ink, textContentColor = colors.muted,
    )
}

@Composable
fun SettingsScreen(
    state: UiState,
    currentLanguage: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onReducedMotion: (Boolean) -> Unit,
    onUpdateMode: (UpdateMode) -> Unit,
    onShowAppTime: (Boolean) -> Unit,
    onDebugSample: () -> Unit,
    onReset: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = state.prefs
    var confirmReset by remember { mutableStateOf(false) }
    ScreenColumn {
        PageHeader(stringResource(R.string.settings_title), stringResource(R.string.settings_caption), onBack = onBack)
        Card(background = colors.mint, bordered = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(15.dp)).background(colors.paper.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                    Icon(ReelIcons.Leaf, null, Modifier.size(22.dp), tint = colors.mintInk)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.profile_title), style = ReelType.label, color = colors.ink)
                    Text(stringResource(R.string.profile_desc), style = ReelType.small, color = colors.muted)
                }
                Pill(stringResource(if (state.isPro) R.string.pro_badge else R.string.free_badge), if (state.isPro) PillKind.PRO else PillKind.OK)
            }
        }

        SectionTitle(stringResource(R.string.preferences))
        Card {
            SettingRow(stringResource(R.string.language_title), stringResource(R.string.language_desc), leading = { RowIcon(ReelIcons.Globe) }, divider = false)
            val langs = listOf("", "en", "ur-PK")
            Segmented(
                listOf(stringResource(R.string.lang_system), "English", "اردو"),
                langs.indexOfFirst { currentLanguage.startsWith(it.take(2)) && it.isNotEmpty() }.let { if (it < 0) 0 else it },
            ) { onLanguage(langs[it]) }
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.appearance), stringResource(R.string.appearance_desc), leading = { RowIcon(ReelIcons.Moon) }, divider = false)
            Segmented(
                listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)),
                ThemeMode.entries.indexOf(prefs.themeMode),
            ) { onTheme(ThemeMode.entries[it]) }
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.reduced_motion), stringResource(R.string.reduced_motion_desc), leading = { RowIcon(ReelIcons.Sliders) }) {
                ReelSwitch(prefs.reducedMotion, onReducedMotion, stringResource(R.string.reduced_motion))
            }
            SettingRow(stringResource(R.string.update_mode), stringResource(R.string.update_mode_desc), leading = { RowIcon(ReelIcons.Battery) }, divider = false)
            Segmented(
                listOf(stringResource(R.string.mode_balanced), stringResource(R.string.mode_responsive)),
                UpdateMode.entries.indexOf(prefs.updateMode),
            ) { onUpdateMode(UpdateMode.entries[it]) }
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.perm_usage_title), stringResource(R.string.perm_usage_desc), leading = { RowIcon(ReelIcons.Smartphone) }) {
                ReelSwitch(prefs.showAppForegroundTime, { on ->
                    onShowAppTime(on)
                    if (on && !state.system.usageAccess) ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }, stringResource(R.string.perm_usage_title))
            }
            SettingRow(stringResource(R.string.notifications_row), stringResource(R.string.notifications_row_desc), leading = { RowIcon(ReelIcons.Bell) }, onClick = { onNavigate(Routes.GOALS) }) {
                Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
            }
            SettingRow(stringResource(R.string.widgets_row), stringResource(R.string.widgets_row_desc), leading = { RowIcon(ReelIcons.Grid) }, onClick = { onNavigate(Routes.WIDGETS) }, divider = false) {
                Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
            }
        }

        SectionTitle(stringResource(R.string.privacy_support))
        Card {
            SettingRow(stringResource(R.string.privacy_row), stringResource(R.string.privacy_row_desc), leading = { RowIcon(ReelIcons.Shield) }, onClick = { onNavigate(Routes.PRIVACY) }) {
                Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
            }
            SettingRow(stringResource(R.string.tracking_title), stringResource(R.string.tracking_row_desc), leading = { RowIcon(ReelIcons.Activity) }, onClick = { onNavigate(Routes.TRACKING) }) {
                Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
            }
            SettingRow(stringResource(R.string.help_row), stringResource(R.string.help_row_desc), leading = { RowIcon(ReelIcons.Help) }, onClick = { onNavigate(Routes.STATES) }) {
                Icon(ReelIcons.Chevron, null, Modifier.size(14.dp), tint = colors.faint)
            }
            if (BuildConfig.PRIVACY_POLICY_URL.isNotEmpty()) {
                SettingRow(stringResource(R.string.privacy_policy), null, leading = { RowIcon(ReelIcons.Lock) }, onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL))) }
                }) { Icon(ReelIcons.External, null, Modifier.size(14.dp), tint = colors.faint) }
            }
            if (BuildConfig.SUPPORT_EMAIL.isNotEmpty()) {
                SettingRow(stringResource(R.string.support_title), stringResource(R.string.support_desc), leading = { RowIcon(ReelIcons.Help) }, onClick = {
                    runCatching {
                        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + BuildConfig.SUPPORT_EMAIL)).putExtra(Intent.EXTRA_SUBJECT, "Reel Counter ${BuildConfig.VERSION_NAME}"))
                    }
                }) { Icon(ReelIcons.External, null, Modifier.size(14.dp), tint = colors.faint) }
            }
            SettingRow(stringResource(R.string.about), stringResource(R.string.about_desc, BuildConfig.VERSION_NAME), leading = { RowIcon(ReelIcons.Info) })
            Text(stringResource(R.string.affiliation_disclaimer), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, modifier = Modifier.padding(vertical = 10.dp))
            if (BuildConfig.DEBUG) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
            if (BuildConfig.DEBUG) {
                SettingRow(stringResource(R.string.debug_sample), stringResource(R.string.debug_sample_desc), leading = { RowIcon(ReelIcons.Sparkles) }, onClick = onDebugSample, divider = false)
            }
        }
        Spacer(Modifier.height(14.dp))
        ReelButton(stringResource(R.string.explore_pro), { onNavigate(Routes.PRO) }, icon = ReelIcons.Sparkles)
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.reset_app), { confirmReset = true }, kind = ButtonKind.GHOST, icon = ReelIcons.Refresh)
        FooterCaption(stringResource(R.string.settings_footer))
    }
    if (confirmReset) ConfirmDialog(
        stringResource(R.string.reset_app), stringResource(R.string.reset_confirm_body), stringResource(R.string.reset_app),
        onReset, { confirmReset = false },
    )
}

@Composable
fun PrivacyScreen(state: UiState, onBack: () -> Unit, onDeleteAll: () -> Unit, onWithdrawConsent: () -> Unit, onNavigate: (String) -> Unit) {
    val ctx = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val sys = state.system
    ScreenColumn {
        PageHeader(stringResource(R.string.privacy_title), stringResource(R.string.privacy_caption), onBack = onBack)
        Card(background = colors.mint, bordered = false) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(colors.paper.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                Icon(ReelIcons.Lock, null, Modifier.size(22.dp), tint = colors.mintInk)
            }
            Text(stringResource(R.string.privacy_hero_title), style = ReelType.headline.copy(fontSize = 20.sp), color = colors.ink, modifier = Modifier.padding(top = 12.dp))
            Text(stringResource(R.string.privacy_hero_body), style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 6.dp))
        }

        SectionTitle(stringResource(R.string.what_we_store))
        Card {
            SettingRow(stringResource(R.string.store_counts), stringResource(R.string.store_counts_desc), leading = { RowIcon(ReelIcons.Check, colors.mintInk) })
            SettingRow(stringResource(R.string.store_settings), stringResource(R.string.store_settings_desc), leading = { RowIcon(ReelIcons.Check, colors.mintInk) })
            SettingRow(stringResource(R.string.store_never), stringResource(R.string.store_never_desc), leading = { RowIcon(ReelIcons.Lock, colors.peachInk) })
            SettingRow(stringResource(R.string.store_network), stringResource(R.string.store_network_desc), leading = { RowIcon(ReelIcons.Globe) }, divider = false)
        }

        SectionTitle(stringResource(R.string.data_permissions))
        Card {
            SettingRow(stringResource(R.string.activity_retention), stringResource(R.string.activity_retention_desc)) { Pill(stringResource(R.string.days_30), PillKind.NEUTRAL) }
            SettingRow(stringResource(R.string.perm_accessibility_title), stringResource(R.string.accessibility_privacy_desc), onClick = {
                if (state.prefs.hasValidConsent) ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) else onNavigate(Routes.SETUP)
            }) {
                Pill(stringResource(if (sys.serviceEnabled) R.string.status_on else R.string.status_off), if (sys.serviceEnabled) PillKind.OK else PillKind.NEUTRAL)
            }
            SettingRow(stringResource(R.string.perm_usage_title), stringResource(R.string.usage_privacy_desc), onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                Pill(stringResource(if (sys.usageAccess) R.string.status_on else R.string.optional), if (sys.usageAccess) PillKind.OK else PillKind.NEUTRAL)
            }
            SettingRow(stringResource(R.string.perm_notifications), stringResource(R.string.perm_notifications_desc), onClick = {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }) {
                Pill(stringResource(if (sys.notificationsAllowed) R.string.status_on else R.string.optional), if (sys.notificationsAllowed) PillKind.OK else PillKind.NEUTRAL)
            }
            SettingRow(stringResource(R.string.perm_overlay), stringResource(R.string.perm_overlay_desc), onClick = {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }, divider = false) {
                Pill(stringResource(if (sys.overlayAllowed) R.string.status_on else R.string.optional), if (sys.overlayAllowed) PillKind.OK else PillKind.NEUTRAL)
            }
        }

        SectionTitle(stringResource(R.string.take_control))
        Card {
            SettingRow(stringResource(R.string.delete_all), stringResource(if (deleted) R.string.delete_all_done else R.string.delete_all_desc)) {
                Text(
                    stringResource(R.string.action_delete), style = ReelType.smallBold, color = colors.peachInk,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { confirmDelete = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
            SettingRow(stringResource(R.string.withdraw_consent), stringResource(R.string.withdraw_consent_desc), divider = false) {
                Text(
                    stringResource(R.string.action_withdraw), style = ReelType.smallBold, color = colors.peachInk,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, colors.line, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, enabled = state.prefs.hasValidConsent) { confirmWithdraw = true }.padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
        if (BuildConfig.PRIVACY_POLICY_URL.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            ReelButton(stringResource(R.string.privacy_policy), {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
            }, kind = ButtonKind.GHOST, icon = ReelIcons.External)
        }
        Callout(stringResource(R.string.privacy_callout))
        ReelButton(stringResource(R.string.how_title), { onNavigate(Routes.STATES) }, kind = ButtonKind.GHOST, icon = ReelIcons.Help)
    }
    if (confirmDelete) ConfirmDialog(
        stringResource(R.string.delete_all), stringResource(R.string.delete_confirm_body), stringResource(R.string.action_delete),
        { onDeleteAll(); deleted = true }, { confirmDelete = false },
    )
    if (confirmWithdraw) ConfirmDialog(
        stringResource(R.string.withdraw_consent), stringResource(R.string.withdraw_confirm_body), stringResource(R.string.action_withdraw),
        {
            onWithdrawConsent()
            ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        { confirmWithdraw = false },
    )
}

@Composable
fun ProScreen(
    state: UiState,
    plans: Map<String, PlanInfo>,
    status: BillingStatus,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onPurchase: (Activity, String) -> Boolean,
    onRestore: () -> Unit,
) {
    val ctx = LocalContext.current
    var selected by rememberSaveable { mutableIntStateOf(1) }
    LaunchedEffect(Unit) { if (BuildConfig.BILLING_ENABLED) onConnect() }
    ScreenColumn {
        PageHeader(stringResource(R.string.pro_title), stringResource(R.string.pro_caption), onBack = onBack)
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(29.dp)).background(Brush.linearGradient(listOf(colors.proStart, colors.proEnd)))
                .heightIn(min = 230.dp).padding(horizontal = 23.dp, vertical = 25.dp),
        ) {
            Text("✦ " + stringResource(R.string.pro_kicker), style = ReelType.smallBold, color = Color(0xFFBBD9C4))
            Text(stringResource(R.string.pro_headline), style = ReelType.display.copy(fontSize = 33.sp), color = Color.White, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            Text(stringResource(R.string.pro_hero_body), style = ReelType.small, color = Color(0xFFC6DBC9))
        }

        if (state.isPro) {
            Card(Modifier.padding(top = 14.dp), background = colors.mint, bordered = false) {
                Text(stringResource(R.string.pro_active_title), style = ReelType.label, color = colors.mintInk)
                Text(stringResource(R.string.pro_active_body), style = ReelType.small, color = colors.mintInk, modifier = Modifier.padding(top = 4.dp))
            }
        }

        SectionTitle(stringResource(R.string.pro_extras))
        Card {
            listOf(
                ReelIcons.Sparkles to R.string.perk_themes,
                ReelIcons.Chart to R.string.perk_insights,
                ReelIcons.Sliders to R.string.perk_compare,
                ReelIcons.Refresh to R.string.perk_ongoing,
            ).forEachIndexed { i, (icon, s) ->
                SettingRow(stringResource(s), null, leading = {
                    Box(Modifier.size(31.dp).clip(RoundedCornerShape(11.dp)).background(colors.mintSoft), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(15.dp), tint = colors.mintInk)
                    }
                }, divider = i < 3)
            }
        }
        Callout(stringResource(R.string.pro_free_forever), ReelIcons.Heart)

        if (!BuildConfig.BILLING_ENABLED) {
            // Website (sideloaded) build: Play Billing cannot sell to non-Play installs.
            Card(Modifier.padding(top = 14.dp), background = colors.mint, bordered = false) {
                Text(stringResource(R.string.pro_website_title), style = ReelType.label, color = colors.mintInk)
                Text(stringResource(R.string.pro_website_body), style = ReelType.small, color = colors.mintInk, modifier = Modifier.padding(top = 4.dp))
            }
            return@ScreenColumn
        }
        if (!state.isPro) {
            SectionTitle(stringResource(R.string.choose_plan))
            listOf(
                Triple(ProductIds.MONTHLY, R.string.plan_monthly, R.string.plan_monthly_sub),
                Triple(ProductIds.YEARLY, R.string.plan_yearly, R.string.plan_yearly_sub),
            ).forEachIndexed { i, (id, name, sub) ->
                val sel = selected == i
                val plan = plans[id]
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 9.dp).clip(RoundedCornerShape(17.dp))
                        .background(if (sel) colors.mintSoft else colors.paper)
                        .border(if (sel) 2.dp else 1.dp, if (sel) colors.goalFill else colors.line, RoundedCornerShape(17.dp))
                        .clickable(role = Role.RadioButton) { selected = i }.semantics { this.selected = sel }.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(19.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, if (sel) colors.goalFill else colors.faint, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                        if (sel) Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(colors.goalFill))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(name), style = ReelType.label, color = colors.ink)
                        Text(stringResource(sub), style = ReelType.small, color = colors.muted)
                    }
                    Text(
                        plan?.formattedPrice?.let { stringResource(if (id == ProductIds.YEARLY) R.string.price_per_year else R.string.price_per_month, it) }
                            ?: stringResource(R.string.price_unavailable),
                        style = ReelType.smallBold, color = colors.mintInk, textAlign = TextAlign.End,
                    )
                }
            }
            Text(stringResource(R.string.subscription_terms), style = ReelType.small.copy(fontSize = 10.sp), color = colors.muted, modifier = Modifier.padding(vertical = 6.dp))
            val productId = if (selected == 0) ProductIds.MONTHLY else ProductIds.YEARLY
            ReelButton(
                stringResource(if (selected == 0) R.string.continue_monthly else R.string.continue_yearly),
                { (ctx as? Activity)?.let { onPurchase(it, productId) } },
                trailingArrow = true,
                enabled = plans[productId]?.offerToken != null && status != BillingStatus.VERIFYING,
            )
        }
        billingStatusText(status, state)?.let { Callout(it, ReelIcons.Info, warm = status == BillingStatus.ERROR || status == BillingStatus.VERIFICATION_UNAVAILABLE) }
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.restore_purchases), onRestore, kind = ButtonKind.GHOST, icon = ReelIcons.Refresh)
        Spacer(Modifier.height(9.dp))
        ReelButton(stringResource(R.string.manage_subscription), {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BillingManager.manageSubscriptionUrl(ctx.packageName, state.prefs.entitlement.productId))))
        }, kind = ButtonKind.GHOST, icon = ReelIcons.External)
        FooterCaption(stringResource(R.string.pro_footer))
    }
}

@Composable
private fun billingStatusText(status: BillingStatus, state: UiState): String? = when {
    state.prefs.entitlement.state == EntitlementState.PENDING -> stringResource(R.string.billing_pending)
    status == BillingStatus.UNAVAILABLE -> stringResource(R.string.billing_unavailable)
    status == BillingStatus.VERIFYING -> stringResource(R.string.billing_verifying)
    status == BillingStatus.VERIFICATION_UNAVAILABLE -> stringResource(R.string.billing_verification_unavailable)
    status == BillingStatus.ERROR -> stringResource(R.string.billing_error)
    status == BillingStatus.PURCHASED -> stringResource(R.string.billing_thanks)
    else -> null
}

/** S11: honest distinctions between "not tracking", "nothing detected", "unavailable", "estimated". */
@Composable
fun StatesScreen(state: UiState, onBack: () -> Unit, onNavigate: (String) -> Unit) {
    var idx by rememberSaveable { mutableIntStateOf(0) }
    ScreenColumn {
        PageHeader(stringResource(R.string.states_title), stringResource(R.string.states_caption), onBack = onBack)
        ChipRow(
            listOf(
                stringResource(R.string.state_chip_off), stringResource(R.string.state_chip_empty),
                stringResource(R.string.state_chip_unavailable), stringResource(R.string.state_chip_estimated),
            ),
            idx, { idx = it },
        )
        val (icon, title, body) = when (idx) {
            0 -> Triple(ReelIcons.Pause, R.string.state_off_title, R.string.state_off_body)
            1 -> Triple(ReelIcons.Leaf, R.string.state_empty_title, R.string.state_empty_body)
            2 -> Triple(ReelIcons.Alert, R.string.state_unavailable_title, R.string.state_unavailable_body)
            else -> Triple(ReelIcons.Activity, R.string.state_estimated_title, R.string.state_estimated_body)
        }
        EmptyArt(icon)
        Text(stringResource(title), style = ReelType.headline.copy(fontSize = 22.sp), color = colors.ink, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(stringResource(body), style = ReelType.body, color = colors.muted, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 22.dp), textAlign = TextAlign.Center)
        ReelButton(
            stringResource(if (idx == 2 || idx == 0) R.string.check_tracking_status else R.string.go_to_dashboard),
            { onNavigate(if (idx == 2 || idx == 0) Routes.TRACKING else Routes.HOME) },
            trailingArrow = true,
        )
        Callout(stringResource(R.string.force_stop_note))
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.current_status, healthLabel(state.health)), style = ReelType.small, color = colors.muted)
    }
}
