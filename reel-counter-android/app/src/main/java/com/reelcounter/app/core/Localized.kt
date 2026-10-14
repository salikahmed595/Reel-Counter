package com.reelcounter.app.core

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.reelcounter.app.R

/**
 * Resources honoring the in-app language override for non-Activity contexts (service, widgets,
 * notifications). API 33+ applies per-app locales system-wide; below that we wrap explicitly.
 */
object Localized {
    fun context(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locales[0])
        return base.createConfigurationContext(config)
    }
}

@StringRes
fun Platform.nameRes(): Int = when (this) {
    Platform.INSTAGRAM -> R.string.platform_instagram
    Platform.YOUTUBE -> R.string.platform_youtube
    Platform.FACEBOOK -> R.string.platform_facebook
    Platform.TIKTOK -> R.string.platform_tiktok
}
