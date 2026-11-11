package com.shii.dev.cambix.ui.camera

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.Settings
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shii.dev.cambix.camera.CameraCaps
import com.shii.dev.cambix.camera.CaptureMode
import com.shii.dev.cambix.camera.FlashMode
import com.shii.dev.cambix.ui.components.CxIcon
import com.shii.dev.cambix.ui.components.CxIconView
import com.shii.dev.cambix.ui.components.GlassIconButton
import com.shii.dev.cambix.ui.components.GlassSegmented
import com.shii.dev.cambix.ui.components.GlassSlider
import com.shii.dev.cambix.ui.components.GlassSwitch
import com.shii.dev.cambix.ui.components.MediaThumb
import com.shii.dev.cambix.ui.components.PillButton
import com.shii.dev.cambix.ui.components.glass
import com.shii.dev.cambix.ui.components.tap
import com.shii.dev.cambix.ui.theme.Cx
import com.shii.dev.cambix.ui.theme.CxText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is LifecycleOwner) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun CameraScreen(vm: CameraViewModel, onOpenGallery: () -> Unit) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var cameraGranted by remember { mutableStateOf(granted(context, Manifest.permission.CAMERA)) }
    var askedAudio by rememberSaveable { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        cameraGranted = ok
        vm.setCameraPermission(ok)
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.setAudioPermission(ok)
    }

    LaunchedEffect(Unit) {
        vm.setCameraPermission(cameraGranted)
        vm.setAudioPermission(granted(context, Manifest.permission.RECORD_AUDIO))
        if (!cameraGranted) cameraLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(state.mode) {
        if (state.mode == CaptureMode.VIDEO && !state.audioGranted && !askedAudio) {
            askedAudio = true
            audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val lifecycleOwner = remember(context) { context.findLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val owner = lifecycleOwner ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // The user may have granted the permission in system settings while we were away.
                    val now = granted(context, Manifest.permission.CAMERA)
                    if (now != cameraGranted) {
                        cameraGranted = now
                        vm.setCameraPermission(now)
                    }
                    vm.setAudioPermission(granted(context, Manifest.permission.RECORD_AUDIO))
                    vm.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> vm.onPause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            vm.onPause()
        }
    }

    if (!cameraGranted) {
        PermissionPanel(
            onRequest = { cameraLauncher.launch(Manifest.permission.CAMERA) },
            onSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            },
        )
        return
    }

    CameraContent(state, vm, onOpenGallery)
}

