package com.shii.dev.cambix.ui.discover

import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.shii.dev.cambix.Prefs
import com.shii.dev.cambix.grade.Grader
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.grade.LookCategory
import com.shii.dev.cambix.grade.LookEntry
import com.shii.dev.cambix.grade.LookLibrary
import com.shii.dev.cambix.grade.SampleScene
import com.shii.dev.cambix.media.MediaQuery
import com.shii.dev.cambix.ui.components.CxIcon
import com.shii.dev.cambix.ui.components.GlassIconButton
import com.shii.dev.cambix.ui.components.GlassSlider
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.components.tap
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val THUMB_W = 160
private const val THUMB_H = 120

private class Sample(val key: String, val px: IntArray)

private enum class ExportKind { CUBE, PRESET }

@Composable
fun DiscoverScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val library = remember { LookLibrary.get(context) }
    val prefs = remember { Prefs(context) }

    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { library.entries() }
    var favorites by remember(version) { mutableStateOf(library.favorites()) }
    var category by remember { mutableStateOf<LookCategory?>(null) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var activeId by remember { mutableStateOf(prefs.activeLookId) }
    var intensity by remember { mutableFloatStateOf(prefs.activeLookIntensity) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<LookEntry?>(null) }
    var exportKind by remember { mutableStateOf(ExportKind.CUBE) }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    // Thumbnails use the newest CAMBIX photo when there is one, so a look is judged on the user's own picture.
    val sample by produceState<Sample?>(null, version) {
        value = withContext(Dispatchers.IO) {
            try {
                val uri = MediaQuery.loadAll(context).firstOrNull { !it.isVideo }?.uri
                val bmp = uri?.let { context.contentResolver.loadThumbnail(it, android.util.Size(THUMB_W * 2, THUMB_H * 2), null) }
                if (bmp != null) {
                    val scaled = Bitmap.createScaledBitmap(bmp.copy(Bitmap.Config.ARGB_8888, false), THUMB_W, THUMB_H, true)
                    val px = IntArray(THUMB_W * THUMB_H)
                    scaled.getPixels(px, 0, THUMB_W, 0, 0, THUMB_W, THUMB_H)
                    Sample("photo:$uri", px)
                } else {
                    Sample("scene", SampleScene.create(THUMB_W, THUMB_H))
                }
            } catch (e: Exception) {
                Sample("scene", SampleScene.create(THUMB_W, THUMB_H))
            }
        }
    }

    fun displayName(uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "LUT.cube"

    val importCube = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val entry = withContext(Dispatchers.IO) { library.importCube(context.contentResolver, uri, displayName(uri)) }
                version++
                "LUT diimpor: ${entry.name}"
            } catch (e: IllegalArgumentException) {
                e.message ?: "File LUT tidak valid"
            } catch (e: Exception) {
                "File LUT tidak bisa dibaca"
            }
        }
    }
    val importPreset = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val entry = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                    library.importPresetJson(text)
                }
                version++
                "Preset diimpor: ${entry.name}"
            } catch (e: IllegalArgumentException) {
                e.message ?: "File preset tidak valid"
            } catch (e: Exception) {
                "File preset tidak bisa dibaca"
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val entry = library.find(activeId)
        if (uri != null && entry != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val text = if (exportKind == ExportKind.CUBE) library.exportCubeText(entry) else library.presetJson(entry)
                    if (text == null) throw IllegalStateException("Look ini tidak punya LUT untuk diekspor")
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                "Tersimpan"
            } catch (e: IllegalStateException) {
                e.message ?: "Gagal mengekspor"
            } catch (e: Exception) {
                "Gagal mengekspor file"
            }
        }
    }

    val visible = entries.filter { e ->
        (!onlyFavorites || e.id in favorites) && (category == null || e.category == category)
    }
    val active = entries.firstOrNull { it.id == activeId }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                CxText("Jelajah", size = 28.sp, weight = FontWeight.SemiBold)
                CxText("Pilih look untuk kamera. Thumbnail memakai foto terbaru Anda.", size = 13.sp, color = Cx.OnGlassDim)
            }
        }
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Impor LUT .cube", onClick = { importCube.launch(arrayOf("*/*")) })
            PillButton("Impor preset", onClick = { importPreset.launch(arrayOf("*/*")) })
        }
        LazyRow(
            Modifier.padding(vertical = 12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip("Semua", category == null && !onlyFavorites) { category = null; onlyFavorites = false } }
            item { FilterChip("Favorit", onlyFavorites) { onlyFavorites = !onlyFavorites; category = null } }
            items(LookCategory.entries.toList()) { c ->
                FilterChip(c.label, category == c) { category = if (category == c) null else c; onlyFavorites = false }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (visible.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CxText(
                        if (onlyFavorites) "Belum ada favorit. Ketuk hati pada sebuah look." else "Belum ada item di kategori ini. Impor LUT atau simpan preset dari Studio.",
                        size = 14.sp, color = Cx.OnGlassDim, align = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { entry ->
                        LookCard(
                            entry = entry,
                            sample = sample,
                            library = library,
                            favorite = entry.id in favorites,
                            active = entry.id == activeId,
                            onSelect = {
                                prefs.activeLookId = entry.id
                                activeId = entry.id
                                message = "Dipakai di kamera: ${entry.name}"
                            },
                            onFavorite = {
                                library.toggleFavorite(entry.id)
                                favorites = library.favorites()
                            },
                        )
                    }
                }
            }
        }

        if (active != null) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).glass(RoundedCornerShape(24.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        CxText("Aktif di kamera", size = 12.sp, color = Cx.OnGlassDim)
                        CxText(active.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                    }
                    PillButton("Matikan", onClick = {
                        prefs.activeLookId = null
                        activeId = null
                    })
                }
                if (active.look.lutId != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        CxText("Intensitas", Modifier.padding(end = 8.dp), size = 13.sp, weight = FontWeight.SemiBold)
                        GlassSlider(intensity, { intensity = it; prefs.activeLookIntensity = it }, description = "Intensitas look", modifier = Modifier.weight(1f))
                        CxText("${(intensity * 100).toInt()}%", Modifier.padding(start = 8.dp), size = 13.sp, weight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (active.look.lutId != null) {
                        PillButton("Ekspor .cube", onClick = {
                            exportKind = ExportKind.CUBE
                            exporter.launch(active.name.replace(' ', '_') + ".cube")
                        })
                    }
                    if (active.category == LookCategory.PRESET) {
                        PillButton("Ekspor preset", onClick = {
                            exportKind = ExportKind.PRESET
                            exporter.launch(active.name.replace(' ', '_') + ".json")
                        })
                    }
                    if (!active.builtIn) PillButton("Hapus", onClick = { confirmDelete = active })
                }
            }
        }
        message?.let {
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    CxText(it, size = 13.sp, weight = FontWeight.SemiBold)
                }
            }
        }
    }

    confirmDelete?.let { entry ->
        Dialog(onDismissRequest = { confirmDelete = null }) {
            Column(
                Modifier.background(Cx.Graphite, RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp)).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CxText("Hapus ${entry.name}?", size = 18.sp, weight = FontWeight.SemiBold)
                CxText("Berkas ini dihapus dari penyimpanan CAMBIX. Foto yang sudah dibuat dengannya tidak berubah.", size = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Batal", onClick = { confirmDelete = null })
                    PillButton("Hapus", primary = true, onClick = {
                        library.delete(entry)
                        if (activeId == entry.id) activeId = null
                        confirmDelete = null
                        version++
                    })
                }
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(if (selected) Cx.OnGlass else Cx.GraphiteHi)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CxText(text, size = 13.sp, weight = FontWeight.SemiBold, color = if (selected) Cx.Black else Cx.OnGlass, maxLines = 1)
    }
}

