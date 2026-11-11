package com.shii.dev.cambix.ui.studio

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shii.dev.cambix.grade.Aspect
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.grade.LookLibrary
import com.shii.dev.cambix.media.MediaItem
import com.shii.dev.cambix.media.MediaQuery
import com.shii.dev.cambix.ui.components.CxIcon
import com.shii.dev.cambix.ui.components.CxIconView
import com.shii.dev.cambix.ui.components.GlassIconButton
import com.shii.dev.cambix.ui.components.GlassSegmented
import com.shii.dev.cambix.ui.components.GlassSlider
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.components.MediaThumb
import com.shii.dev.cambix.ui.components.tap
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class Param(
    val label: String,
    val min: Float,
    val max: Float,
    val default: Float,
    val get: (Look) -> Float,
    val set: (Look, Float) -> Look,
)

private val LIGHT = listOf(
    Param("Eksposur", -2f, 2f, 0f, { it.exposure }, { l, v -> l.copy(exposure = v) }),
    Param("Kontras", -1f, 1f, 0f, { it.contrast }, { l, v -> l.copy(contrast = v) }),
    Param("Sorotan", -1f, 1f, 0f, { it.highlights }, { l, v -> l.copy(highlights = v) }),
    Param("Bayangan", -1f, 1f, 0f, { it.shadows }, { l, v -> l.copy(shadows = v) }),
    Param("Putih", -1f, 1f, 0f, { it.whites }, { l, v -> l.copy(whites = v) }),
    Param("Hitam", -1f, 1f, 0f, { it.blacks }, { l, v -> l.copy(blacks = v) }),
)

private val COLOR = listOf(
    Param("Suhu", -1f, 1f, 0f, { it.temperature }, { l, v -> l.copy(temperature = v) }),
    Param("Tint", -1f, 1f, 0f, { it.tint }, { l, v -> l.copy(tint = v) }),
    Param("Saturasi", -1f, 1f, 0f, { it.saturation }, { l, v -> l.copy(saturation = v) }),
    Param("Vibrance", -1f, 1f, 0f, { it.vibrance }, { l, v -> l.copy(vibrance = v) }),
)

private val EFFECTS = listOf(
    Param("Vignet", -1f, 1f, 0f, { it.vignette }, { l, v -> l.copy(vignette = v) }),
    Param("Grain", 0f, 1f, 0f, { it.grain }, { l, v -> l.copy(grain = v) }),
    Param("Pudar", 0f, 1f, 0f, { it.fade }, { l, v -> l.copy(fade = v) }),
    Param("Ketajaman", 0f, 1f, 0f, { it.sharpen }, { l, v -> l.copy(sharpen = v) }),
    Param("Kejelasan", -1f, 1f, 0f, { it.clarity }, { l, v -> l.copy(clarity = v) }),
    Param("Glow", 0f, 1f, 0f, { it.glow }, { l, v -> l.copy(glow = v) }),
    Param("Aberasi RGB", 0f, 1f, 0f, { it.aberration }, { l, v -> l.copy(aberration = v) }),
    Param("Sinar matahari", 0f, 1f, 0f, { it.sunStrength }, { l, v -> l.copy(sunStrength = v) }),
)

private val SUN = listOf(
    Param("Posisi X", 0f, 1f, 0.8f, { it.sunX }, { l, v -> l.copy(sunX = v) }),
    Param("Posisi Y", 0f, 1f, 0.2f, { it.sunY }, { l, v -> l.copy(sunY = v) }),
    Param("Jangkauan", 0.1f, 1f, 0.5f, { it.sunRadius }, { l, v -> l.copy(sunRadius = v) }),
    Param("Kehangatan", 0f, 1f, 0.7f, { it.sunWarmth }, { l, v -> l.copy(sunWarmth = v) }),
)

private val HSL_NAMES = listOf("Merah", "Oranye", "Kuning", "Hijau", "Aqua", "Biru", "Ungu", "Magenta")
private val HSL_COLORS = listOf(
    Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFF00ACC1), Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFFD81B60),
)

