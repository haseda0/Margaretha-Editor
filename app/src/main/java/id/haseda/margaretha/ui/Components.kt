package id.haseda.margaretha.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Round icon control with a 48dp touch target and an accessible label. */
@Composable
fun IconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MColors.Text,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .alpha(if (enabled) 1f else 0.32f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Bottom toolbar tool: a raised key with its label engraved underneath. Pressing pushes the key in. */
@Composable
fun ToolButton(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, danger: Boolean = false, active: Boolean = false) {
    val color = if (danger) MColors.Danger else if (active) MColors.AccentBright else MColors.Text
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier = Modifier
            .width(68.dp)
            .clickable(enabled = enabled, role = Role.Button, interactionSource = interaction, indication = null, onClick = onClick)
            .alpha(if (enabled) 1f else 0.3f)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp).raised(12.dp, pressed = pressed, lit = active), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(top = if (pressed) 1.dp else 0.dp).size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (danger) MColors.Danger else MColors.TextMuted, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** Primary pill — the single warm accent action on a screen. */
@Composable
fun AccentPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(
                if (enabled) Brush.verticalGradient(listOf(MColors.AccentBright, MColors.Accent))
                else Brush.verticalGradient(listOf(MColors.Surface3, MColors.Surface3)),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) MColors.OnAccent else MColors.TextFaint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) MColors.OnAccent else MColors.TextFaint)
    }
}

/** Secondary quiet button. */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = MColors.Text) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(CircleShape)
            .background(MColors.Surface2)
            .border(1.dp, MColors.Line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Selectable option chip with an optional disabled reason. */
@Composable
fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, sub: String? = null) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 64.dp, minHeight = 44.dp)
            .clip(shape)
            .background(if (selected) MColors.Accent.copy(alpha = 0.16f) else MColors.Surface2)
            .border(1.dp, if (selected) MColors.Accent else MColors.Line, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (selected) MColors.AccentBright else MColors.Text)
        if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint)
    }
}

val SheetPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)

/** Small pill used for choices inside tool panels (tighter than [OptionChip]). */
@Composable
fun MiniChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 36.dp)
            .clip(shape)
            .background(if (selected) MColors.Accent.copy(alpha = 0.16f) else MColors.Surface2)
            .border(1.dp, if (selected) MColors.Accent else MColors.Line, shape)
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (selected) MColors.AccentBright else MColors.Text, maxLines = 1)
    }
}

/**
 * Labelled slider with a live value read-out. [onChange] fires while dragging, [onDone] once on
 * release; tapping the value resets it to [resetTo] when one is given.
 */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onChange: (Float) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    resetTo: Float? = null,
) {
    Row(modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MColors.TextMuted, modifier = Modifier.width(92.dp), maxLines = 1)
        GrooveSlider(
            value = value,
            range = range,
            onChange = onChange,
            onDone = onDone,
            modifier = Modifier.weight(1f),
            origin = resetTo ?: range.start,
            label = label,
            valueText = valueText,
        )
        val changed = resetTo != null && value != resetTo
        Text(
            valueText,
            style = TimecodeStyle,
            color = if (changed) MColors.AccentBright else MColors.TextFaint,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .width(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = changed, onClickLabel = "Kembalikan $label ke awal") {
                    if (resetTo != null) {
                        onChange(resetTo)
                        onDone()
                    }
                }
                .padding(vertical = 6.dp),
        )
    }
}

/** Round colour swatch; the selected one gets the accent ring. */
@Composable
fun Swatch(color: Color, selected: Boolean, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(2.dp, if (selected) MColors.Accent else Color.Transparent, CircleShape)
            .clickable(role = Role.RadioButton, onClickLabel = label, onClick = onClick)
            .padding(5.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MColors.LineStrong, CircleShape),
    )
}

/** Section caption inside a panel. */
@Composable
fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MColors.TextFaint, modifier = modifier.padding(top = 10.dp, bottom = 4.dp))
}

/**
 * Studio backdrop: deep base with one light source. Warm glow from the top-left, a cooler analog
 * glow bottom-right and a soft vignette at the bottom, all drawn once behind the content.
 */
fun Modifier.studioBackdrop(): Modifier = this
    .background(MColors.Base)
    .drawBehind {
        val d = maxOf(size.width, size.height)
        drawRect(Brush.radialGradient(listOf(MColors.Accent.copy(alpha = 0.10f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(0f, 0f), radius = d * 0.75f))
        drawRect(Brush.radialGradient(listOf(MColors.TrackSubtitle.copy(alpha = 0.08f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width, size.height), radius = d * 0.7f))
        drawRect(Brush.verticalGradient(0.7f to Color.Transparent, 1f to MColors.Abyss.copy(alpha = 0.55f)))
    }
