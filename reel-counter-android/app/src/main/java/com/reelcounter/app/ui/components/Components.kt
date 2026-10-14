package com.reelcounter.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.core.Platform
import com.reelcounter.app.ui.theme.LocalReducedMotion
import com.reelcounter.app.ui.theme.LocalReelColors
import com.reelcounter.app.ui.theme.ReelType
import com.reelcounter.app.ui.theme.platformColors

val colors @Composable get() = LocalReelColors.current

/** Scrollable screen body with the prototype's 23dp gutters and room for the bottom bar. */
@Composable
fun ScreenColumn(bottomBar: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(top = 14.dp, bottom = if (bottomBar) 110.dp else 32.dp),
        content = content,
    )
}

@Composable
fun PageHeader(title: String, caption: String? = null, onBack: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            if (onBack != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onBack).padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(ReelIcons.Back, null, Modifier.size(14.dp), tint = colors.muted)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_back), style = ReelType.smallBold, color = colors.muted)
                }
            }
            Text(title, style = ReelType.sectionHeading, color = colors.ink, modifier = Modifier.padding(top = 5.dp, bottom = 6.dp).semantics { heading() })
            if (caption != null) Text(caption, style = ReelType.caption, color = colors.muted)
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun IconSquareButton(icon: ImageVector, description: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(colors.paper).border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = colors.ink)
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(6.dp).clip(CircleShape).background(Color(0xFFEC9A7C)))
    }
}

@Composable
fun Card(
    modifier: Modifier = Modifier,
    background: Color = colors.paper,
    bordered: Boolean = true,
    padding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(23.dp)
    var m = modifier.fillMaxWidth().clip(shape).background(background)
    if (bordered) m = m.border(1.dp, colors.line, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

enum class PillKind { OK, WARN, NEUTRAL, PRO }

@Composable
fun Pill(text: String, kind: PillKind = PillKind.OK, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        PillKind.OK -> colors.mint to colors.mintInk
        PillKind.WARN -> colors.peach to colors.peachInk
        PillKind.NEUTRAL -> colors.chip to colors.muted
        PillKind.PRO -> colors.lav to colors.lavInk
    }
    Text(
        text, style = ReelType.smallBold.copy(fontSize = 10.sp), color = fg,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

@Composable
fun ReelSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
        colors = SwitchDefaults.colors(
            checkedTrackColor = colors.switchOn,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = colors.switchOff,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
fun SettingRow(
    title: String,
    description: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = ReelType.label, color = colors.ink)
                if (description != null) Text(description, style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 3.dp))
            }
            if (trailing != null) {
                Spacer(Modifier.width(10.dp))
                trailing()
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
    }
}

@Composable
fun RowIcon(icon: ImageVector, tint: Color = colors.ink) = Icon(icon, null, Modifier.size(20.dp), tint = tint)

enum class ButtonKind { PRIMARY, SECONDARY, GHOST }

@Composable
fun ReelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    trailingArrow: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val (bg, fg) = when (kind) {
        ButtonKind.PRIMARY -> colors.primaryButton to colors.onPrimaryButton
        ButtonKind.SECONDARY -> colors.mint to colors.mintInk
        ButtonKind.GHOST -> colors.paper to colors.ink
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape).background(if (enabled) bg else bg.copy(alpha = 0.5f))
            .then(if (kind == ButtonKind.GHOST) Modifier.border(1.dp, colors.line, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp), tint = fg)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = ReelType.button, color = fg, textAlign = TextAlign.Center)
        if (trailingArrow) {
            Spacer(Modifier.width(8.dp))
            Icon(ReelIcons.Arrow, null, Modifier.size(15.dp), tint = fg)
        }
    }
}

@Composable
fun LinkButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = ReelType.smallBold, color = colors.mintInk)
        Spacer(Modifier.width(4.dp))
        Icon(ReelIcons.Arrow, null, Modifier.size(13.dp), tint = colors.mintInk)
    }
}

@Composable
fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ReelType.sectionTitle, color = colors.ink, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