@Composable
private fun CameraContent(state: CameraUiState, vm: CameraViewModel, onOpenGallery: () -> Unit) {
    val caps = state.config?.caps
    val aspect = state.config?.plan?.previewSize?.let { it.height.toFloat() / it.width } ?: (3f / 4f)
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val shutterFlash = remember { Animatable(0f) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var monitorData by remember { mutableStateOf<MonitorData?>(null) }
    val roll by rememberRollDegrees(state.level)

    // Four samples a second from the same surface the user sees, so the scopes show the filtered picture.
    LaunchedEffect(state.monitor, textureView) {
        val tv = textureView
        if (state.monitor == MonitorKind.OFF || tv == null) {
            monitorData = null
            return@LaunchedEffect
        }
        val bmp = Bitmap.createBitmap(48, 64, Bitmap.Config.ARGB_8888)
        val px = IntArray(48 * 64)
        while (isActive) {
            if (tv.isAvailable) {
                tv.getBitmap(bmp)
                bmp.getPixels(px, 0, 48, 0, 0, 48, 64)
                val copy = px.copyOf()
                monitorData = withContext(Dispatchers.Default) { MonitorAnalysis.analyze(copy, 48, 64) }
            }
            delay(400)
        }
    }

    LaunchedEffect(focusPoint) {
        if (focusPoint != null) {
            delay(900)
            focusPoint = null
        }
    }
    LaunchedEffect(state.shutterTick) {
        if (state.shutterTick > 0) {
            shutterFlash.snapTo(0.55f)
            shutterFlash.animateTo(0f, tween(220))
        }
    }
    LaunchedEffect(state.noticeId) {
        if (state.notice != null) {
            delay(2600)
            vm.clearNotice(state.noticeId)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Cx.Black).clipToBounds()) {
        val previewHeight = maxWidth / aspect

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset)
                .requiredSize(maxWidth, previewHeight)
                .pointerInput(Unit) {
                    detectTapGestures { o ->
                        focusPoint = o
                        vm.tapFocus(o.x / size.width, o.y / size.height)
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) vm.pinchZoom(zoomChange)
                    }
                },
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        keepScreenOn = true
                        textureView = this
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                vm.onSurfaceAvailable(surface)
                            }

                            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                vm.onSurfaceDestroyed()
                                return true
                            }

                            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (state.grid) GridOverlay()
            FocusBox(focusPoint)
            if (state.level) HorizonOverlay(roll)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shutterFlash.value }
                    .background(Color.White),
            )
            if (state.countdown > 0) {
                Box(Modifier.align(Alignment.Center).size(140.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
                    CxText(state.countdown.toString(), size = 72.sp, weight = FontWeight.Light, align = TextAlign.Center)
                }
            }
            if (!state.ready && state.error == null) {
                CxText("Menyiapkan kamera", Modifier.align(Alignment.Center), size = 14.sp, color = Cx.OnGlassDim)
            }
        }

        TopBar(state, vm, caps, Modifier.align(Alignment.TopCenter).padding(top = topInset + 8.dp))

        MonitorCard(state.monitor, monitorData, Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = topInset + 64.dp))

        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassIconButton(
                CxIcon.Wand,
                description = "Look dan filter",
                onClick = { vm.togglePanel(CameraPanel.LOOK) },
                active = state.panel == CameraPanel.LOOK || state.lookId != null,
                size = 44.dp,
            )
            GlassIconButton(
                CxIcon.Chart,
                description = "Monitor dan alat bantu",
                onClick = { vm.togglePanel(CameraPanel.MONITOR) },
                active = state.panel == CameraPanel.MONITOR || state.monitor != MonitorKind.OFF,
                size = 44.dp,
            )
        }

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = topInset + 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.recording) RecordingPill(state.recordSeconds)
            if (state.busy) InfoPill("Memproses...", Cx.Accent)
            state.config?.plan?.note?.let { InfoPill(it, Cx.Warn) }
            val cfg = state.config
            if (cfg != null && cfg.mode == CaptureMode.VIDEO && !cfg.recordsAudio) {
                InfoPill("Tanpa audio (izin mikrofon belum diberikan)", Cx.Warn)
            }
            state.notice?.let { InfoPill(it, Cx.OnGlass) }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(top = 24.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                state.panel == CameraPanel.LOOK -> LookPanel(state, vm)
                state.panel == CameraPanel.MONITOR -> MonitorPanel(state, vm)
                state.mode == CaptureMode.PRO && caps != null -> ProPanel(state, caps, vm)
                state.mode == CaptureMode.VIDEO -> VideoPanel(state, vm)
            }
            if (caps != null && caps.zoomMax > caps.zoomMin + 0.05f) ZoomRow(caps, state.zoom, vm::setZoom)
            ModeSelector(state.mode, enabled = !state.busy && !state.recording && state.countdown == 0, onSelect = vm::selectMode)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LastThumb(state.lastUri, onOpenGallery)
                ShutterButton(state, onClick = vm::pressShutter)
                GlassIconButton(
                    CxIcon.Flip,
                    description = if (state.backCamera) "Pindah ke kamera depan" else "Pindah ke kamera belakang",
                    onClick = vm::flip,
                    enabled = !state.busy && !state.recording && state.countdown == 0,
                    size = 56.dp,
                )
            }
        }

        state.error?.let { ErrorCard(it, onRetry = vm::retry, modifier = Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun TopBar(state: CameraUiState, vm: CameraViewModel, caps: CameraCaps?, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (caps?.hasFlash == true && state.mode != CaptureMode.PRO) {
            val label = when (state.flash) {
                FlashMode.OFF -> "mati"
                FlashMode.AUTO -> "otomatis"
                FlashMode.ON -> "menyala"
            }
            GlassIconButton(
                icon = when (state.flash) {
                    FlashMode.OFF -> CxIcon.FlashOff
                    FlashMode.AUTO -> CxIcon.FlashAuto
                    FlashMode.ON -> CxIcon.FlashOn
                },
                description = "Flash $label",
                onClick = vm::cycleFlash,
                active = state.flash != FlashMode.OFF,
            )
        }
        GlassIconButton(
            CxIcon.Timer,
            description = if (state.timerSeconds == 0) "Timer mati" else "Timer ${state.timerSeconds} detik",
            onClick = vm::cycleTimer,
            active = state.timerSeconds > 0,
        )
        GlassIconButton(CxIcon.Grid, description = "Garis bantu", onClick = vm::toggleGrid, active = state.grid)
        Spacer(Modifier.weight(1f))
        val text = statusText(state)
        if (text.isNotEmpty()) {
            Box(Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 10.dp)) {
                CxText(text, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Only values read back from the active configuration are shown; nothing is claimed in advance. */
private fun statusText(s: CameraUiState): String {
    val cfg = s.config ?: return ""
    val p = cfg.plan
    val parts = ArrayList<String>()
    fun megapixels(w: Int, h: Int) = "${((w.toLong() * h) / 1_000_000f).roundToInt()} MP"
    when (cfg.mode) {
        CaptureMode.VIDEO -> p.videoSize?.let { parts.add("${it.height}p ${p.videoFps} fps") }
        CaptureMode.LIVE -> {
            p.stillSize?.let { parts.add(megapixels(it.width, it.height)) }
            parts.add("Live ${p.videoFps} fps")
        }
        else -> p.stillSize?.let { parts.add(megapixels(it.width, it.height)) }
    }
    if (s.timerSeconds > 0) parts.add("${s.timerSeconds} dtk")
    return parts.joinToString(" | ")
}

@Composable
private fun InfoPill(text: String, color: Color) {
    Box(Modifier.padding(horizontal = 24.dp).glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp)) {
        CxText(text, size = 13.sp, weight = FontWeight.SemiBold, color = color, align = TextAlign.Center)
    }
}

@Composable
private fun RecordingPill(seconds: Int) {
    Row(
        Modifier.glass(CircleShape).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Cx.Record))
        CxText("%02d:%02d".format(seconds / 60, seconds % 60), size = 14.sp, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val c = Color(0x66FFFFFF)
        val w = 1.dp.toPx()
        for (i in 1..2) {
            drawLine(c, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), w)
            drawLine(c, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), w)
        }
    }
}

@Composable
private fun FocusBox(point: Offset?) {
    Canvas(Modifier.fillMaxSize()) {
        if (point != null) {
            val half = 36.dp.toPx()
            drawRoundRect(
                color = Cx.Titanium,
                topLeft = Offset(point.x - half, point.y - half),
                size = Size(half * 2, half * 2),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun ZoomRow(caps: CameraCaps, zoom: Float, onZoom: (Float) -> Unit) {
    val presets = listOf(0.5f, 1f, 2f, 3f, 5f, 10f).filter { it >= caps.zoomMin - 0.01f && it <= caps.zoomMax + 0.01f }
    val offPreset = presets.none { abs(zoom - it) < 0.06f }
    val values = (if (offPreset) presets + zoom else presets).sorted()
    Row(Modifier.glass(CircleShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        values.forEach { v ->
            val on = abs(zoom - v) < 0.06f
            val number = if (abs(v - v.roundToInt()) < 0.01f) v.roundToInt().toString() else "%.1f".format(v)
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (on) Cx.OnGlass else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onZoom(v) })
                    .semantics { contentDescription = "Zoom $number kali" },
                contentAlignment = Alignment.Center,
            ) {
                CxText(if (on) "${number}x" else number, size = 13.sp, weight = FontWeight.SemiBold, color = if (on) Cx.Black else Cx.OnGlass)
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: CaptureMode, enabled: Boolean, onSelect: (CaptureMode) -> Unit) {
    val modes = listOf(
        CaptureMode.PRO to "Pro",
        CaptureMode.VIDEO to "Video",
        CaptureMode.PHOTO to "Foto",
        CaptureMode.LIVE to "Live",
    )
    Row(Modifier.glass(CircleShape).padding(4.dp)) {
        modes.forEach { (mode, label) ->
            val on = mode == selected
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .widthIn(min = 66.dp)
                    .clip(CircleShape)
                    .background(if (on) Cx.OnGlass else Color.Transparent)
                    .selectable(selected = on, enabled = enabled, role = Role.Tab, onClick = { onSelect(mode) })
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                CxText(
                    label,
                    size = 14.sp,
                    weight = FontWeight.SemiBold,
                    color = if (on) Cx.Black else if (enabled) Cx.OnGlass else Cx.OnGlassDim,
                )
            }
        }
    }
}

@Composable
private fun LastThumb(uri: Uri?, onClick: () -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .tap(onClick = onClick)
            .semantics { contentDescription = "Buka galeri" }
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, Cx.EdgeHi, RoundedCornerShape(16.dp)),
    ) {
        MediaThumb(uri, Modifier.fillMaxSize(), sizePx = 160)
    }
}

@Composable
private fun ShutterButton(state: CameraUiState, onClick: () -> Unit) {
    val video = state.mode == CaptureMode.VIDEO
    val enabled = state.ready && (!state.busy || state.recording)
    val label = when {
        video && state.recording -> "Hentikan rekaman"
        video -> "Mulai rekam"
        state.countdown > 0 -> "Batalkan timer"
        else -> "Ambil foto"
    }
    Box(
        Modifier
            .size(84.dp)
            .alpha(if (enabled || state.countdown > 0) 1f else 0.45f)
            .tap(enabled = enabled || state.countdown > 0, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Color.White, r - 2.dp.toPx(), style = Stroke(4.dp.toPx()))
            when {
                video && state.recording -> {
                    val side = 30.dp.toPx()
                    drawRoundRect(
                        Cx.Record,
                        topLeft = Offset(center.x - side / 2f, center.y - side / 2f),
                        size = Size(side, side),
                        cornerRadius = CornerRadius(8.dp.toPx()),
                    )
                }
                video -> drawCircle(Cx.Record, r - 10.dp.toPx())
                else -> {
                    drawCircle(Color.White, r - 10.dp.toPx())
                    if (state.mode == CaptureMode.LIVE) {
                        drawCircle(Color(0x66000000), r - 18.dp.toPx(), style = Stroke(2.dp.toPx()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProPanel(state: CameraUiState, caps: CameraCaps, vm: CameraViewModel) {
    val autoManual = listOf(false to "Auto", true to "Manual")
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (caps.manualSensor) {
            ProHeader("Eksposur") {
                GlassSegmented(autoManual, state.proExposureManual, vm::setExposureManual, Modifier.widthIn(max = 170.dp))
            }
            if (state.proExposureManual) {
                SliderLine(
                    "ISO", state.proIso.toString(),
                    ProMath.toFraction(caps.isoMin.toFloat(), caps.isoMax.toFloat(), state.proIso.toFloat()),
                    vm::setIsoFraction,
                )
                SliderLine(
                    "Rana", formatShutter(state.proExposureNs),
                    ProMath.toFraction(caps.exposureMinNs.toFloat(), ProMath.shutterMaxNs(caps).toFloat(), state.proExposureNs.toFloat()),
                    vm::setShutterFraction,
                )
            }
        } else {
            CxText("Eksposur manual tidak didukung kamera ini.", size = 13.sp, color = Cx.OnGlassDim)
        }
        if (!state.proExposureManual && caps.evMax > caps.evMin) {
            SliderLine(
                "EV", "%+.1f".format(state.ev * caps.evStep),
                (state.ev - caps.evMin).toFloat() / (caps.evMax - caps.evMin),
                vm::setEvFraction,
            )
        }
        if (caps.minFocusDiopters > 0f) {
            ProHeader("Fokus") {
                GlassSegmented(autoManual, state.proFocusManual, vm::setFocusManual, Modifier.widthIn(max = 170.dp))
            }
            if (state.proFocusManual) {
                val text = if (state.proFocus <= 0.01f) "Jauh" else "${(100f / state.proFocus).roundToInt()} cm"
                SliderLine("Jarak", text, state.proFocus / caps.minFocusDiopters, vm::setFocusFraction)
            }
        } else {
            CxText("Fokus manual tidak didukung (lensa fokus tetap).", size = 13.sp, color = Cx.OnGlassDim)
        }
        if (caps.rawSupported) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                CxText("Simpan RAW (DNG) + JPEG", size = 14.sp, weight = FontWeight.SemiBold)
                GlassSwitch(state.rawOn, vm::setRaw, "Simpan RAW DNG")
            }
        } else {
            CxText("RAW tidak didukung kamera ini.", size = 13.sp, color = Cx.OnGlassDim)
        }
    }
}

@Composable
private fun ProHeader(title: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        CxText(title, size = 14.sp, weight = FontWeight.SemiBold)
        control()
    }
}

@Composable
private fun SliderLine(label: String, value: String, fraction: Float, onFraction: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CxText(label, Modifier.widthIn(min = 44.dp), size = 13.sp, weight = FontWeight.SemiBold)
        GlassSlider(fraction, onFraction, description = label, modifier = Modifier.weight(1f))
        CxText(value, Modifier.widthIn(min = 64.dp), size = 13.sp, weight = FontWeight.SemiBold, align = TextAlign.End)
    }
}

private fun formatShutter(ns: Long): String =
    if (ns >= 500_000_000L) "%.1f s".format(ns / 1_000_000_000f) else "1/${(1_000_000_000f / ns).roundToInt()}"

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier.padding(32.dp).glass(RoundedCornerShape(28.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CxText("Kamera bermasalah", size = 18.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        CxText(message, size = 14.sp, align = TextAlign.Center)
        PillButton("Coba lagi", onRetry, primary = true)
    }
}

@Composable
private fun PermissionPanel(onRequest: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(72.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            CxIconView(CxIcon.Camera, tint = Cx.OnGlass, size = 36.dp)
        }
        Spacer(Modifier.size(20.dp))
        CxText("CAMBIX butuh izin kamera", size = 20.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        CxText(
            "Foto dan video diproses di perangkat ini. Aplikasi tidak punya izin internet, jadi tidak ada yang diunggah.",
            size = 14.sp, color = Cx.OnGlassDim, align = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        PillButton("Izinkan kamera", onRequest, primary = true)
        Spacer(Modifier.size(12.dp))
        PillButton("Buka pengaturan aplikasi", onSettings)
    }
}
