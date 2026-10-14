package com.reelcounter.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.app.ui.MainViewModel
import com.reelcounter.app.ui.screens.AppRoot
import com.reelcounter.app.ui.theme.ReelTheme

/** AppCompatActivity so per-app language (English/Urdu, RTL) works on Android 10–12 too. */
class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialRoute = intent?.getStringExtra(EXTRA_ROUTE)
        // Reconcile the cached Pro entitlement with Google Play on launch (offline-safe, PRD §11).
        if (savedInstanceState == null && BuildConfig.BILLING_ENABLED) vm.connectBilling()
        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            ReelTheme(state.prefs.themeMode, state.prefs.reducedMotion) {
                AppRoot(vm, initialRoute)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshSystemState()
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