@Composable
fun PlatformBadge(platform: Platform, size: Dp = 38.dp) {
    val pc = platformColors(platform, colors.dark)
    Box(Modifier.size(size).clip(RoundedCornerShape(13.dp)).background(pc.bg), contentAlignment = Alignment.Center) {
        Text(pc.glyph, color = pc.ink, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun Callout(text: String, icon: ImageVector = ReelIcons.Info, warm: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(19.dp))
            .background(if (warm) colors.peach else colors.chip).padding(15.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (warm) colors.peachInk else colors.muted)
        Spacer(Modifier.width(10.dp))
        Text(text, style = ReelType.small.copy(lineHeight = 17.sp), color = if (warm) colors.peachInk else colors.muted)
    }
}

@Composable
fun animatedFraction(target: Float): Float {
    val reduced = LocalReducedMotion.current
    val v by animateFloatAsState(target, if (reduced) snap() else tween(500), label = "fraction")
    return v
}

@Composable
fun ProgressRing(fraction: Float?, label: String, center: String, size: Dp = 112.dp, stroke: Dp = 13.dp, background: Color = colors.paper) {
    val f = animatedFraction((fraction ?: 0f).coerceIn(0f, 1f))
    val track = colors.line
    val fill = colors.goalFill
    Box(Modifier.size(size).semantics { contentDescription = "$center $label" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            drawCircle(background, radius = this.size.minDimension / 2)
            drawArc(track, 0f, 360f, false, topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s), style = Stroke(s))
            drawArc(fill, -90f, 360f * f, false, topLeft = Offset(s / 2, s / 2), size = Size(this.size.width - s, this.size.height - s), style = Stroke(s, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, style = ReelType.metric.copy(fontSize = if (size < 100.dp) 19.sp else 23.sp), color = colors.ink)
            Text(label, style = ReelType.small.copy(fontSize = 9.sp), color = colors.muted)
        }
    }
}

@Composable
fun GoalBar(fraction: Float, modifier: Modifier = Modifier, track: Color = colors.goalTrack, fill: Color = colors.goalFill, height: Dp = 7.dp) {
    val f = animatedFraction(fraction.coerceIn(0f, 1f))
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(RoundedCornerShape(20.dp)).background(fill))
    }
}

data class Bar(val value: Float, val label: String, val noData: Boolean = false, val partial: Boolean = false, val highlight: Boolean = false)

/** Bars for days with no tracking coverage render as dashed outlines, never as fabricated zeros. */
@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, height: Dp = 137.dp, description: String) {
    val max = (bars.maxOfOrNull { it.value } ?: 0f).coerceAtLeast(1f)
    val base = colors.mint
    val strong = colors.goalFill
    val outline = colors.faint
    Column(modifier.semantics { contentDescription = description }) {
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.Bottom) {
            bars.forEach { b ->
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val r = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                    if (b.noData) {
                        val h = 10.dp.toPx()
                        drawRoundRect(
                            outline, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r,
                            style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
                        )
                    } else {
                        val h = (size.height * (b.value / max)).coerceAtLeast(6.dp.toPx())
                        val c = if (b.highlight) strong else base
                        drawRoundRect(if (b.partial) c.copy(alpha = 0.55f) else c, topLeft = Offset(0f, size.height - h), size = Size(size.width, h), cornerRadius = r)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            bars.forEach { b ->
                Text(b.label, Modifier.weight(1f), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<Float, Color>>, center: String, size: Dp = 112.dp) {
    val total = slices.sumOf { it.first.toDouble() }.toFloat()
    val empty = colors.line
    val inner = colors.paper
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = 21.dp.toPx()
            val tl = Offset(s / 2, s / 2)
            val sz = Size(this.size.width - s, this.size.height - s)
            if (total <= 0f) {
                drawArc(empty, 0f, 360f, false, tl, sz, style = Stroke(s))
            } else {
                var start = -90f
                slices.forEach { (v, c) ->
                    val sweep = 360f * v / total
                    drawArc(c, start, sweep, false, tl, sz, style = Stroke(s))
                    start += sweep
                }
            }
            drawCircle(inner, radius = this.size.minDimension / 2 - s)
        }
        Text(center, style = ReelType.label.copy(fontSize = 14.sp), color = colors.ink)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(colors.chip).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (sel) colors.paper else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = ReelType.smallBold, color = if (sel) colors.mintInk else colors.muted)
            }
        }
    }
}

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Text(
                o, style = ReelType.smallBold, color = if (sel) colors.mintInk else colors.muted,
                modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(if (sel) colors.mint else colors.chip)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 15.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun MetricTile(
    modifier: Modifier,
    background: Color,
    icon: ImageVector,
    title: String?,
    value: String,
    label: String,
    onInfo: (() -> Unit)? = null,
) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(background).heightIn(min = 137.dp).padding(17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = colors.muted)
            if (title != null) {
                Spacer(Modifier.width(6.dp))
                Text(title, style = ReelType.smallBold, color = colors.muted, modifier = Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (onInfo != null) {
                val d = stringResource(R.string.cd_more_info)
                Box(
                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = if (colors.dark) 0.08f else 0.6f))
                        .clickable(role = Role.Button, onClick = onInfo).semantics { contentDescription = d },
                    contentAlignment = Alignment.Center,
                ) { Icon(ReelIcons.Info, null, Modifier.size(14.dp), tint = colors.muted) }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = ReelType.metric, color = colors.ink)
        Text(label, style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun FooterCaption(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(top = 18.dp), style = ReelType.small.copy(fontSize = 10.sp), color = colors.faint, textAlign = TextAlign.Center)
}

@Composable
fun EmptyArt(icon: ImageVector) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 20.dp).height(180.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.radialGradient(listOf(colors.mint, colors.bg))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(64.dp), tint = colors.mintDeep) }
}

@Composable
fun LiveDot(color: Color) {
    Box(Modifier.size(13.dp).clip(CircleShape).background(color.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
    }
}
