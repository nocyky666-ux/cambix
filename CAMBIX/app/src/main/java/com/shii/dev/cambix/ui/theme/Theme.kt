package com.shii.dev.cambix.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

object Cx {
    val Black = Color(0xFF000000)
    val Graphite = Color(0xFF1B1C20)
    val GraphiteHi = Color(0xFF2A2B31)
    val OnGlass = Color(0xFFF4F5F7)
    val OnGlassDim = Color(0xFFB4B7BF)
    val Titanium = Color(0xFFC9CBD1)
    val Accent = Color(0xFF4D9BFF)
    val Record = Color(0xFFFF453A)
    val Warn = Color(0xFFFFB84D)
    val Ok = Color(0xFF5BD68A)

    // Dark smoked glass: the 60% underlay keeps text above 4.5:1 even over a white sky.
    val GlassUnderlay = Color(0x99000000)
    val GlassHi = Color(0x1AFFFFFF)
    val GlassLo = Color(0x0AFFFFFF)
    val EdgeHi = Color(0x99FFFFFF)
    val EdgeLo = Color(0x1FFFFFFF)
}

@Composable
fun CxText(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Medium,
    color: Color = Cx.OnGlass,
    align: TextAlign = TextAlign.Start,
    maxLines: Int = Int.MAX_VALUE,
    letterSpacing: TextUnit = TextUnit.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = weight,
            textAlign = align,
            letterSpacing = letterSpacing,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun CambixTheme(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Cx.Black)) { content() }
}
