package com.shii.dev.cambix.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.camera.CapturePolicy
import com.shii.dev.cambix.grade.LookLibrary
import com.shii.dev.cambix.ui.components.GlassSegmented
import com.shii.dev.cambix.ui.components.GlassSlider
import com.shii.dev.cambix.ui.components.GlassSwitch
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun PanelSlider(label: String, value: String, fraction: Float, onFraction: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CxText(label, Modifier.widthIn(min = 80.dp), size = 13.sp, weight = FontWeight.SemiBold)
        GlassSlider(fraction, onFraction, description = label, modifier = Modifier.weight(1f))
        CxText(value, Modifier.widthIn(min = 52.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) Cx.OnGlass else Color(0x26FFFFFF))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CxText(text, size = 13.sp, weight = FontWeight.SemiBold, color = if (selected) Cx.Black else Cx.OnGlass, maxLines = 1)
    }
}

@Composable
internal fun LookPanel(state: CameraUiState, vm: CameraViewModel) {
    val context = LocalContext.current
    val library = remember { LookLibrary.get(context) }
    val entries = remember { library.entries() }
    val favorites = remember { library.favorites() }
    var tab by remember { mutableIntStateOf(0) }

    SheetColumn {
        GlassSegmented(
            options = listOf(0 to "Look", 1 to "Koreksi", 2 to "Simpan"),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.fillMaxWidth(),
        )
        when (tab) {
            0 -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Chip("Tanpa", state.lookId == null) { vm.selectLook(null) } }
                    items(entries.sortedByDescending { it.id in favorites }, key = { it.id }) { e ->
                        Chip(e.name, state.lookId == e.id) { vm.selectLook(e) }
                    }
                }
                if (state.baseLook.lutId != null) {
                    PanelSlider("Intensitas", "${(state.lookIntensity * 100).toInt()}%", state.lookIntensity, vm::setLookIntensity)
                }
                CxText(
                    "Pilih dan kelola look di tab Jelajah. LUT .cube bisa diimpor di sana.",
                    size = 12.sp, color = Cx.OnGlassDim,
                )
            }
            1 -> {
                PanelSlider("Eksposur", "%+.1f".format(state.quickExposure), (state.quickExposure + 1f) / 2f) { vm.setQuick(exposure = it * 2f - 1f) }
                PanelSlider("Kontras", "%+.1f".format(state.quickContrast), (state.quickContrast + 1f) / 2f) { vm.setQuick(contrast = it * 2f - 1f) }
                PanelSlider("Suhu", "%+.1f".format(state.quickTemperature), (state.quickTemperature + 1f) / 2f) { vm.setQuick(temperature = it * 2f - 1f) }
                PanelSlider("Saturasi", "%+.1f".format(state.quickSaturation), (state.quickSaturation + 1f) / 2f) { vm.setQuick(saturation = it * 2f - 1f) }
                PillButton("Atur ulang koreksi", onClick = vm::resetQuick)
            }
            else -> {
                CxText("Saat foto disimpan", size = 14.sp, weight = FontWeight.SemiBold)
                GlassSegmented(
                    options = listOf(
                        CapturePolicy.PREVIEW_ONLY to "Pratinjau",
                        CapturePolicy.APPLY to "Terapkan",
                        CapturePolicy.BOTH to "Asli + edit",
                    ),
                    selected = state.capturePolicy,
                    onSelect = vm::setCapturePolicy,
                    modifier = Modifier.fillMaxWidth(),
                )
                CxText(
                    "Pratinjau: file tetap asli. Terapkan: look dibakar ke foto. Asli + edit: dua file. " +
                        "Live Photo dan video selalu disimpan tanpa filter.",
                    size = 12.sp, color = Cx.OnGlassDim,
                )
                if (state.config != null && !state.config.gpuPreview) {
                    CxText(
                        "Pratinjau filter live tidak aktif (GPU tidak tersedia atau mode hemat). Pilihan di atas tetap berlaku saat simpan.",
                        size = 12.sp, color = Cx.Warn,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchCell(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        CxText(label, size = 13.sp, weight = FontWeight.SemiBold)
        GlassSwitch(checked, onChange, label)
    }
}

@Composable
internal fun MonitorPanel(state: CameraUiState, vm: CameraViewModel) {
    SheetColumn {
        GlassSegmented(
            options = MonitorKind.entries.map { it to it.label },
            selected = state.monitor,
            onSelect = vm::setMonitorKind,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchCell("Zebra", state.zebra, vm::setZebra, Modifier.weight(1f))
            SwitchCell("Peaking fokus", state.peaking, vm::setPeaking, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwitchCell("Warna palsu", state.falseColor, vm::setFalseColor, Modifier.weight(1f))
            SwitchCell("Level horizon", state.level, vm::setLevel, Modifier.weight(1f))
        }
        if (state.config != null && !state.config.gpuPreview) {
            CxText("Zebra, peaking, dan warna palsu butuh pratinjau GPU, yang sedang tidak aktif.", size = 12.sp, color = Cx.Warn)
        }
    }
}

@Composable
internal fun VideoPanel(state: CameraUiState, vm: CameraViewModel) {
    val config = state.config ?: return
    val options = config.videoOptions
    val has4k = options.any { it.height == 2160 && it.supported }
    val effectiveHeight = if (state.videoHeight >= 2160 && has4k) 2160 else 1080
    fun fpsOk(fps: Int) = options.firstOrNull { it.height == effectiveHeight && it.fps == fps }?.supported ?: false

    SheetColumn {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassSegmented(
                options = listOf(1080 to "1080p", 2160 to "4K"),
                selected = if (state.videoHeight >= 2160) 2160 else 1080,
                onSelect = { vm.setVideo(height = it) },
                modifier = Modifier.weight(1f),
                enabledOf = { it == 1080 || has4k },
            )
            GlassSegmented(
                options = listOf(30 to "30 fps", 60 to "60 fps"),
                selected = if (state.videoFps >= 60) 60 else 30,
                onSelect = { vm.setVideo(fps = it) },
                modifier = Modifier.weight(1f),
                enabledOf = { fpsOk(it) },
            )
        }
        GlassSegmented(
            options = listOf(false to "H.264", true to "H.265"),
            selected = state.videoHevc,
            onSelect = { vm.setVideo(hevc = it) },
            modifier = Modifier.fillMaxWidth(),
            enabledOf = { !it || config.hevcSupported },
        )
        val plan = config.plan
        val size = plan.videoSize
        if (size != null) {
            CxText(
                "Aktif: ${size.width}x${size.height}, ${plan.videoFps} fps, ${if (plan.hevc) "H.265" else "H.264"}",
                size = 12.sp, color = Cx.OnGlassDim,
            )
        }
    }
}
