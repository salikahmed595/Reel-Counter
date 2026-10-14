package com.reelcounter.app.ui.screens

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.reelcounter.app.R
import com.reelcounter.app.ui.MainViewModel
import com.reelcounter.app.ui.components.ReelIcons
import com.reelcounter.app.ui.components.colors
import com.reelcounter.app.ui.theme.LocalReducedMotion
import com.reelcounter.app.ui.theme.ReelType

object Routes {
    const val WELCOME = "welcome"
    const val SETUP = "setup"
    const val HOME = "home"
    const val INSIGHTS = "insights"
    const val GOALS = "goals"
    const val WIDGETS = "widgets"
    const val TRACKING = "tracking"
    const val OVERLAY = "overlay"
    const val SETTINGS = "settings"
    const val PRIVACY = "privacy"
    const val PRO = "pro"
    const val STATES = "states"

    val TABS = setOf(HOME, INSIGHTS, GOALS, WIDGETS)
    val ALL = setOf(WELCOME, SETUP, HOME, INSIGHTS, GOALS, WIDGETS, TRACKING, OVERLAY, SETTINGS, PRIVACY, PRO, STATES)
}

@Composable
fun AppRoot(vm: MainViewModel, initialRoute: String?) {
    val state by vm.state.collectAsStateWithLifecycle()
    if (!state.loaded) {
        Box(Modifier.fillMaxSize().background(colors.bg))
        return
    }
    val nav = rememberNavController()
    val start = androidx.compose.runtime.remember { if (state.prefs.onboardingDone) (initialRoute?.takeIf { it in Routes.ALL } ?: Routes.HOME) else Routes.WELCOME }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val reduced = LocalReducedMotion.current
    val ctx = LocalContext.current

    fun go(r: String) = nav.navigateTab(r)
    fun back() { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        NavHost(
            nav, startDestination = start,
            enterTransition = { if (reduced) EnterTransition.None else fadeIn() },
            exitTransition = { if (reduced) ExitTransition.None else fadeOut() },
        ) {
            composable(Routes.WELCOME) { WelcomeScreen(onGetStarted = { nav.navigate(Routes.SETUP) }, onPrivacy = { nav.navigate(Routes.PRIVACY) }) }
            composable(Routes.SETUP) {
                SetupScreen(
                    state, onBack = { back() }, onConsent = vm::recordConsent, onShowAppTime = vm::setShowAppTime, onPickGoal = vm::setQuickGoal,
                    onFinish = {
                        vm.finishOnboarding()
                        nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.HOME) { HomeScreen(state, ::go, vm::setGlobalPaused, vm::setPlatformPaused, vm::setQuickGoal, vm::dismissGuide) }
            composable(Routes.INSIGHTS) {
                val today by vm.transitionsToday.collectAsStateWithLifecycle()
                val month by vm.transitions30d.collectAsStateWithLifecycle()
                InsightsScreen(state, today, month, ::go, vm::markTipSeen)
            }
            composable(Routes.GOALS) {
                val saved = stringResource(R.string.goals_saved)
                GoalsScreen(
                    state, ::go, vm::saveGoals, vm::setReminders, vm::setThreshold, vm::setQuietHours,
                    onSaved = { Toast.makeText(ctx, saved, Toast.LENGTH_SHORT).show() },
                    onTipSeen = vm::markTipSeen,
                )
            }
            composable(Routes.WIDGETS) { WidgetsScreen(state, ::go, vm::markTipSeen) }
            composable(Routes.TRACKING) { TrackingScreen(state, { back() }, ::go, vm::setGlobalPaused, vm::setPlatformPaused, vm::markTipSeen) }
            composable(Routes.OVERLAY) { OverlayScreen(state, { back() }, vm::setOverlay, vm::setOverlayCooldown, vm::previewOverlay) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    state, vm.currentLanguage(), { back() }, ::go, vm::setLanguage, vm::setThemeMode, vm::setReducedMotion,
                    vm::setUpdateMode, vm::setShowAppTime, vm::loadDebugSample,
                    onReset = {
                        vm.resetApp()
                        nav.navigate(Routes.WELCOME) { popUpTo(0) { inclusive = true } }
                    },
                )
            }
            composable(Routes.PRIVACY) { PrivacyScreen(state, { back() }, vm::deleteAllActivity, { vm.recordConsent(false) }, ::go) }
            composable(Routes.PRO) {
                val plans by vm.billingPlans.collectAsStateWithLifecycle()
                val status by vm.billingStatus.collectAsStateWithLifecycle()
                ProScreen(state, plans, status, { back() }, vm::connectBilling, vm::purchase, vm::restorePurchases)
            }
            composable(Routes.STATES) { StatesScreen(state, { back() }, ::go) }
        }
        if (route in Routes.TABS) BottomBar(route!!, ::go, Modifier.align(Alignment.BottomCenter))
    }
}

private fun NavHostController.navigateTab(route: String) {
    if (route in Routes.TABS) {
        navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    } else {
        navigate(route) { launchSingleTop = true }
    }
}

@Composable
private fun BottomBar(current: String, onSelect: (String) -> Unit, modifier: Modifier) {
    val tabs: List<Triple<String, Int, ImageVector>> = listOf(
        Triple(Routes.HOME, R.string.tab_home, ReelIcons.Home),
        Triple(Routes.INSIGHTS, R.string.tab_insights, ReelIcons.Chart),
        Triple(Routes.GOALS, R.string.tab_goals, ReelIcons.Target),
        Triple(Routes.WIDGETS, R.string.tab_widgets, ReelIcons.Grid),
    )
    Column(modifier.fillMaxWidth().background(colors.navBar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { (r, label, icon) ->
                val sel = r == current
                Column(
                    Modifier.width(72.dp).clip(RoundedCornerShape(15.dp)).clickable(role = Role.Tab) { onSelect(r) }
                        .semantics { selected = sel }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(42.dp, 32.dp).clip(RoundedCornerShape(13.dp)).background(if (sel) colors.mint else colors.navBar.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(22.dp), tint = if (sel) colors.mintInk else colors.faint) }
                    Text(stringResource(label), style = ReelType.smallBold.copy(fontSize = 10.sp), color = if (sel) colors.mintInk else colors.faint, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
