package com.shii.dev.cambix.ui.gallery

import android.app.Activity
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.exifinterface.media.ExifInterface
import com.shii.dev.cambix.Prefs
import android.content.Context
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.shii.dev.cambix.camera.MotionPhoto
import com.shii.dev.cambix.media.MediaItem
import com.shii.dev.cambix.media.MediaQuery
import com.shii.dev.cambix.ui.components.CxIcon
import com.shii.dev.cambix.ui.components.CxIconView
import com.shii.dev.cambix.ui.components.GlassIconButton
import com.shii.dev.cambix.ui.components.MediaThumb
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.components.tap
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class GalleryFilter(val label: String) {
    ALL("Semua"), PHOTO("Foto"), LIVE("Live"), VIDEO("Video"), RAW("RAW"), FAVORITE("Favorit");

    fun accepts(i: MediaItem, favorites: Set<String>): Boolean = when (this) {
        ALL -> true
        PHOTO -> !i.isVideo && !i.isLive && !i.isRaw
        LIVE -> i.isLive
        VIDEO -> i.isVideo
        RAW -> i.isRaw
        FAVORITE -> i.uri.toString() in favorites
    }
}

@Composable
fun GalleryScreen(onEdit: (Uri) -> Unit) {
    val context = LocalContext.current
    var media by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var openIndex by remember { mutableStateOf<Int?>(null) }
    val prefs = remember { Prefs(context) }
    var favorites by remember { mutableStateOf(prefs.favorites) }
    var filter by remember { mutableStateOf(GalleryFilter.ALL) }
    val shown = media.filter { filter.accepts(it, favorites) }

    fun toggleFavorite(item: MediaItem) {
        val key = item.uri.toString()
        favorites = if (key in favorites) favorites - key else favorites + key
        prefs.favorites = favorites
    }

    LaunchedEffect(reload) {
        loading = true
        error = null
        try {
            media = withContext(Dispatchers.IO) { MediaQuery.loadAll(context) }
        } catch (e: Exception) {
            error = e.message ?: "Galeri gagal dimuat."
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                CxText("Galeri", size = 28.sp, weight = FontWeight.SemiBold)
                if (media.isNotEmpty()) CxText("${media.size} item", size = 14.sp, color = Cx.OnGlassDim)
            }
            if (media.isNotEmpty()) {
                LazyRow(
                    Modifier.padding(bottom = 10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(GalleryFilter.entries.toList()) { f ->
                        val on = f == filter
                        Box(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(if (on) Cx.OnGlass else Cx.GraphiteHi)
                                .selectable(selected = on, role = Role.RadioButton, onClick = { filter = f })
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) { CxText(f.label, size = 13.sp, weight = FontWeight.SemiBold, color = if (on) Cx.Black else Cx.OnGlass) }
                    }
                }
            }
            when {
                loading && media.isEmpty() -> CenterMessage("Memuat galeri...")
                error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CxText(error ?: "", align = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.size(16.dp))
                    PillButton("Muat ulang", onClick = { reload++ }, primary = true)
                }
                shown.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    CxIconView(CxIcon.Photos, tint = Cx.OnGlassDim, size = 48.dp)
                    Spacer(Modifier.size(12.dp))
                    CxText(if (media.isEmpty()) "Belum ada hasil" else "Tidak ada item di filter ini", size = 18.sp, weight = FontWeight.SemiBold)
                    Spacer(Modifier.size(6.dp))
                    CxText(
                        if (media.isEmpty()) "Foto, Live Photo, RAW, dan video dari CAMBIX muncul di sini." else "Coba filter lain.",
                        size = 14.sp, color = Cx.OnGlassDim, align = TextAlign.Center,
                    )
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(shown, key = { _, item -> item.uri.toString() }) { index, item ->
                        val kind = if (item.isVideo) "video" else if (item.isLive) "Live Photo" else "foto"
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .tap(onClick = { openIndex = index })
                                .semantics { contentDescription = "Buka $kind" },
                        ) {
                            MediaThumb(item.uri, Modifier.fillMaxSize())
                            if (item.isLive) Badge(CxIcon.Live, Modifier.align(Alignment.TopStart))
                            if (item.isVideo) Badge(CxIcon.Play, Modifier.align(Alignment.BottomStart))
                            if (item.isRaw) Badge(CxIcon.Camera, Modifier.align(Alignment.TopEnd))
                            if (item.uri.toString() in favorites) Badge(CxIcon.HeartFilled, Modifier.align(Alignment.BottomEnd))
                        }
                    }
                }
            }
        }

        val idx = openIndex
        if (idx != null && idx in shown.indices) {
            val current = shown[idx]
            Viewer(
                item = current,
                favorite = current.uri.toString() in favorites,
                onFavorite = { toggleFavorite(current) },
                onEdit = {
                    openIndex = null
                    onEdit(current.uri)
                },
                onClose = { openIndex = null },
                onDeleted = {
                    openIndex = null
                    reload++
                },
            )
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CxText(text, color = Cx.OnGlassDim)
    }
}

