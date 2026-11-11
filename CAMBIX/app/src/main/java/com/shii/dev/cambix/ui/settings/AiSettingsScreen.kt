package com.shii.dev.cambix.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.ai.AdapterType
import com.shii.dev.cambix.ai.AiClient
import com.shii.dev.cambix.ai.AiException
import com.shii.dev.cambix.ai.AuthType
import com.shii.dev.cambix.ai.ProviderConfig
import com.shii.dev.cambix.ai.ProviderStore
import com.shii.dev.cambix.ui.components.CxIcon
import com.shii.dev.cambix.ui.components.GlassIconButton
import com.shii.dev.cambix.ui.components.GlassSegmented
import com.shii.dev.cambix.ui.components.GlassSlider
import com.shii.dev.cambix.ui.components.GlassSwitch
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun AiSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ProviderStore(context) }
    var providers by remember { mutableStateOf(store.list()) }
    var defaultId by remember { mutableStateOf(store.default()?.id) }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    BackHandler {
        if (editing != null) {
            editing = null
        } else {
            onBack()
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            delay(3200)
            message = null
        }
    }

    fun refresh() {
        providers = store.list()
        defaultId = store.default()?.id
    }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportJson().toByteArray(Charsets.UTF_8)) }
                }
                "Konfigurasi diekspor tanpa kunci API"
            } catch (e: Exception) {
                "Gagal mengekspor"
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = try {
                val added = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: ""
                    store.importJson(text)
                }
                refresh()
                "$added penyedia ditambahkan. Isi ulang kunci API tiap penyedia."
            } catch (e: Exception) {
                "File konfigurasi tidak valid"
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val current = editing
        if (current != null) {
            ProviderEditor(
                initial = current,
                store = store,
                isDefault = current.id == defaultId,
                onClose = {
                    editing = null
                    refresh()
                },
            )
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassIconButton(CxIcon.Back, description = "Kembali ke Setelan", onClick = onBack)
                    CxText("Koneksi AI", size = 26.sp, weight = FontWeight.SemiBold)
                }
                CxText(
                    "Pakai API milik Anda sendiri. CAMBIX tidak punya server dan tidak mengirim gambar tanpa persetujuan Anda setiap kali. " +
                        "Mode manual selalu tersedia tanpa AI.",
                    size = 14.sp, color = Cx.OnGlassDim,
                )
                if (providers.isEmpty()) {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CxText("Belum ada penyedia", size = 16.sp, weight = FontWeight.SemiBold)
                        CxText("Tambahkan OpenAI-compatible, Gemini, atau REST kustom.", size = 13.sp, color = Cx.OnGlassDim)
                    }
                }
                providers.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(20.dp))
                            .selectable(selected = p.id == defaultId, role = Role.Button, onClick = { editing = p })
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            CxText(p.name, size = 16.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            CxText("${p.adapter.label}  |  ${p.model.ifBlank { "model belum dipilih" }}", size = 12.sp, color = Cx.OnGlassDim, maxLines = 1)
                        }
                        if (p.id == defaultId) CxText("Default", size = 12.sp, weight = FontWeight.SemiBold, color = Cx.Accent)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Tambah penyedia", primary = true, onClick = {
                        editing = ProviderConfig.blank(store.newId(), AdapterType.OPENAI)
                    })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton("Impor", onClick = { importer.launch(arrayOf("*/*")) })
                    PillButton("Ekspor", enabled = providers.isNotEmpty(), onClick = { exporter.launch("cambix-ai-providers.json") })
                }
                CxText(
                    "Kunci API disimpan terenkripsi di Android Keystore dan tidak ikut diekspor. Catatan: aplikasi klien tetap bisa " +
                        "dibongkar orang lain. Untuk distribusi publik, arahkan Base URL ke proxy backend milik Anda.",
                    size = 12.sp, color = Cx.OnGlassDim,
                )
            }
        }
        message?.let {
            Box(Modifier.align(Alignment.BottomCenter).padding(24.dp).glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
                CxText(it, size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    password: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CxText(label, size = 12.sp, weight = FontWeight.SemiBold, color = Cx.OnGlassDim)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (singleLine) 48.dp else 96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Cx.GraphiteHi)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = singleLine,
                textStyle = TextStyle(color = Cx.OnGlass, fontSize = 15.sp),
                cursorBrush = SolidColor(Cx.OnGlass),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty() && placeholder.isNotEmpty()) CxText(placeholder, size = 15.sp, color = Cx.OnGlassDim)
        }
    }
}

