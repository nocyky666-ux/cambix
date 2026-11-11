package com.shii.dev.cambix.ui.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shii.dev.cambix.Prefs
import com.shii.dev.cambix.ai.AiClient
import com.shii.dev.cambix.ai.AiException
import com.shii.dev.cambix.ai.AiTasks
import com.shii.dev.cambix.ai.ProviderStore
import com.shii.dev.cambix.grade.Aspect
import com.shii.dev.cambix.grade.BuiltIns
import com.shii.dev.cambix.grade.Frame
import com.shii.dev.cambix.grade.Geometry
import com.shii.dev.cambix.grade.Grader
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.grade.LookCategory
import com.shii.dev.cambix.grade.LookEntry
import com.shii.dev.cambix.grade.LookJson
import com.shii.dev.cambix.grade.LookLibrary
import com.shii.dev.cambix.media.MediaSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.sqrt

enum class StudioTool(val label: String) {
    LIGHT("Cahaya"),
    COLOR("Warna"),
    HSL("HSL"),
    LOOK("Look"),
    EFFECTS("Efek"),
    CROP("Pangkas"),
}

/** Everything that defines the edit. The original file is never written to; this is all that is saved. */
data class Edit(
    val look: Look = Look(),
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val aspect: Aspect = Aspect.ORIGINAL,
)

