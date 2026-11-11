package com.shii.dev.cambix.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shii.dev.cambix.Prefs
import com.shii.dev.cambix.R
import com.shii.dev.cambix.ai.ProviderStore
import com.shii.dev.cambix.camera.DeviceInspector
import com.shii.dev.cambix.camera.DeviceReport
import com.shii.dev.cambix.camera.LensReport
import com.shii.dev.cambix.ui.components.GlassSegmented
import com.shii.dev.cambix.ui.components.GlassSwitch
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var grid by remember { mutableStateOf(prefs.grid) }
    var half by remember { mutableStateOf(prefs.liveHalfSeconds) }
    var gpu by remember { mutableStateOf(prefs.gpuPreview) }
    var performance by remember { mutableStateOf(prefs.performance) }
    var showAi by remember { mutableStateOf(false) }
    val providerCount = remember(showAi) { ProviderStore(context).list().size }
    var report by remember { mutableStateOf<DeviceReport?>(null) }
    var reportError by remember { mutableStateOf<String?>(null) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            report = withContext(Dispatchers.IO) { DeviceInspector.inspect(context) }
        } catch (e: Exception) {
            reportError = e.message ?: "Kemampuan perangkat gagal dibaca."
        }
    }

    if (showAi) {
        AiSettingsScreen(onBack = { showAi = false })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Image(
                painter = painterResource(R.drawable.cambix_logo),
                contentDescription = "Logo CAMBIX",
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
            )
            Column {
                CxText("CAMBIX", size = 28.sp, weight = FontWeight.SemiBold)
                CxText("Versi ${versionName(context)}  |  Shii Dev", size = 14.sp, color = Cx.OnGlassDim)
            }
        }

        Section("Kamera") {
            SettingRow("Garis bantu", "Grid sepertiga di viewfinder.") {
                GlassSwitch(grid, { grid = it; prefs.grid = it }, "Garis bantu")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CxText("Durasi Live Photo", size = 16.sp)
                CxText("Waktu yang direkam sebelum dan sesudah tombol ditekan.", size = 13.sp, color = Cx.OnGlassDim)
                GlassSegmented(
                    options = listOf(1.0f to "1,0 dtk", 1.5f to "1,5 dtk", 2.0f to "2,0 dtk"),
                    selected = half,
                    onSelect = { half = it; prefs.liveHalfSeconds = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingRow("Pratinjau filter live (GPU)", "Look dan LUT terlihat langsung di viewfinder. Matikan jika pratinjau tersendat.") {
                GlassSwitch(gpu, { gpu = it; prefs.gpuPreview = it }, "Pratinjau filter live GPU")
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CxText("Mode kinerja", size = 16.sp)
                CxText("Mengatur ukuran gambar kerja Studio. Mode Hemat juga mematikan pratinjau GPU.", size = 13.sp, color = Cx.OnGlassDim)
                GlassSegmented(
                    options = listOf(0 to "Hemat", 1 to "Seimbang", 2 to "Tinggi", 3 to "Maks"),
                    selected = performance,
                    onSelect = { performance = it; prefs.performance = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            CxText("Resolusi, frame rate, dan codec video diatur langsung di mode Video pada kamera.", size = 12.sp, color = Cx.OnGlassDim)
        }

        Section("Koneksi AI") {
            CxText(
                if (providerCount == 0) "Belum ada penyedia. AI bersifat opsional dan memakai API milik Anda sendiri."
                else "$providerCount penyedia terpasang. Gambar hanya dikirim setelah Anda setuju setiap kali.",
                size = 14.sp,
            )
            PillButton("Kelola koneksi AI", onClick = { showAi = true })
        }

        Section("Kemampuan perangkat") {
            val r = report
            when {
                reportError != null -> CxText(reportError ?: "", size = 14.sp, color = Cx.Warn)
                r == null -> CxText("Membaca kemampuan kamera...", size = 14.sp, color = Cx.OnGlassDim)
                else -> {
                    CxText("${r.device}  |  Android ${r.android}  |  RAM ${"%.1f".format(r.ramGb)} GB", size = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Flag("Encoder H.264", r.h264)
                        Flag("Encoder HEVC", r.hevc)
                    }
                    r.lenses.forEach { Lens(it) }
                    if (r.lenses.isEmpty()) CxText("Tidak ada kamera yang bisa dibaca.", size = 14.sp, color = Cx.Warn)
                    CxText(
                        "Nilai di atas dibaca dari Camera2 dan MediaCodec, bukan diasumsikan. " +
                            "Versi 1.0.0 merekam sampai 1080p; baris 4K hanya menunjukkan kemampuan perangkat.",
                        size = 12.sp, color = Cx.OnGlassDim,
                    )
                    PillButton(
                        if (copied) "Laporan tersalin" else "Salin laporan perangkat",
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("CAMBIX", r.asText()))
                            copied = true
                        },
                    )
                }
            }
        }

        Section("Privasi") {
            CxText(
                "Foto dan video diproses di perangkat dan disimpan di Pictures/CAMBIX dan Movies/CAMBIX. Lokasi tidak dicatat. " +
                    "Mikrofon hanya dipakai saat merekam video. Izin internet hanya dipakai fitur AI yang Anda atur sendiri, dan " +
                    "gambar dikirim hanya setelah Anda menyetujuinya setiap kali.",
                size = 14.sp,
            )
        }

        Section("Tentang") {
            AboutLine("Pengembang", "Shii Dev")
            AboutLine("Paket", "com.shii.dev.cambix")
            AboutLine("Slogan", "Capture reality. Control light. Master color.")
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Suppress("DEPRECATION")
private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CxText(title.uppercase(), size = 12.sp, weight = FontWeight.SemiBold, color = Cx.Titanium, letterSpacing = 1.sp)
        content()
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CxText(title, size = 16.sp)
            CxText(subtitle, size = 13.sp, color = Cx.OnGlassDim)
        }
        control()
    }
}

@Composable
private fun AboutLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CxText(label, size = 14.sp, color = Cx.OnGlassDim)
        CxText(value, size = 14.sp)
    }
}

@Composable
private fun Flag(label: String, ok: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CxText(label, size = 13.sp, color = Cx.OnGlassDim)
        CxText(if (ok) "ya" else "tidak", size = 13.sp, weight = FontWeight.SemiBold, color = if (ok) Cx.Ok else Cx.Warn)
    }
}

@Composable
private fun Lens(l: LensReport) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CxText(l.title, size = 16.sp, weight = FontWeight.SemiBold)
        AboutLine("Level Camera2", l.level)
        AboutLine("Foto maksimum", "%.1f MP".format(l.maxPhotoMp))
        AboutLine("Zoom", l.zoomText)
        AboutLine("Kontrol manual ISO dan rana", if (l.manualSensor) "didukung" else "tidak didukung")
        AboutLine("RAW (DNG) di perangkat", if (l.rawSupported) "didukung, belum dipakai di v1.0" else "tidak didukung")
        AboutLine("Flash", if (l.flash) "ada" else "tidak ada")
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            l.video.forEach { Flag(it.label, it.supported) }
        }
    }
}
