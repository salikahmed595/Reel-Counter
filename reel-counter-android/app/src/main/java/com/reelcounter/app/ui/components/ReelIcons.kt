package com.reelcounter.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons ported from the approved prototype's SVG set (24×24, 1.8 stroke, round caps). */
object ReelIcons {
    private fun c(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"
    private fun r(x: Float, y: Float, w: Float, h: Float, rx: Float): String {
        val iw = w - 2 * rx
        val ih = h - 2 * rx
        return "M${x + rx},${y}h${iw}a$rx,$rx 0 0 1 $rx,${rx}v${ih}a$rx,$rx 0 0 1 ${-rx},${rx}h${-iw}a$rx,$rx 0 0 1 ${-rx},${-rx}v${-ih}a$rx,$rx 0 0 1 $rx,${-rx}z"
    }

    private fun icon(name: String, vararg paths: String, mirror: Boolean = false, fillDots: Boolean = false): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror)
        paths.forEach { d ->
            b.addPath(
                pathData = addPathNodes(d),
                fill = if (fillDots) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Home = icon("home", "m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z")
    val Chart = icon("chart", "M3 3v18h18", "m7 15 4-5 4 3 5-7")
    val Target = icon("target", c(12f, 12f, 9f), c(12f, 12f, 5f), c(12f, 12f, 1f))
    val Grid = icon("grid", r(3f, 3f, 7f, 7f, 2f), r(14f, 3f, 7f, 7f, 2f), r(3f, 14f, 7f, 7f, 2f), r(14f, 14f, 7f, 7f, 2f))
    val Settings = icon(
        "settings", c(12f, 12f, 3f),
        "M19.4 15a1.8 1.8 0 0 0 .36 2l-2 2a1.8 1.8 0 0 0-2-.36l-1.3.55a1.8 1.8 0 0 0-1.1 1.65h-2.8a1.8 1.8 0 0 0-1.1-1.65l-1.3-.55a1.8 1.8 0 0 0-2 .36l-2-2a1.8 1.8 0 0 0 .36-2L4 13.7a1.8 1.8 0 0 0-1.65-1.1V9.8A1.8 1.8 0 0 0 4 8.7l.55-1.3a1.8 1.8 0 0 0-.36-2l2-2a1.8 1.8 0 0 0 2 .36l1.3-.55a1.8 1.8 0 0 0 1.1-1.65h2.8a1.8 1.8 0 0 0 1.1 1.65l1.3.55a1.8 1.8 0 0 0 2-.36l2 2a1.8 1.8 0 0 0-.36 2l.55 1.3A1.8 1.8 0 0 0 21.65 9.8v2.8A1.8 1.8 0 0 0 20 13.7z",
    )
    val Bell = icon("bell", "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4")
    val Shield = icon("shield", "m12 22 7-4V6l-7-4-7 4v12z", "m9 12 2 2 4-4")
    val Pause = icon("pause", r(5f, 4f, 5f, 16f, 1f), r(14f, 4f, 5f, 16f, 1f))
    val Play = icon("play", "m8 4 12 8-12 8z")
    val Arrow = icon("arrow", "m5 12 14 0m-6-6 6 6-6 6", mirror = true)
    val Back = icon("back", "m15 18-6-6 6-6", mirror = true)
    val Check = icon("check", "m5 12 4 4L19 6")
    val Clock = icon("clock", c(12f, 12f, 9f), "M12 7v5l3 2")
    val Moon = icon("moon", "M20 15.1A8.7 8.7 0 0 1 8.9 4 9 9 0 1 0 20 15.1")
    val Info = icon("info", c(12f, 12f, 10f), "M12 11v6M12 7h.01")
    val Lock = icon("lock", r(5f, 10f, 14f, 11f, 2f), "M8 10V7a4 4 0 0 1 8 0v3")
    val Chevron = icon("chevron", "m9 18 6-6-6-6", mirror = true)
    val Globe = icon("globe", c(12f, 12f, 9f), "M3 12h18M12 3c-5 5-5 13 0 18M12 3c5 5 5 13 0 18")
    val Eye = icon("eye", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7-10-7-10-7z", c(12f, 12f, 3f))
    val Help = icon("help", c(12f, 12f, 10f), "M9 9a3 3 0 0 1 6 0c0 2-3 3-3 5M12 18h.01")
    val Trash = icon("trash", "M4 7h16M10 4h4M6 7l1 14h10l1-14M10 11v6M14 11v6")
    val Sliders = icon("sliders", "M4 7h16M4 17h16", c(9f, 7f, 3f), c(15f, 17f, 3f))
    val Sparkles = icon("sparkles", "m11 2 2 7 7 2-7 2-2 7-2-7-7-2 7-2zM20 17l1 3 3 1-3 1-1 3-1-3-3-1 3-1z")
    val Crown = icon("crown", "M2 8 7 13l5-8 5 8 5-5-2 12H4z")
    val Smartphone = icon("smartphone", r(6f, 2f, 12f, 20f, 3f), "M11 18h2")
    val Activity = icon("activity", "M2 12h4l3-7 5 14 3-7h5")
    val Heart = icon("heart", "M20.8 4.5a5.7 5.7 0 0 0-8.1 0l-.7.7-.7-.7a5.7 5.7 0 0 0-8.1 8L12 21l8.8-8.5a5.7 5.7 0 0 0 0-8z")
    val Alert = icon("alert", "m12 3 10 18H2z", "M12 9v5M12 18h.01")
    val Refresh = icon("refresh", "M20 7v5h-5M4 17v-5h5", "M5 9a8 8 0 0 1 14-2l1 5M4 12l1 5a8 8 0 0 0 14-2")
    val External = icon("external", "M13 4h7v7M20 4l-9 9M19 14v6H4V5h6", mirror = true)
    val Battery = icon("battery", r(2f, 6f, 18f, 12f, 3f), "M22 10v4M7 12h8")
    val Edit = icon("edit", "m3 21 5-.9L21 7a2.1 2.1 0 0 0-3-3L5 17z")
    val Leaf = icon("leaf", "M5 21c0-9 5-15 15-16-1 10-7 15-15 16z", "M5 21 14 12")
}
