package com.reelcounter.app.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.reelcounter.app.MainActivity
import com.reelcounter.app.R
import com.reelcounter.app.ReelCounterApp
import com.reelcounter.app.data.prefs.WidgetConfig
import com.reelcounter.app.ui.MainViewModel
import com.reelcounter.app.ui.components.ButtonKind
import com.reelcounter.app.ui.components.PageHeader
import com.reelcounter.app.ui.components.ReelButton
import com.reelcounter.app.ui.components.ScreenColumn
import com.reelcounter.app.ui.screens.WidgetConfigurator
import com.reelcounter.app.ui.screens.WidgetPreview
import com.reelcounter.app.ui.theme.ReelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Per-instance configuration (metric, scope, theme, estimates) keyed by appWidgetId (PRD §3 S06). */
class WidgetConfigActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val container = (application as ReelCounterApp).container
        val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)
        val layout = when (info?.provider?.className) {
            SmallWidgetReceiver::class.java.name -> WidgetLayout.SMALL
            LargeWidgetReceiver::class.java.name -> WidgetLayout.LARGE
            else -> WidgetLayout.MEDIUM
        }

        setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            ReelTheme(state.prefs.themeMode, state.prefs.reducedMotion) {
                var config by remember { mutableStateOf<WidgetConfig?>(null) }
                androidx.compose.runtime.LaunchedEffect(appWidgetId) { config = container.prefsRepo.widgetConfig(appWidgetId) }
                val current = config ?: return@ReelTheme
                ScreenColumn(bottomBar = false) {
                    PageHeader(stringResource(R.string.widget_config_title), stringResource(R.string.widget_config_caption))
                    WidgetPreview(state, current, layout)
                    WidgetConfigurator(current, state.isPro, { config = it }, {
                        startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "pro"))
                    })
                    Spacer(Modifier.height(18.dp))
                    ReelButton(stringResource(R.string.widget_config_save), {
                        lifecycleScope.launch {
                            container.prefsRepo.setWidgetConfig(appWidgetId, current)
                            container.widgetUpdater.updateAllNow()
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                            finish()
                        }
                    })
                    Spacer(Modifier.height(9.dp))
                    ReelButton(stringResource(R.string.action_cancel), { finish() }, kind = ButtonKind.GHOST)
                }
            }
        }
    }
}

/** Receives the appWidgetId of a widget pinned from the in-app studio and stores its chosen config. */
class WidgetPinnedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val config = WidgetConfig.decode(intent.getStringExtra(EXTRA_CONFIG))
        val c = (context.applicationContext as ReelCounterApp).container
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                c.prefsRepo.setWidgetConfig(id, config)
                c.widgetUpdater.updateAllNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_CONFIG = "widget_config"
    }
}
