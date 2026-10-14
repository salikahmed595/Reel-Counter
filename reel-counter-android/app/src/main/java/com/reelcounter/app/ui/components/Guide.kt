package com.reelcounter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelcounter.app.R
import com.reelcounter.app.ui.theme.ReelType

/** Daily-limit presets offered to new users; they can fine-tune later in Goals. */
val GOAL_PRESETS = listOf(50 to R.string.preset_light, 100 to R.string.preset_balanced, 200 to R.string.preset_relaxed)

@Composable
fun NumberBadge(n: Int, done: Boolean) {
    Box(
        Modifier.size(30.dp).clip(CircleShape).background(if (done) colors.goalFill else colors.paper)
            .border(1.5.dp, if (done) colors.goalFill else colors.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(ReelIcons.Check, null, Modifier.size(16.dp), tint = Color.White)
        else Text("$n", style = ReelType.smallBold.copy(fontSize = 13.sp), color = colors.ink)
    }
}

/** One numbered, plain-language step with a single obvious action. */
@Composable
fun GuideStep(n: Int, title: String, description: String, done: Boolean, action: String? = null, onAction: (() -> Unit)? = null) {
    val doneLabel = stringResource(R.string.guide_done)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$n. $title. ${if (done) doneLabel else description}" },
        verticalAlignment = Alignment.Top,
    ) {
        NumberBadge(n, done)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, style = ReelType.label, color = if (done) colors.muted else colors.ink,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            if (!done) Text(description, style = ReelType.small, color = colors.muted, modifier = Modifier.padding(top = 2.dp))
            if (!done && action != null && onAction != null) {
                Text(
                    "$action  →", style = ReelType.smallBold, color = colors.onPrimaryButton,
                    modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(colors.primaryButton)
                        .clickable(role = Role.Button, onClick = onAction).padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

/** Big, tappable daily-limit choices — one tap, no typing. */
@Composable
fun GoalPresetPicker(selected: Int?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GOAL_PRESETS.forEach { (goal, label) ->
            val sel = selected == goal
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (sel) colors.mint else colors.paper)
                    .border(if (sel) 2.dp else 1.dp, if (sel) colors.goalFill else colors.line, RoundedCornerShape(18.dp))
                    .clickable(role = Role.RadioButton) { onPick(goal) }.semantics { this.selected = sel }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(label), style = ReelType.label, color = colors.ink)
                    Text(stringResource(R.string.preset_per_day, goal), style = ReelType.small, color = colors.muted)
                }
                Text("$goal", style = ReelType.metric.copy(fontSize = 24.sp), color = if (sel) colors.mintInk else colors.ink)
            }
        }
    }
}

/** One-sentence explanation shown the first time a screen is opened. */
@Composable
fun TipBanner(text: String, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp)).background(colors.sky).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(ReelIcons.Info, null, Modifier.size(18.dp), tint = colors.skyInk)
        Spacer(Modifier.width(10.dp))
        Text(text, style = ReelType.small.copy(lineHeight = 17.sp), color = colors.skyInk, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(R.string.action_got_it), style = ReelType.smallBold, color = colors.skyInk,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onDismiss).padding(8.dp),
        )
    }
}
