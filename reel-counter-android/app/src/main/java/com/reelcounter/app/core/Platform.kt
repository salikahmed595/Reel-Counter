package com.reelcounter.app.core

/**
 * Supported short-form platforms. Package matching uses an exact allowlist (PRD §5.3).
 * Display names are localized through string resources, never hardcoded in UI.
 */
enum class Platform(val key: String, val packages: Set<String>, val abbr: String) {
    INSTAGRAM("instagram", setOf("com.instagram.android"), "IG"),
    YOUTUBE("youtube", setOf("com.google.android.youtube"), "YT"),
    FACEBOOK("facebook", setOf("com.facebook.katana"), "FB"),
    TIKTOK("tiktok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), "TT");

    companion object {
        val ALL_PACKAGES: Set<String> = entries.flatMap { it.packages }.toSet()

        fun forPackage(packageName: String?): Platform? =
            if (packageName == null) null else entries.firstOrNull { packageName in it.packages }

        fun fromKey(key: String): Platform? = entries.firstOrNull { it.key == key }
    }
}