data class StudioUi(
    val uri: Uri? = null,
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val preview: ImageBitmap? = null,
    val original: ImageBitmap? = null,
    val edit: Edit = Edit(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val tool: StudioTool = StudioTool.LIGHT,
    val hslBand: Int = 0,
    val rendering: Boolean = false,
    val exporting: Boolean = false,
    val notice: String? = null,
    val noticeId: Int = 0,
    val aiBusy: Boolean = false,
    val aiTips: String? = null,
)

class StudioViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)
    private val library = LookLibrary.get(app)
    private val aiStore = ProviderStore(app)
    private val _ui = MutableStateFlow(StudioUi())
    val ui: StateFlow<StudioUi> = _ui.asStateFlow()

    private var proxy: Frame? = null
    private var base: Frame? = null
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    private var lastTag = ""
    private var lastPush = 0L
    private var loadJob: Job? = null
    private var renderJob: Job? = null
    private var persistJob: Job? = null

    fun aiProviderName(): String? = aiStore.default()?.name

    // ---- open and close ----

    fun open(uri: Uri) {
        if (_ui.value.uri == uri && proxy != null) return
        loadJob?.cancel()
        renderJob?.cancel()
        undoStack.clear()
        redoStack.clear()
        proxy = null
        base = null
        _ui.value = StudioUi(uri = uri, loading = true)
        loadJob = viewModelScope.launch {
            try {
                val (frame, name) = withContext(Dispatchers.IO) { decodeProxy(uri, proxyLongSide()) }
                proxy = frame
                val saved = withContext(Dispatchers.IO) { loadProject(uri) }
                _ui.update { it.copy(name = name, edit = saved ?: Edit(), loading = false) }
                rebuildBase()
                render()
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                _ui.update { it.copy(loading = false, error = "Memori tidak cukup untuk membuka foto ini. Turunkan mode kinerja di Setelan.") }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = "Foto tidak bisa dibuka: ${e.message ?: "format tidak dikenali"}") }
            }
        }
    }

    fun close() {
        loadJob?.cancel()
        renderJob?.cancel()
        persistJob?.cancel()
        proxy = null
        base = null
        _ui.value = StudioUi()
    }

    private fun proxyLongSide(): Int = intArrayOf(720, 1080, 1440, 2048)[prefs.performance.coerceIn(0, 3)]

    private fun decodeProxy(uri: Uri, longSide: Int): Pair<Frame, String> {
        val source = ImageDecoder.createSource(app.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = minOf(1f, longSide.toFloat() / maxOf(w, h))
            decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
        }
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        bitmap.recycle()
        return Pair(Frame(px, w, h), displayName(uri))
    }

    private fun displayName(uri: Uri): String =
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: (uri.lastPathSegment ?: "Foto")

    // ---- editing ----

    fun setTool(tool: StudioTool) = _ui.update { it.copy(tool = tool) }

    fun setHslBand(index: Int) = _ui.update { it.copy(hslBand = index.coerceIn(0, 7)) }

    /** Edits that share a [tag] and arrive within 0.8 s become one undo step, so a slider drag is one step. */
    fun update(tag: String, change: (Edit) -> Edit) {
        val current = _ui.value.edit
        val next = change(current)
        if (next == current) return
        val now = System.currentTimeMillis()
        if (tag != lastTag || now - lastPush > 800) {
            undoStack.addLast(current)
            if (undoStack.size > 60) undoStack.removeFirst()
            redoStack.clear()
        }
        lastTag = tag
        lastPush = now
        applyEdit(current, next)
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        val current = _ui.value.edit
        redoStack.addLast(current)
        lastTag = ""
        applyEdit(current, previous)
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        val current = _ui.value.edit
        undoStack.addLast(current)
        lastTag = ""
        applyEdit(current, next)
    }

    private fun applyEdit(from: Edit, to: Edit) {
        _ui.update { it.copy(edit = to, canUndo = undoStack.isNotEmpty(), canRedo = redoStack.isNotEmpty()) }
        if (from.rotation != to.rotation || from.flipH != to.flipH || from.aspect != to.aspect) rebuildBase()
        render()
        persist()
    }

    /** Applies a catalogue entry. A preset replaces the whole look; a LUT keeps the user's own adjustments. */
    fun applyEntry(entry: LookEntry) {
        update("look") { e ->
            val l = entry.look
            if (entry.category == LookCategory.PRESET) {
                e.copy(look = l)
            } else {
                e.copy(look = e.look.copy(lutId = l.lutId, lutIntensity = 1f, grain = l.grain, vignette = l.vignette, fade = l.fade, glow = l.glow))
            }
        }
    }

    fun savePreset(name: String) {
        val entry = library.savePreset(name, _ui.value.edit.look)
        notice("Preset disimpan: ${entry.name}")
    }

    private fun rebuildBase() {
        val source = proxy ?: return
        val e = _ui.value.edit
        val frame = Geometry.transform(source, e.rotation, e.flipH, e.aspect)
        base = frame
        val original = Bitmap.createBitmap(frame.px, frame.w, frame.h, Bitmap.Config.ARGB_8888).asImageBitmap()
        _ui.update { it.copy(original = original) }
    }

    private fun render() {
        renderJob?.cancel()
        val frame = base ?: return
        val edit = _ui.value.edit
        renderJob = viewModelScope.launch {
            delay(40)
            _ui.update { it.copy(rendering = true) }
            val bitmap = withContext(Dispatchers.Default) {
                val out = Grader.render(frame.px, frame.w, frame.h, edit.look, library.cube(edit.look.lutId))
                Bitmap.createBitmap(out, frame.w, frame.h, Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            _ui.update { it.copy(preview = bitmap, rendering = false) }
        }
    }

    // ---- project recovery ----

    private fun projectFile(uri: Uri): File {
        val dir = File(app.filesDir, "projects").also { it.mkdirs() }
        val hash = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    private fun persist() {
        val uri = _ui.value.uri ?: return
        val edit = _ui.value.edit
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(600)
            withContext(Dispatchers.IO) {
                try {
                    val o = JSONObject()
                        .put("rotation", edit.rotation).put("flipH", edit.flipH).put("aspect", edit.aspect.name)
                        .put("look", LookJson.toJson(edit.look))
                    projectFile(uri).writeText(o.toString())
                } catch (e: Exception) {
                    // Recovery is a convenience; a failed write must not interrupt editing.
                }
            }
        }
    }

    private fun loadProject(uri: Uri): Edit? {
        val f = projectFile(uri)
        if (!f.exists()) return null
        return try {
            val o = JSONObject(f.readText())
            Edit(
                look = LookJson.fromJson(o.getJSONObject("look")),
                rotation = o.optInt("rotation", 0),
                flipH = o.optBoolean("flipH", false),
                aspect = Aspect.entries.firstOrNull { it.name == o.optString("aspect") } ?: Aspect.ORIGINAL,
            )
        } catch (e: Exception) {
            null
        }
    }

    // ---- export ----

    fun exportImage(png: Boolean, quality: Int) {
        val uri = _ui.value.uri ?: return
        val edit = _ui.value.edit
        if (_ui.value.exporting) return
        _ui.update { it.copy(exporting = true) }
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.Default) { exportBlocking(uri, edit, png, quality) }
                "Tersimpan di Pictures/CAMBIX"
            } catch (e: OutOfMemoryError) {
                "Memori tidak cukup untuk ekspor resolusi penuh."
            } catch (e: Exception) {
                "Ekspor gagal: ${e.message ?: "penyimpanan penuh?"}"
            }
            _ui.update { it.copy(exporting = false) }
            notice(message)
        }
    }

    private fun exportBlocking(uri: Uri, edit: Edit, png: Boolean, quality: Int) {
        val source = ImageDecoder.createSource(app.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val pixels = w.toLong() * h
            if (pixels > MAX_EXPORT_PIXELS) {
                val s = sqrt(MAX_EXPORT_PIXELS.toDouble() / pixels)
                decoder.setTargetSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
            }
        }
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        bitmap.recycle()
        val frame = Geometry.transform(Frame(px, w, h), edit.rotation, edit.flipH, edit.aspect)
        val graded = Grader.render(frame.px, frame.w, frame.h, edit.look, library.cube(edit.look.lutId))
        val result = Bitmap.createBitmap(graded, frame.w, frame.h, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        result.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, quality.coerceIn(60, 100), out)
        result.recycle()
        val ext = if (png) "png" else "jpg"
        MediaSaver.saveImage(app, out.toByteArray(), "CAMBIX_${MediaSaver.stamp()}_EDIT.$ext", if (png) "image/png" else "image/jpeg")
    }

    // ---- AI ----

    /** The caller must already have asked the user; this sends a downscaled copy of the current picture. */
    fun askAi() {
        val provider = aiStore.default()
        val frame = base
        if (provider == null) {
            notice("Belum ada penyedia AI. Tambahkan di Setelan > Koneksi AI.")
            return
        }
        if (frame == null || _ui.value.aiBusy) return
        _ui.update { it.copy(aiBusy = true, aiTips = null) }
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { smallJpeg(frame, 768) }
                val suggestion = AiTasks.suggest(AiClient(aiStore), provider, jpeg, _ui.value.edit.look, BuiltIns.all.map { it.id })
                update("ai") { it.copy(look = suggestion.look) }
                _ui.update { it.copy(aiBusy = false, aiTips = suggestion.tips.ifBlank { "AI menyarankan koreksi warna. Tidak ada tips komposisi." }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                _ui.update { it.copy(aiBusy = false) }
                notice(e.message ?: "AI gagal menjawab")
            } catch (e: Exception) {
                _ui.update { it.copy(aiBusy = false) }
                notice("AI gagal: ${e.message ?: "kesalahan tak dikenal"}")
            }
        }
    }

    fun clearAiTips() = _ui.update { it.copy(aiTips = null) }

    private fun smallJpeg(frame: Frame, longSide: Int): ByteArray {
        val full = Bitmap.createBitmap(frame.px, frame.w, frame.h, Bitmap.Config.ARGB_8888)
        val scale = minOf(1f, longSide.toFloat() / maxOf(frame.w, frame.h))
        val small = if (scale < 1f) Bitmap.createScaledBitmap(full, (frame.w * scale).toInt().coerceAtLeast(1), (frame.h * scale).toInt().coerceAtLeast(1), true) else full
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, 80, out)
        if (small !== full) small.recycle()
        full.recycle()
        return out.toByteArray()
    }

    // ---- notices ----

    private fun notice(message: String) {
        _ui.update { it.copy(notice = message, noticeId = it.noticeId + 1) }
    }

    fun clearNotice(id: Int) {
        _ui.update { if (it.noticeId == id) it.copy(notice = null) else it }
    }

    private companion object {
        const val MAX_EXPORT_PIXELS = 12_600_000L
    }
}