@Composable
fun StudioScreen(vm: StudioViewModel, pendingUri: Uri?, onPendingConsumed: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(pendingUri) {
        if (pendingUri != null) {
            vm.open(pendingUri)
            onPendingConsumed()
        }
    }
    LaunchedEffect(ui.noticeId) {
        if (ui.notice != null) {
            delay(2800)
            vm.clearNotice(ui.noticeId)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (ui.uri == null) StudioHome(vm) else Editor(ui, vm)
        ui.notice?.let {
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 24.dp, end = 24.dp)) {
                Box(Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    CxText(it, size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun StudioHome(vm: StudioViewModel) {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<MediaItem>?>(null) }
    LaunchedEffect(Unit) {
        items = withContext(Dispatchers.IO) {
            try {
                MediaQuery.loadAll(context).filter { !it.isVideo }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.open(uri)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CxText("Studio", size = 28.sp, weight = FontWeight.SemiBold)
            CxText("Pilih foto untuk diedit. File asli tidak pernah diubah; hasil disimpan sebagai file baru.", size = 13.sp, color = Cx.OnGlassDim)
        }
        Box(Modifier.padding(horizontal = 20.dp)) {
            PillButton("Pilih foto dari perangkat", onClick = { picker.launch(arrayOf("image/*")) })
        }
        Spacer(Modifier.size(12.dp))
        val list = items
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CxText("Memuat...", color = Cx.OnGlassDim) }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                CxText("Belum ada foto CAMBIX. Ambil foto dulu, atau pilih dari perangkat.", size = 14.sp, color = Cx.OnGlassDim, align = TextAlign.Center)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(list, key = { it.uri.toString() }) { item ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .tap(onClick = { vm.open(item.uri) })
                            .semantics { contentDescription = "Edit ${item.name}" },
                    ) { MediaThumb(item.uri, Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

@Composable
private fun Editor(ui: StudioUi, vm: StudioViewModel) {
    var comparing by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var showPreset by remember { mutableStateOf(false) }

    BackHandler(onBack = vm::close)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlassIconButton(CxIcon.Back, description = "Tutup editor", onClick = vm::close, size = 44.dp)
                CxText(ui.name, Modifier.weight(1f).padding(horizontal = 4.dp), size = 13.sp, color = Cx.OnGlassDim, maxLines = 1)
                GlassIconButton(CxIcon.Undo, description = "Urungkan", onClick = vm::undo, enabled = ui.canUndo, size = 44.dp)
                GlassIconButton(CxIcon.Redo, description = "Ulangi", onClick = vm::redo, enabled = ui.canRedo, size = 44.dp)
                HoldButton("Tahan untuk membandingkan dengan foto asli") { comparing = it }
                GlassIconButton(CxIcon.Sparkle, description = "Saran AI", onClick = { showAi = true }, enabled = !ui.aiBusy, active = ui.aiBusy, size = 44.dp)
                GlassIconButton(CxIcon.Download, description = "Ekspor", onClick = { showExport = true }, size = 44.dp)
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val image = if (comparing) ui.original else (ui.preview ?: ui.original)
                when {
                    ui.loading -> CxText("Membuka foto...", color = Cx.OnGlassDim)
                    ui.error != null -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CxText(ui.error, align = TextAlign.Center)
                        PillButton("Kembali", onClick = vm::close, primary = true)
                    }
                    image != null -> Image(bitmap = image, contentDescription = "Pratinjau hasil edit", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
                if (ui.rendering || ui.aiBusy) {
                    Box(Modifier.align(Alignment.TopEnd).padding(12.dp).glass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        CxText(if (ui.aiBusy) "AI bekerja..." else "Memproses", size = 12.sp, weight = FontWeight.SemiBold, color = Cx.Accent)
                    }
                }
                if (comparing) {
                    Box(Modifier.align(Alignment.TopStart).padding(12.dp).glass(CircleShape).padding(horizontal = 12.dp, vertical = 6.dp)) {
                        CxText("Asli", size = 12.sp, weight = FontWeight.SemiBold)
                    }
                }
                ui.aiTips?.let { tips ->
                    Row(
                        Modifier.align(Alignment.BottomCenter).padding(12.dp).glass(RoundedCornerShape(20.dp)).padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CxText(tips, Modifier.weight(1f), size = 13.sp)
                        GlassIconButton(CxIcon.Close, description = "Tutup saran", onClick = vm::clearAiTips, size = 36.dp)
                    }
                }
            }

            if (!ui.loading && ui.error == null) {
                ToolPanel(ui, vm, onSavePreset = { showPreset = true })
                LazyRow(
                    Modifier.padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StudioTool.entries.toList()) { tool ->
                        val on = tool == ui.tool
                        Box(
                            Modifier
                                .heightIn(min = 48.dp)
                                .clip(CircleShape)
                                .background(if (on) Cx.OnGlass else Cx.GraphiteHi)
                                .selectable(selected = on, role = Role.Tab, onClick = { vm.setTool(tool) })
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CxText(tool.label, size = 14.sp, weight = FontWeight.SemiBold, color = if (on) Cx.Black else Cx.OnGlass)
                        }
                    }
                }
            }
        }

        if (ui.exporting) {
            Box(Modifier.fillMaxSize().background(Color(0xAA000000)), contentAlignment = Alignment.Center) {
                Box(Modifier.glass(RoundedCornerShape(20.dp)).padding(24.dp)) { CxText("Mengekspor resolusi penuh...", size = 15.sp) }
            }
        }
    }

    if (showExport) ExportDialog(onDismiss = { showExport = false }) { png, quality ->
        showExport = false
        vm.exportImage(png, quality)
    }
    if (showAi) AiDialog(provider = vm.aiProviderName(), onDismiss = { showAi = false }) {
        showAi = false
        vm.askAi()
    }
    if (showPreset) PresetDialog(onDismiss = { showPreset = false }) { name ->
        showPreset = false
        vm.savePreset(name)
    }
}

@Composable
private fun HoldButton(description: String, onHold: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .glass(CircleShape)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onHold(true)
                    tryAwaitRelease()
                    onHold(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) { CxIconView(CxIcon.Compare, tint = Cx.OnGlass, size = 22.dp) }
}

@Composable
private fun ToolPanel(ui: StudioUi, vm: StudioViewModel, onSavePreset: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
            .padding(horizontal = 12.dp)
            .glass(RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (ui.tool) {
            StudioTool.LIGHT -> ParamSliders("Cahaya", LIGHT, ui, vm)
            StudioTool.COLOR -> ParamSliders("Warna", COLOR, ui, vm)
            StudioTool.EFFECTS -> ParamSliders("Efek", if (ui.edit.look.sunStrength > 0f) EFFECTS + SUN else EFFECTS, ui, vm)
            StudioTool.HSL -> HslPanel(ui, vm)
            StudioTool.LOOK -> LookTool(ui, vm, onSavePreset)
            StudioTool.CROP -> CropPanel(ui, vm)
        }
    }
}

@Composable
private fun ParamSliders(title: String, params: List<Param>, ui: StudioUi, vm: StudioViewModel) {
    val look = ui.edit.look
    params.forEach { p ->
        val v = p.get(look)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CxText(p.label, Modifier.widthIn(min = 100.dp), size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
            GlassSlider(
                fraction = (v - p.min) / (p.max - p.min),
                onFraction = { f -> vm.update(p.label) { e -> e.copy(look = p.set(e.look, p.min + f * (p.max - p.min))) } },
                description = p.label,
                modifier = Modifier.weight(1f),
            )
            CxText(
                if (p.max - p.min >= 3.5f) "%.1f".format(v) else "${(v * 100).roundToInt()}",
                Modifier.widthIn(min = 44.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End,
            )
        }
    }
    PillButton("Atur ulang $title", onClick = {
        vm.update("reset") { e -> e.copy(look = params.fold(e.look) { l, p -> p.set(l, p.default) }) }
    })
}

@Composable
private fun HslPanel(ui: StudioUi, vm: StudioViewModel) {
    val index = ui.hslBand
    Row(Modifier.fillMaxWidth()) {
        HSL_NAMES.forEachIndexed { i, name ->
            val on = i == index
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { vm.setHslBand(i) })
                    .semantics { contentDescription = name },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(HSL_COLORS[i])
                        .then(if (on) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
                )
            }
        }
    }
    CxText(HSL_NAMES[index], size = 14.sp, weight = FontWeight.SemiBold)
    val band = ui.edit.look.hsl[index]
    HslSlider("Rona", band.hue) { v -> setBand(vm, index) { it.copy(hue = v) } }
    HslSlider("Saturasi", band.sat) { v -> setBand(vm, index) { it.copy(sat = v) } }
    HslSlider("Luminans", band.lum) { v -> setBand(vm, index) { it.copy(lum = v) } }
    PillButton("Atur ulang ${HSL_NAMES[index]}", onClick = { setBand(vm, index) { com.shii.dev.cambix.grade.HslBand() } })
}

private fun setBand(vm: StudioViewModel, index: Int, change: (com.shii.dev.cambix.grade.HslBand) -> com.shii.dev.cambix.grade.HslBand) {
    vm.update("hsl$index") { e ->
        val bands = e.look.hsl.toMutableList()
        bands[index] = change(bands[index])
        e.copy(look = e.look.copy(hsl = bands))
    }
}

@Composable
private fun HslSlider(label: String, value: Float, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CxText(label, Modifier.widthIn(min = 100.dp), size = 13.sp, weight = FontWeight.SemiBold)
        GlassSlider((value + 1f) / 2f, { f -> onValue(f * 2f - 1f) }, description = label, modifier = Modifier.weight(1f))
        CxText("${(value * 100).roundToInt()}", Modifier.widthIn(min = 44.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
    }
}

@Composable
private fun LookTool(ui: StudioUi, vm: StudioViewModel, onSavePreset: () -> Unit) {
    val context = LocalContext.current
    val entries = remember { LookLibrary.get(context).entries() }
    val look = ui.edit.look
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { LookChip("Tanpa LUT", look.lutId == null) { vm.update("look") { it.copy(look = it.look.copy(lutId = null)) } } }
        items(entries, key = { it.id }) { e ->
            LookChip(e.name, e.look.lutId != null && e.look.lutId == look.lutId) { vm.applyEntry(e) }
        }
    }
    if (look.lutId != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CxText("Intensitas", Modifier.widthIn(min = 100.dp), size = 13.sp, weight = FontWeight.SemiBold)
            GlassSlider(
                look.lutIntensity,
                { f -> vm.update("lutIntensity") { it.copy(look = it.look.copy(lutIntensity = f)) } },
                description = "Intensitas LUT",
                modifier = Modifier.weight(1f),
            )
            CxText("${(look.lutIntensity * 100).roundToInt()}", Modifier.widthIn(min = 44.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
        }
    }
    PillButton("Simpan edit ini sebagai preset", onClick = onSavePreset)
}

@Composable
private fun LookChip(text: String, selected: Boolean, onClick: () -> Unit) {
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
private fun CropPanel(ui: StudioUi, vm: StudioViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("Putar kiri", onClick = { vm.update("rotate") { it.copy(rotation = it.rotation + 3) } })
        PillButton("Putar kanan", onClick = { vm.update("rotate") { it.copy(rotation = it.rotation + 1) } })
    }
    PillButton(if (ui.edit.flipH) "Cermin: aktif" else "Cermin horizontal", onClick = { vm.update("flip") { it.copy(flipH = !it.flipH) } })
    CxText("Rasio potong (dari tengah)", size = 13.sp, color = Cx.OnGlassDim)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Aspect.entries.toList()) { a -> LookChip(a.label, ui.edit.aspect == a) { vm.update("aspect") { it.copy(aspect = a) } } }
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: (Boolean, Int) -> Unit) {
    var png by remember { mutableStateOf(false) }
    var quality by remember { mutableFloatStateOf(0.875f) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(Cx.Graphite, RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CxText("Ekspor foto", size = 18.sp, weight = FontWeight.SemiBold)
            GlassSegmented(listOf(false to "JPEG", true to "PNG"), png, { png = it }, Modifier.fillMaxWidth())
            if (!png) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CxText("Kualitas", Modifier.widthIn(min = 70.dp), size = 13.sp, weight = FontWeight.SemiBold)
                    GlassSlider(quality, { quality = it }, description = "Kualitas JPEG", modifier = Modifier.weight(1f))
                    CxText("${(60 + 40 * quality).roundToInt()}", Modifier.widthIn(min = 36.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
                }
            }
            CxText(
                "Resolusi asli (maksimum sekitar 12,6 MP). Metadata EXIF tidak ikut disalin. File asli tidak diubah.",
                size = 12.sp, color = Cx.OnGlassDim,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Batal", onClick = onDismiss)
                PillButton("Ekspor", primary = true, onClick = { onExport(png, (60 + 40 * quality).roundToInt()) })
            }
        }
    }
}

@Composable
private fun AiDialog(provider: String?, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(Cx.Graphite, RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (provider == null) {
                CxText("Belum ada penyedia AI", size = 18.sp, weight = FontWeight.SemiBold)
                CxText("Tambahkan API Anda sendiri di Setelan > Koneksi AI. Tanpa itu, semua fitur lain tetap bekerja secara manual.", size = 14.sp)
                PillButton("Mengerti", onClick = onDismiss, primary = true)
            } else {
                CxText("Kirim gambar ke AI?", size = 18.sp, weight = FontWeight.SemiBold)
                CxText(
                    "Salinan foto ini (maksimum 768 px, dikompres) akan dikirim lewat internet ke \"$provider\", server yang Anda atur sendiri. " +
                        "CAMBIX tidak mengirim apa pun tanpa persetujuan ini.",
                    size = 14.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Batal", onClick = onDismiss)
                    PillButton("Kirim", primary = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun PresetDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.background(Cx.Graphite, RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CxText("Simpan preset", size = 18.sp, weight = FontWeight.SemiBold)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(Cx.GraphiteHi).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Cx.OnGlass, fontSize = 16.sp),
                    cursorBrush = SolidColor(Cx.OnGlass),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (name.isEmpty()) CxText("Nama preset", color = Cx.OnGlassDim)
            }
            CxText("Preset berisi semua penyesuaian warna dan efek, tanpa pangkas dan putar. Ia muncul di Jelajah.", size = 12.sp, color = Cx.OnGlassDim)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Batal", onClick = onDismiss)
                PillButton("Simpan", primary = true, enabled = name.isNotBlank(), onClick = { onSave(name) })
            }
        }
    }
}
