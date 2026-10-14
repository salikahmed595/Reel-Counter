package com.reelcounter.app.reminders

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.reelcounter.app.MainActivity
import com.reelcounter.app.R
import com.reelcounter.app.core.Localized
import com.reelcounter.app.core.nameRes
import com.reelcounter.app.tracking.Permissions

class AndroidReminderNotifier(private val appContext: Context) : ReminderNotifier {
    init {
        val ctx = Localized.context(appContext)
        val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = ctx.getString(R.string.notif_channel_desc)
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun canPost(): Boolean =
        Permissions.canPostNotifications(appContext) && NotificationManagerCompat.from(appContext).areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    override fun notifyDigest(crossings: List<Crossing>) {
        if (!canPost()) return
        val ctx = Localized.context(appContext)
        val top = crossings.maxBy { it.percent }
        val title = message(ctx, top)
        val body = if (crossings.size > 1) {
            crossings.sortedByDescending { it.percent }.joinToString("\n") { line(ctx, it) }
        } else {
            ctx.getString(R.string.notif_body_break)
        }
        val intent = PendingIntent.getActivity(
            appContext, 0,
            Intent(appContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_leaf)
            .setContentTitle(title)
            .setContentText(if (crossings.size > 1) line(ctx, top) else body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, n)
    }

    companion object {
        const val CHANNEL_ID = "goal_reminders"
        private const val NOTIFICATION_ID = 1001

        fun message(ctx: Context, c: Crossing): String = when (c.percent) {
            100 -> ctx.getString(R.string.notif_title_100)
            else -> ctx.getString(R.string.notif_title_pct, c.percent)
        }

        fun line(ctx: Context, c: Crossing): String {
            val scope = c.platform?.let { ctx.getString(it.nameRes()) } ?: ctx.getString(R.string.combined_scope)
            return ctx.getString(R.string.notif_line, scope, c.percent, c.count, c.goal)
        }
    }
}

/**
 * Restrained, dismissible, non-blocking bubble (PRD §3 S10, §10). Requires a separate
 * SYSTEM_ALERT_WINDOW grant; touches outside the bubble pass through; auto-hides after a few seconds.
 */
class AndroidOverlayPresenter(private val appContext: Context) : OverlayPresenter {
    private val main = Handler(Looper.getMainLooper())
    private var current: View? = null

    override fun canShow(): Boolean = Permissions.canDrawOverlays(appContext)

    override fun show(crossing: Crossing) {
        main.post { showOnMain(message(crossing), Localized.context(appContext).getString(R.string.overlay_sub)) }
    }

    fun preview() {
        val ctx = Localized.context(appContext)
        main.post { showOnMain(ctx.getString(R.string.overlay_preview_title), ctx.getString(R.string.overlay_sub)) }
    }

    private fun message(c: Crossing): String {
        val ctx = Localized.context(appContext)
        return if (c.percent >= 100) ctx.getString(R.string.overlay_title_100) else ctx.getString(R.string.overlay_title_pct, c.percent)
    }

    private fun showOnMain(title: String, sub: String) {
        if (!canShow()) return
        hide()
        val ctx = Localized.context(appContext)
        val dp = { v: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, ctx.resources.displayMetrics).toInt() }
        val bubble = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(13f), dp(16f), dp(13f))
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(0xFFE7F5E9.toInt()) }
            elevation = dp(6f).toFloat()
            contentDescription = "$title. $sub"
            addView(TextView(ctx).apply { text = "🌿  $title"; setTextColor(0xFF2F5E45.toInt()); textSize = 13f; paint.isFakeBoldText = true })
            addView(TextView(ctx).apply { text = sub; setTextColor(0xFF4F7360.toInt()); textSize = 11f })
            setOnClickListener { hide() }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12f); y = dp(96f)
        }
        runCatching { appContext.getSystemService(WindowManager::class.java).addView(bubble, params) }
            .onSuccess { current = bubble; main.postDelayed({ hide() }, 6_000) }
    }

    fun hide() {
        val v = current ?: return
        current = null
        runCatching { appContext.getSystemService(WindowManager::class.java).removeView(v) }
    }
}