@Composable
private fun Badge(icon: CxIcon, modifier: Modifier) {
    Box(modifier.padding(6.dp).size(24.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
        CxIconView(icon, tint = Cx.OnGlass, size = 14.dp)
    }
}

@Composable
private fun Viewer(
    item: MediaItem,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var livePath by remember { mutableStateOf<String?>(null) }
    var liveLoading by remember { mutableStateOf(false) }
    var livePlaying by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val exifText by produceState<String?>(null, item.uri, showInfo) {
        value = if (!showInfo || item.isVideo) null else withContext(Dispatchers.IO) { readExif(context, item.uri) }
    }

    BackHandler(onBack = onClose)

    LaunchedEffect(message) {
        if (message != null) {
            delay(2600)
            message = null
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onDeleted()
    }

    fun delete() {
        try {
            val rows = context.contentResolver.delete(item.uri, null, null)
            if (rows > 0) onDeleted() else message = "File tidak ditemukan."
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(item.uri))
                deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                message = "File ini tidak bisa dihapus dari CAMBIX."
            }
        } catch (e: Exception) {
            message = "Gagal menghapus: ${e.message ?: "kesalahan penyimpanan"}"
        }
    }

    fun share() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (item.isVideo) "video/mp4" else "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, item.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Bagikan"))
    }

    LaunchedEffect(liveLoading) {
        if (!liveLoading) return@LaunchedEffect
        val path = withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(item.uri)?.use { it.readBytes() }
                val mp4 = bytes?.let { MotionPhoto.extractMp4(it) }
                if (mp4 == null) {
                    null
                } else {
                    val f = File(context.cacheDir, "live_view.mp4")
                    f.writeBytes(mp4)
                    f.absolutePath
                }
            } catch (e: Exception) {
                null
            }
        }
        liveLoading = false
        if (path == null) {
            message = "File ini tidak berisi gerakan."
        } else {
            livePath = path
            livePlaying = true
        }
    }

    Box(Modifier.fillMaxSize().background(Cx.Black)) {
        if (item.isVideo) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).also { v ->
                        val controller = MediaController(ctx)
                        controller.setAnchorView(v)
                        v.setMediaController(controller)
                        v.setVideoURI(item.uri)
                        v.setOnPreparedListener { v.start() }
                    }
                },
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 96.dp, top = 64.dp),
            )
        } else {
            FullImage(item.uri, Modifier.fillMaxSize())
            val path = livePath
            if (livePlaying && path != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).also { v ->
                            v.setVideoPath(path)
                            v.setOnPreparedListener { v.start() }
                            v.setOnCompletionListener { livePlaying = false }
                            v.setOnErrorListener { _, _, _ ->
                                livePlaying = false
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().background(Cx.Black),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassIconButton(CxIcon.Back, description = "Kembali ke galeri", onClick = onClose)
            CxText(item.name, size = 14.sp, color = Cx.OnGlassDim, maxLines = 1, modifier = Modifier.weight(1f))
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                Box(Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    CxText(it, size = 13.sp, weight = FontWeight.SemiBold)
                }
            }
            CxText(infoLine(item), size = 13.sp, color = Cx.OnGlassDim, align = TextAlign.Center)
            if (showInfo) {
                Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(14.dp)) {
                    CxText(exifText ?: if (item.isVideo) "Video tidak punya data EXIF." else "Membaca...", size = 13.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.isLive) {
                    GlassIconButton(
                        CxIcon.Live,
                        description = if (livePlaying) "Menghentikan gerakan" else "Putar gerakan Live Photo",
                        onClick = {
                            if (livePlaying) {
                                livePlaying = false
                            } else if (livePath != null) {
                                livePlaying = true
                            } else if (!liveLoading) {
                                liveLoading = true
                            }
                        },
                        active = livePlaying,
                        size = 48.dp,
                    )
                }
                GlassIconButton(
                    if (favorite) CxIcon.HeartFilled else CxIcon.Heart,
                    description = if (favorite) "Hapus dari favorit" else "Tambah ke favorit",
                    onClick = onFavorite, active = favorite, size = 48.dp,
                )
                if (!item.isVideo) GlassIconButton(CxIcon.Sliders, description = "Edit di Studio", onClick = onEdit, size = 48.dp)
                GlassIconButton(CxIcon.Info, description = "Info dan EXIF", onClick = { showInfo = !showInfo }, active = showInfo, size = 48.dp)
                GlassIconButton(CxIcon.Share, description = "Bagikan", onClick = ::share, size = 48.dp)
                GlassIconButton(CxIcon.Trash, description = "Hapus", onClick = { confirmDelete = true }, size = 48.dp)
            }
        }

        if (confirmDelete) {
            Dialog(onDismissRequest = { confirmDelete = false }) {
                Column(
                    Modifier.background(Cx.Graphite, RoundedCornerShape(28.dp)).glass(RoundedCornerShape(28.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CxText("Hapus item ini?", size = 18.sp, weight = FontWeight.SemiBold)
                    CxText("File akan dihapus dari perangkat dan tidak bisa dikembalikan dari CAMBIX.", size = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Batal", onClick = { confirmDelete = false })
                        PillButton("Hapus", onClick = {
                            confirmDelete = false
                            delete()
                        }, primary = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullImage(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = minOf(1f, 2048f / maxOf(w, h))
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(bitmap = b, contentDescription = "Foto", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            CxText("Memuat...", color = Cx.OnGlassDim)
        }
    }
}

private fun infoLine(item: MediaItem): String {
    val date = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id", "ID")).format(Date(item.dateMs))
    val size = "%.1f MB".format(item.sizeBytes / 1_048_576f)
    val dims = if (item.width > 0 && item.height > 0) "${item.width}x${item.height}  |  " else ""
    return "$dims$size  |  $date"
}

/** Reads what the camera wrote; files saved by Studio or a filter bake carry no EXIF and say so. */
private fun readExif(context: Context, uri: Uri): String = try {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val e = ExifInterface(stream)
        val lines = ArrayList<String>()
        val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ")
        if (camera.isNotBlank()) lines.add("Kamera: $camera")
        e.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)?.let { lines.add("ISO: $it") }
        val t = e.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
        if (t > 0.0) lines.add("Rana: " + if (t < 1.0) "1/${Math.round(1.0 / t)} dtk" else "%.1f dtk".format(t))
        val f = e.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0)
        if (f > 0.0) lines.add("Bukaan: f/%.1f".format(f))
        val fl = e.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0)
        if (fl > 0.0) lines.add("Panjang fokus: %.1f mm".format(fl))
        e.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let { lines.add("Balans putih: " + if (it == "1") "manual" else "otomatis") }
        e.getAttribute(ExifInterface.TAG_COLOR_SPACE)?.let { lines.add("Ruang warna: " + if (it == "1") "sRGB" else "tidak terkalibrasi") }
        if (lines.isEmpty()) "Tidak ada data EXIF pada file ini." else lines.joinToString("\n")
    } ?: "File tidak bisa dibaca."
} catch (e: Exception) {
    "Data EXIF tidak bisa dibaca."
}