@Composable
private fun LookCard(
    entry: LookEntry,
    sample: Sample?,
    library: LookLibrary,
    favorite: Boolean,
    active: Boolean,
    onSelect: () -> Unit,
    onFavorite: () -> Unit,
) {
    val thumb by produceState<ImageBitmap?>(null, entry.id, sample?.key) {
        val s = sample
        if (s == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            try {
                val look: Look = entry.look
                val out = Grader.render(s.px, THUMB_W, THUMB_H, look, library.cube(look.lutId))
                Bitmap.createBitmap(out, THUMB_W, THUMB_H, Bitmap.Config.ARGB_8888).asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Column(
        Modifier
            .tap(onClick = onSelect)
            .semantics { contentDescription = "Pakai look ${entry.name} di kamera" }
            .clip(RoundedCornerShape(20.dp))
            .background(Cx.Graphite)
            .then(if (active) Modifier.border(2.dp, Cx.Accent, RoundedCornerShape(20.dp)) else Modifier),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(THUMB_W.toFloat() / THUMB_H)) {
            val t = thumb
            if (t != null) {
                Image(bitmap = t, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            GlassIconButton(
                if (favorite) CxIcon.HeartFilled else CxIcon.Heart,
                description = if (favorite) "Hapus dari favorit" else "Tambah ke favorit",
                onClick = onFavorite,
                active = favorite,
                size = 40.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            CxText(entry.name, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
            CxText(if (active) "Aktif di kamera" else entry.category.label, size = 12.sp, color = if (active) Cx.Accent else Cx.OnGlassDim, maxLines = 1)
        }
    }
}
