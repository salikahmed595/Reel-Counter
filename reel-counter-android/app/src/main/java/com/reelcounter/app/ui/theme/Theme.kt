package com.reelcounter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.reelcounter.app.core.Platform
import com.reelcounter.app.data.prefs.ThemeMode

/**
 * Semantic tokens from the approved prototype (warm ivory, sage, lavender, peach, sky, ink).
 * Muted text values are darkened vs. the mock so small text meets WCAG 2.2 AA (PRD §4).
 */
@Immutable
data class ReelColors(
    val bg: Color,
    val paper: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val line: Color,
    val mint: Color,
    val mintSoft: Color,
    val mintDeep: Color,
    val mintInk: Color,
    val lav: Color,
    val lavInk: Color,
    val peach: Color,
    val peachInk: Color,
    val sky: Color,
    val skyInk: Color,
    val chip: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val goalFill: Color,
    val goalTrack: Color,
    val switchOn: Color,
    val switchOff: Color,
    val proStart: Color,
    val proEnd: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val navBar: Color,
    val dark: Boolean,
)

val LightReel = ReelColors(
    bg = Color(0xFFF9FAF6), paper = Color(0xFFFFFFFF), ink = Color(0xFF23302B), muted = Color(0xFF5B6B62),
    faint = Color(0xFF7A877F), line = Color(0xFFECEFEA), mint = Color(0xFFE3F0E5), mintSoft = Color(0xFFE8F1E8),
    mintDeep = Color(0xFF3E7962), mintInk = Color(0xFF2F6A4C), lav = Color(0xFFEEE8F6), lavInk = Color(0xFF5E4D82),
    peach = Color(0xFFF8EAE1), peachInk = Color(0xFF8E5537), sky = Color(0xFFE8EDF7), skyInk = Color(0xFF3F5F88),
    chip = Color(0xFFEDF0EB), heroStart = Color(0xFFE0F0E5), heroEnd = Color(0xFFEDF6E9), goalFill = Color(0xFF548A6A),
    goalTrack = Color(0xB3FFFFFF), switchOn = Color(0xFF5E9E78), switchOff = Color(0xFFDEE2DC),
    proStart = Color(0xFF304B3D), proEnd = Color(0xFF25372D), primaryButton = Color(0xFF23302B), onPrimaryButton = Color.White,
    navBar = Color(0xFFFFFFFF), dark = false,
)

val DarkReel = ReelColors(
    bg = Color(0xFF121915), paper = Color(0xFF1B231F), ink = Color(0xFFE8F0EA), muted = Color(0xFFADBBB3),
    faint = Color(0xFF8E9C94), line = Color(0xFF2A3430), mint = Color(0xFF1F3328), mintSoft = Color(0xFF203029),
    mintDeep = Color(0xFF8FD1A8), mintInk = Color(0xFF9CDCB4), lav = Color(0xFF2A2536), lavInk = Color(0xFFCDBDF0),
    peach = Color(0xFF35271F), peachInk = Color(0xFFF0C1A6), sky = Color(0xFF1F2836), skyInk = Color(0xFFB4CBEE),
    chip = Color(0xFF253029), heroStart = Color(0xFF1E3428), heroEnd = Color(0xFF1A2B22), goalFill = Color(0xFF8FD1A8),
    goalTrack = Color(0x33FFFFFF), switchOn = Color(0xFF5E9E78), switchOff = Color(0xFF3A4540),
    proStart = Color(0xFF2B4537), proEnd = Color(0xFF1B2A22), primaryButton = Color(0xFFDDEFE2), onPrimaryButton = Color(0xFF16201B),
    navBar = Color(0xFF1B231F), dark = true,
)

val LocalReelColors = staticCompositionLocalOf { LightReel }
val LocalReducedMotion = staticCompositionLocalOf { false }

object ReelType {
    val hero = TextStyle(fontSize = 80.sp, lineHeight = 84.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.08).em)
    val headline = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em)
    val display = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em)
    val sectionHeading = TextStyle(fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.045).em)
    val sectionTitle = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em)
    val metric = TextStyle(fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em)
    val body = TextStyle(fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
    val small = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val smallBold = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold)
    val button = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold)
}

data class PlatformColors(val bg: Color, val ink: Color, val chart: Color, val glyph: String)

fun platformColors(p: Platform, dark: Boolean): PlatformColors = when (p) {
    Platform.INSTAGRAM -> PlatformColors(if (dark) Color(0xFF3A2530) else Color(0xFFF9E5EC), if (dark) Color(0xFFF2A9C1) else Color(0xFFA34B69), Color(0xFFA3C8B1), "IG")
    Platform.YOUTUBE -> PlatformColors(if (dark) Color(0xFF3A2724) else Color(0xFFF9E7E3), if (dark) Color(0xFFF4B0A4) else Color(0xFFB05445), Color(0xFFDFB1BE), "YT")
    Platform.FACEBOOK -> PlatformColors(if (dark) Color(0xFF232C3D) else Color(0xFFE4EBFB), if (dark) Color(0xFFA9C3EE) else Color(0xFF4B71A6), Color(0xFFADC1E5), "FB")
    Platform.TIKTOK -> PlatformColors(if (dark) Color(0xFF2B2836) else Color(0xFFECE9F3), if (dark) Color(0xFFCAC4E6) else Color(0xFF524E72), Color(0xFFC7BEE1), "TT")
}

@Composable
fun ReelTheme(mode: ThemeMode = ThemeMode.SYSTEM, reducedMotion: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val c = if (dark) DarkReel else LightReel
    val scheme = if (dark) {
        darkColorScheme(primary = c.mintDeep, onPrimary = c.bg, background = c.bg, surface = c.paper, onBackground = c.ink, onSurface = c.ink, outline = c.line)
    } else {
        lightColorScheme(primary = c.mintDeep, onPrimary = Color.White, background = c.bg, surface = c.paper, onBackground = c.ink, onSurface = c.ink, outline = c.line)
    }
    CompositionLocalProvider(LocalReelColors provides c, LocalReducedMotion provides reducedMotion) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