private fun headersToText(h: Map<String, String>): String = h.entries.joinToString("\n") { "${it.key}: ${it.value}" }

private fun textToHeaders(t: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (line in t.lines()) {
        val i = line.indexOf(':')
        if (i > 0) out[line.substring(0, i).trim()] = line.substring(i + 1).trim()
    }
    return out
}

@Composable
private fun ProviderEditor(initial: ProviderConfig, store: ProviderStore, isDefault: Boolean, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf(initial) }
    var headersText by remember { mutableStateOf(headersToText(initial.headers)) }
    var keyInput by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(store.hasKey(initial.id)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var timeout by remember { mutableFloatStateOf((initial.timeoutSec - 10) / 110f) }
    var tokens by remember { mutableFloatStateOf((initial.maxTokens - 100) / 3900f) }
    var temp by remember { mutableFloatStateOf(initial.temperature / 1.5f) }

    fun build(): ProviderConfig = cfg.copy(
        headers = textToHeaders(headersText),
        timeoutSec = (10 + 110 * timeout).roundToInt(),
        maxTokens = (100 + 3900 * tokens).roundToInt(),
        temperature = temp * 1.5f,
    )

    /** Saves the form and the key, so a connection test reads exactly what is on screen. */
    fun commit(): ProviderConfig {
        val saved = build()
        store.save(saved)
        if (keyInput.isNotBlank()) {
            store.setApiKey(saved.id, keyInput)
            keyInput = ""
            hasKey = true
        }
        return saved
    }

    fun request(block: suspend (ProviderConfig) -> String) {
        if (busy) return
        val saved = commit()
        busy = true
        status = null
        scope.launch {
            status = try {
                block(saved)
            } catch (e: AiException) {
                e.message ?: "Gagal"
            } catch (e: Exception) {
                "Gagal: ${e.message ?: e.javaClass.simpleName}"
            }
            busy = false
        }
    }

    BackHandler(onBack = onClose)

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassIconButton(CxIcon.Back, description = "Kembali ke daftar penyedia", onClick = onClose)
            CxText("Penyedia AI", size = 24.sp, weight = FontWeight.SemiBold)
        }

        Field("Nama", cfg.name, { cfg = cfg.copy(name = it) })
        CxText("Adapter", size = 12.sp, weight = FontWeight.SemiBold, color = Cx.OnGlassDim)
        GlassSegmented(
            options = AdapterType.entries.map { it to it.label },
            selected = cfg.adapter,
            onSelect = { a ->
                // Switching adapter refills the address defaults but keeps what the user typed for name and model.
                val fresh = ProviderConfig.blank(cfg.id, a)
                cfg = fresh.copy(name = cfg.name, model = cfg.model)
                headersText = headersToText(fresh.headers)
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Field("Base URL", cfg.baseUrl, { cfg = cfg.copy(baseUrl = it.trim()) }, placeholder = "https://api.example.com/v1")
        Field("Endpoint", cfg.endpoint, { cfg = cfg.copy(endpoint = it.trim()) }, placeholder = "/chat/completions")
        Field("Model", cfg.model, { cfg = cfg.copy(model = it.trim()) }, placeholder = "nama model")
        if (cfg.adapter != AdapterType.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Ambil daftar model", enabled = !busy, onClick = {
                    request { saved ->
                        val list = withContext(Dispatchers.IO) { AiClient(store).listModels(saved) }
                        models = list
                        if (list.isEmpty()) "Server tidak mengembalikan model" else "${list.size} model ditemukan. Pilih salah satu."
                    }
                })
            }
            if (models.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(models) { m ->
                        Box(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(if (m == cfg.model) Cx.OnGlass else Color(0x26FFFFFF))
                                .selectable(selected = m == cfg.model, role = Role.RadioButton, onClick = { cfg = cfg.copy(model = m) })
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { CxText(m, size = 13.sp, weight = FontWeight.SemiBold, color = if (m == cfg.model) Cx.Black else Cx.OnGlass, maxLines = 1) }
                    }
                }
            }
        }

        CxText("Autentikasi", size = 12.sp, weight = FontWeight.SemiBold, color = Cx.OnGlassDim)
        GlassSegmented(
            options = AuthType.entries.map { it to it.label },
            selected = cfg.authType,
            onSelect = { cfg = cfg.copy(authType = it) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (cfg.authType == AuthType.HEADER || cfg.authType == AuthType.QUERY) {
            Field(if (cfg.authType == AuthType.HEADER) "Nama header" else "Nama parameter", cfg.authName, { cfg = cfg.copy(authName = it.trim()) })
        }
        if (cfg.authType != AuthType.NONE) {
            Field(
                "Kunci API" + if (hasKey) " (tersimpan, isi untuk mengganti)" else "",
                keyInput, { keyInput = it },
                placeholder = if (hasKey) "••••••••" else "tempel kunci di sini",
                password = true,
            )
            if (hasKey) {
                PillButton("Hapus kunci tersimpan", onClick = {
                    store.setApiKey(cfg.id, null)
                    hasKey = false
                })
            }
        }
        Field("Header tambahan (satu per baris, Nama: nilai)", headersText, { headersText = it }, singleLine = false, placeholder = "X-Contoh: nilai")

        if (cfg.adapter == AdapterType.CUSTOM) {
            Field("Template isi permintaan (JSON)", cfg.bodyTemplate, { cfg = cfg.copy(bodyTemplate = it) }, singleLine = false)
            CxText(
                "Placeholder: {{prompt}} {{image_base64}} {{model}} (diganti teks JSON berkutip) dan {{max_tokens}} {{temperature}} (angka).",
                size = 12.sp, color = Cx.OnGlassDim,
            )
            Field("Path jawaban (contoh: choices.0.message.content)", cfg.responsePath, { cfg = cfg.copy(responsePath = it.trim()) })
        }

        SliderRow("Batas waktu", "${(10 + 110 * timeout).roundToInt()} dtk", timeout) { timeout = it }
        SliderRow("Token maksimum", "${(100 + 3900 * tokens).roundToInt()}", tokens) { tokens = it }
        SliderRow("Suhu", "%.1f".format(temp * 1.5f), temp) { temp = it }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                CxText("Izinkan HTTP (tanpa enkripsi)", size = 14.sp, weight = FontWeight.SemiBold)
                CxText("Hanya untuk server di jaringan Anda sendiri. HTTPS adalah default.", size = 12.sp, color = Cx.OnGlassDim)
            }
            GlassSwitch(cfg.allowHttp, { cfg = cfg.copy(allowHttp = it) }, "Izinkan HTTP")
        }

        status?.let { CxText(it, size = 13.sp, weight = FontWeight.SemiBold, color = Cx.Accent) }
        if (busy) CxText("Menghubungi server...", size = 13.sp, color = Cx.OnGlassDim)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PillButton("Simpan", primary = true, onClick = {
                commit()
                status = "Tersimpan"
            })
            PillButton("Tes koneksi", enabled = !busy, onClick = { request { saved -> AiClient(store).test(saved) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!isDefault) PillButton("Jadikan default", onClick = {
                commit()
                store.defaultId = cfg.id
                status = "Dijadikan default"
            })
            PillButton("Hapus", onClick = {
                store.delete(cfg.id)
                onClose()
            })
        }
        Spacer(Modifier.size(24.dp))
    }
}

@Composable
private fun SliderRow(label: String, value: String, fraction: Float, onFraction: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CxText(label, Modifier.widthIn(min = 110.dp), size = 13.sp, weight = FontWeight.SemiBold)
        GlassSlider(fraction, onFraction, description = label, modifier = Modifier.weight(1f))
        CxText(value, Modifier.widthIn(min = 56.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
    }
}
