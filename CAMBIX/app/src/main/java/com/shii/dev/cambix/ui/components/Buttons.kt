package com.shii.dev.cambix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .tap(enabled = enabled, role = Role.Button, onClick = onClick)
            .clip(CircleShape)
            .background(if (primary) Cx.OnGlass else Cx.GraphiteHi)
            .then(if (primary) Modifier else Modifier.border(1.dp, Cx.EdgeLo, CircleShape))
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        CxText(
            text,
            size = 15.sp,
            weight = FontWeight.SemiBold,
            color = if (!enabled) Cx.OnGlassDim else if (primary) Cx.Black else Cx.OnGlass,
        )
    }
}
