package com.shii.dev.cambix.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText

fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp)): Modifier = this
    .clip(shape)
    .background(Cx.GlassUnderlay)
    .background(Brush.verticalGradient(listOf(Cx.GlassHi, Cx.GlassLo)))
    .border(1.dp, Brush.verticalGradient(listOf(Cx.EdgeHi, Cx.EdgeLo)), shape)

/** Tap target with a short scale-down so a press is felt without a ripple library. */
@Composable
fun Modifier.tap(enabled: Boolean = true, role: Role = Role.Button, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.93f else 1f, label = "press")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun GlassIconButton(
    icon: CxIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 48.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .tap(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .glass(CircleShape)
            .then(if (active) Modifier.border(1.5.dp, Cx.Accent, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        CxIconView(icon, tint = if (!enabled) Cx.OnGlassDim else if (active) Cx.Accent else Cx.OnGlass, size = size * 0.5f)
    }
}

@Composable
fun GlassSwitch(checked: Boolean, onChange: (Boolean) -> Unit, description: String, modifier: Modifier = Modifier) {
    val knobX by animateDpAsState(if (checked) 22.dp else 2.dp, label = "knob")
    Box(
        modifier
            .size(width = 56.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(CircleShape)
                .background(if (checked) Cx.Accent else Cx.GraphiteHi)
                .border(1.dp, Cx.EdgeLo, CircleShape),
        ) {
            Box(
                Modifier
                    .offset(x = knobX, y = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
fun <T> GlassSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabledOf: (T) -> Boolean = { true },
) {
    Row(modifier.glass(RoundedCornerShape(16.dp)).padding(4.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            val allowed = enabledOf(value)
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) Cx.OnGlass else Color.Transparent)
                    .selectable(selected = on, enabled = allowed, role = Role.RadioButton, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center,
            ) {
                CxText(
                    label,
                    size = 13.sp,
                    weight = FontWeight.SemiBold,
                    color = if (on) Cx.Black else if (allowed) Cx.OnGlass else Cx.OnGlassDim,
                )
            }
        }
    }
}

@Composable
fun GlassSlider(
    fraction: Float,
    onFraction: (Float) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(onFraction)
    Box(
        modifier
            .height(44.dp)
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                detectTapGestures { o -> current(((o.x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                val pad = 14.dp.toPx()
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    current(((change.position.x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(fraction.coerceIn(0f, 1f), 0f..1f)
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val pad = 14.dp.toPx()
            val w = size.width - pad * 2
            val x = pad + w * fraction.coerceIn(0f, 1f)
            drawLine(Color(0x66FFFFFF), Offset(pad, cy), Offset(pad + w, cy), 4.dp.toPx(), StrokeCap.Round)
            drawLine(Cx.OnGlass, Offset(pad, cy), Offset(x, cy), 4.dp.toPx(), StrokeCap.Round)
            drawCircle(Cx.OnGlass, 11.dp.toPx(), Offset(x, cy))
        }
    }
}

enum class Tab(val label: String, val icon: CxIcon) {
    CAMERA("Kamera", CxIcon.Camera),
    DISCOVER("Jelajah", CxIcon.Compass),
    STUDIO("Studio", CxIcon.Sliders),
    GALLERY("Galeri", CxIcon.Photos),
    SETTINGS("Setelan", CxIcon.Gear),
}

@Composable
fun GlassNavBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glass(RoundedCornerShape(28.dp))
            .padding(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            val on = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) Color(0x2EFFFFFF) else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) }),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CxIconView(tab.icon, tint = if (on) Cx.OnGlass else Cx.OnGlassDim, size = 24.dp)
                CxText(
                    tab.label,
                    size = 12.sp,
                    weight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) Cx.OnGlass else Cx.OnGlassDim,
                    maxLines = 1,
                )
            }
        }
    }
}
