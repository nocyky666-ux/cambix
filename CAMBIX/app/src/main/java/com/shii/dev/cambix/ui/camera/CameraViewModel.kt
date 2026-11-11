package com.shii.dev.cambix.ui.camera

import android.app.Application
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.OrientationEventListener
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shii.dev.cambix.Prefs
import com.shii.dev.cambix.camera.ActiveConfig
import com.shii.dev.cambix.camera.CameraCaps
import com.shii.dev.cambix.camera.CameraEngine
import com.shii.dev.cambix.camera.CapturePolicy
import com.shii.dev.cambix.camera.CaptureMode
import com.shii.dev.cambix.camera.EngineListener
import com.shii.dev.cambix.camera.FlashMode
import com.shii.dev.cambix.gl.MonitorFlags
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.grade.LookEntry
import com.shii.dev.cambix.grade.LookLibrary
import com.shii.dev.cambix.media.MediaQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.ln

enum class CameraPanel { NONE, LOOK, MONITOR }

data class CameraUiState(
    val mode: CaptureMode = CaptureMode.PHOTO,
    val backCamera: Boolean = true,
    val ready: Boolean = false,
    val busy: Boolean = false,
    val recording: Boolean = false,
    val recordSeconds: Int = 0,
    val countdown: Int = 0,
    val timerSeconds: Int = 0,
    val flash: FlashMode = FlashMode.OFF,
    val zoom: Float = 1f,
    val grid: Boolean = false,
    val config: ActiveConfig? = null,
    val error: String? = null,
    val notice: String? = null,
    val noticeId: Int = 0,
    val lastUri: Uri? = null,
    val shutterTick: Int = 0,
    val audioGranted: Boolean = false,
    val ev: Int = 0,
    val proExposureManual: Boolean = false,
    val proIso: Int = 100,
    val proExposureNs: Long = 8_000_000L,
    val proFocusManual: Boolean = false,
    val proFocus: Float = 0f,
    val rawOn: Boolean = false,
    // look and monitoring
    val panel: CameraPanel = CameraPanel.NONE,
    val baseLook: Look = Look.NEUTRAL,
    val look: Look = Look.NEUTRAL,
    val lookId: String? = null,
    val lookName: String? = null,
    val lookIntensity: Float = 1f,
    val quickExposure: Float = 0f,
    val quickContrast: Float = 0f,
    val quickTemperature: Float = 0f,
    val quickSaturation: Float = 0f,
    val capturePolicy: CapturePolicy = CapturePolicy.PREVIEW_ONLY,
    val monitor: MonitorKind = MonitorKind.OFF,
    val zebra: Boolean = false,
    val peaking: Boolean = false,
    val falseColor: Boolean = false,
    val level: Boolean = false,
    // video
    val videoHeight: Int = 1080,
    val videoFps: Int = 60,
    val videoHevc: Boolean = false,
)

/** Log scale suits ISO and shutter time, where each step is a doubling. */
object ProMath {
    const val MAX_MANUAL_SHUTTER_NS = 250_000_000L

    fun fromFraction(min: Float, max: Float, f: Float): Float {
        val lo = ln(min.coerceAtLeast(1f))
        val hi = ln(max.coerceAtLeast(min.coerceAtLeast(1f) + 1f))
        return exp(lo + (hi - lo) * f.coerceIn(0f, 1f))
    }

    fun toFraction(min: Float, max: Float, x: Float): Float {
        val lo = ln(min.coerceAtLeast(1f))
        val hi = ln(max.coerceAtLeast(min.coerceAtLeast(1f) + 1f))
        if (hi <= lo) return 0f
        return ((ln(x.coerceAtLeast(1f)) - lo) / (hi - lo)).coerceIn(0f, 1f)
    }

    fun shutterMaxNs(caps: CameraCaps): Long = minOf(caps.exposureMaxNs, MAX_MANUAL_SHUTTER_NS)
}

class CameraViewModel(app: Application) : AndroidViewModel(app), EngineListener {
    private val prefs = Prefs(app)
    private val library = LookLibrary.get(app)
    private val engine = CameraEngine(app, this)
    private val _state = MutableStateFlow(
        CameraUiState(
            mode = prefs.lastMode,
            grid = prefs.grid,
            capturePolicy = prefs.capturePolicy,
            videoHeight = prefs.videoHeight,
            videoFps = prefs.videoFps,
            videoHevc = prefs.videoHevc,
        ),
    )
    val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private var texture: SurfaceTexture? = null
    private var resumed = false
    private var cameraGranted = false
    private var timerJob: Job? = null
    private var recordJob: Job? = null

    private val orientationListener = object : OrientationEventListener(app) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
            engine.setDeviceRotation(((orientation + 45) / 90 * 90) % 360)
        }
    }

    init {
        engine.setSettings(prefs.engineSettings())
    }

    // ---- lifecycle ----

    fun onSurfaceAvailable(t: SurfaceTexture) {
        texture = t
        tryStart()
    }

    fun onSurfaceDestroyed() {
        texture = null
        engine.stop()
        _state.update { it.copy(ready = false) }
    }

    fun setCameraPermission(granted: Boolean) {
        cameraGranted = granted
        if (granted) tryStart()
    }

    fun setAudioPermission(granted: Boolean) {
        if (_state.value.audioGranted == granted) return
        _state.update { it.copy(audioGranted = granted) }
        engine.audioPermissionChanged()
    }

    fun onResume() {
        resumed = true
        engine.setSettings(prefs.engineSettings())
        _state.update {
            it.copy(
                grid = prefs.grid,
                capturePolicy = prefs.capturePolicy,
                videoHeight = prefs.videoHeight,
                videoFps = prefs.videoFps,
                videoHevc = prefs.videoHevc,
            )
        }
        reloadLook()
        if (orientationListener.canDetectOrientation()) orientationListener.enable()
        tryStart()
        loadLatest()
    }

    fun onPause() {
        resumed = false
        orientationListener.disable()
        cancelTimer()
        engine.stop()
        _state.update { it.copy(ready = false, recording = false, busy = false) }
    }

    fun retry() {
        _state.update { it.copy(error = null) }
        tryStart()
    }

    private fun tryStart() {
        val t = texture ?: return
        if (!resumed || !cameraGranted) return
        val s = _state.value
        engine.start(t, s.backCamera, s.mode)
    }

    private fun loadLatest() {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = MediaQuery.latest(getApplication<Application>())
            if (uri != null) _state.update { it.copy(lastUri = uri) }
        }
    }

    // ---- controls ----

    fun selectMode(mode: CaptureMode) {
        val s = _state.value
        if (s.mode == mode || s.busy || s.recording || s.countdown > 0) return
        prefs.lastMode = mode
        _state.update { it.copy(mode = mode, ready = false, error = null) }
        engine.setMode(mode)
    }

    fun flip() {
        val s = _state.value
        if (s.busy || s.recording || s.countdown > 0) return
        val back = !s.backCamera
        _state.update { it.copy(backCamera = back, ready = false, zoom = 1f, config = null, error = null) }
        val t = texture
        if (t != null && resumed && cameraGranted) engine.start(t, back, s.mode)
    }

    fun cycleFlash() {
        val next = when (_state.value.flash) {
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
        }
        _state.update { it.copy(flash = next) }
        engine.setFlash(next)
    }

    fun cycleTimer() {
        val next = when (_state.value.timerSeconds) {
            0 -> 3
            3 -> 10
            else -> 0
        }
        _state.update { it.copy(timerSeconds = next) }
    }

    fun toggleGrid() {
        val next = !_state.value.grid
        prefs.grid = next
        _state.update { it.copy(grid = next) }
    }

    fun setZoom(ratio: Float) {
        val c = _state.value.config?.caps ?: return
        val z = ratio.coerceIn(c.zoomMin, c.zoomMax)
        _state.update { it.copy(zoom = z) }
        engine.setZoom(z)
    }

    fun pinchZoom(factor: Float) = setZoom(_state.value.zoom * factor)

    fun tapFocus(u: Float, v: Float) = engine.tapFocus(u, v)

    /** The user pressed the shutter button. (onShutter below is the engine reporting that the shot fired.) */
    fun pressShutter() {
        val s = _state.value
        if (!s.ready) return
        if (s.mode == CaptureMode.VIDEO && s.recording) {
            engine.stopRecording()
            return
        }
        if (s.countdown > 0) {
            cancelTimer()
            return
        }
        if (s.busy) return
        val action = {
            if (s.mode == CaptureMode.VIDEO) engine.startRecording() else engine.capturePhoto()
        }
        if (s.timerSeconds > 0) startCountdown(s.timerSeconds, action) else action()
    }

    private fun startCountdown(seconds: Int, action: () -> Unit) {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (i in seconds downTo 1) {
                _state.update { it.copy(countdown = i) }
                delay(1000)
            }
            _state.update { it.copy(countdown = 0) }
            action()
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _state.update { it.copy(countdown = 0) }
    }

    fun clearNotice(id: Int) {
        _state.update { if (it.noticeId == id) it.copy(notice = null) else it }
    }

    // ---- panels, look and monitoring ----

    fun togglePanel(panel: CameraPanel) {
        _state.update { it.copy(panel = if (it.panel == panel) CameraPanel.NONE else panel) }
    }

    /** Reads the look chosen in Discover, so a change there shows up as soon as the camera is back. */
    private fun reloadLook() {
        val id = prefs.activeLookId
        val entry = library.find(id)
        _state.update {
            it.copy(
                baseLook = entry?.look?.let { look -> if (look.lutId != null) look.copy(lutIntensity = prefs.activeLookIntensity) else look } ?: Look.NEUTRAL,
                lookId = entry?.id,
                lookName = entry?.name,
                lookIntensity = prefs.activeLookIntensity,
            )
        }
        pushLook()
    }

    fun selectLook(entry: LookEntry?) {
        prefs.activeLookId = entry?.id
        reloadLook()
    }

    fun setLookIntensity(value: Float) {
        prefs.activeLookIntensity = value.coerceIn(0f, 1f)
        reloadLook()
    }

    fun setQuick(exposure: Float? = null, contrast: Float? = null, temperature: Float? = null, saturation: Float? = null) {
        _state.update {
            it.copy(
                quickExposure = exposure ?: it.quickExposure,
                quickContrast = contrast ?: it.quickContrast,
                quickTemperature = temperature ?: it.quickTemperature,
                quickSaturation = saturation ?: it.quickSaturation,
            )
        }
        pushLook()
    }

    fun resetQuick() {
        _state.update { it.copy(quickExposure = 0f, quickContrast = 0f, quickTemperature = 0f, quickSaturation = 0f) }
        pushLook()
    }

    /** The quick sliders ride on top of the chosen look; the GPU preview and the saved file get the same result. */
    private fun pushLook() {
        val s = _state.value
        val b = s.baseLook
        val look = b.copy(
            exposure = (b.exposure + s.quickExposure).coerceIn(-2f, 2f),
            contrast = (b.contrast + s.quickContrast).coerceIn(-1f, 1f),
            temperature = (b.temperature + s.quickTemperature).coerceIn(-1f, 1f),
            saturation = (b.saturation + s.quickSaturation).coerceIn(-1f, 1f),
        )
        _state.update { it.copy(look = look) }
        engine.setLook(look, library.cube(look.lutId))
    }

    fun setCapturePolicy(policy: CapturePolicy) {
        prefs.capturePolicy = policy
        _state.update { it.copy(capturePolicy = policy) }
        engine.setSettings(prefs.engineSettings())
    }

    fun setMonitorKind(kind: MonitorKind) = _state.update { it.copy(monitor = kind) }

    fun setZebra(on: Boolean) = updateFlags { it.copy(zebra = on) }

    fun setPeaking(on: Boolean) = updateFlags { it.copy(peaking = on) }

    fun setFalseColor(on: Boolean) = updateFlags { it.copy(falseColor = on) }

    fun setLevel(on: Boolean) = _state.update { it.copy(level = on) }

    private fun updateFlags(change: (CameraUiState) -> CameraUiState) {
        _state.update(change)
        val s = _state.value
        engine.setMonitor(MonitorFlags(s.zebra, s.peaking, s.falseColor))
    }

    // ---- video ----

    fun setVideo(height: Int? = null, fps: Int? = null, hevc: Boolean? = null) {
        if (_state.value.recording) return
        height?.let { prefs.videoHeight = it }
        fps?.let { prefs.videoFps = it }
        hevc?.let { prefs.videoHevc = it }
        _state.update { it.copy(videoHeight = prefs.videoHeight, videoFps = prefs.videoFps, videoHevc = prefs.videoHevc) }
        engine.setSettings(prefs.engineSettings())
    }

    // ---- Pro mode ----

    private fun caps(): CameraCaps? = _state.value.config?.caps

    private fun pushPro() {
        val s = _state.value
        engine.setPro(s.proExposureManual, s.proIso, s.proExposureNs, s.proFocusManual, s.proFocus)
        engine.setEv(s.ev)
    }

    fun setExposureManual(manual: Boolean) {
        _state.update { it.copy(proExposureManual = manual) }
        pushPro()
    }

    fun setFocusManual(manual: Boolean) {
        _state.update { it.copy(proFocusManual = manual) }
        pushPro()
    }

    fun setRaw(on: Boolean) {
        _state.update { it.copy(rawOn = on) }
        engine.setRawEnabled(on)
    }

    fun setIsoFraction(f: Float) {
        val c = caps() ?: return
        val iso = ProMath.fromFraction(c.isoMin.toFloat(), c.isoMax.toFloat(), f).toInt().coerceIn(c.isoMin, c.isoMax)
        _state.update { it.copy(proIso = iso) }
        pushPro()
    }

    fun setShutterFraction(f: Float) {
        val c = caps() ?: return
        val ns = ProMath.fromFraction(c.exposureMinNs.toFloat(), ProMath.shutterMaxNs(c).toFloat(), f).toLong()
        _state.update { it.copy(proExposureNs = ns.coerceIn(c.exposureMinNs, ProMath.shutterMaxNs(c))) }
        pushPro()
    }

    fun setFocusFraction(f: Float) {
        val c = caps() ?: return
        _state.update { it.copy(proFocus = f.coerceIn(0f, 1f) * c.minFocusDiopters) }
        pushPro()
    }

    fun setEvFraction(f: Float) {
        val c = caps() ?: return
        val steps = (c.evMin + (c.evMax - c.evMin) * f.coerceIn(0f, 1f)).let { Math.round(it) }
        _state.update { it.copy(ev = steps) }
        pushPro()
    }

    // ---- engine callbacks (engine thread) ----

    override fun onConfigured(config: ActiveConfig) {
        _state.update {
            it.copy(
                ready = config.mode == it.mode,
                config = config,
                error = null,
                rawOn = if (config.mode == CaptureMode.PRO) config.plan.rawSize != null else it.rawOn,
            )
        }
    }

    override fun onClosed() {
        _state.update { it.copy(ready = false) }
    }

    override fun onError(message: String) {
        _state.update { it.copy(ready = false, error = message, recording = false, busy = false) }
    }

    override fun onNotice(message: String) {
        _state.update { it.copy(notice = message, noticeId = it.noticeId + 1) }
    }

    override fun onShutter() {
        _state.update { it.copy(shutterTick = it.shutterTick + 1) }
    }

    override fun onBusy(busy: Boolean) {
        _state.update { it.copy(busy = busy) }
    }

    override fun onSaved(uri: Uri, live: Boolean) {
        _state.update {
            it.copy(lastUri = uri, notice = if (live) "Live Photo tersimpan" else "Foto tersimpan", noticeId = it.noticeId + 1)
        }
    }

    override fun onVideoSaved(uri: Uri) {
        _state.update { it.copy(lastUri = uri, notice = "Video tersimpan", noticeId = it.noticeId + 1) }
    }

    override fun onRecording(active: Boolean) {
        viewModelScope.launch {
            recordJob?.cancel()
            recordJob = null
            _state.update { it.copy(recording = active, recordSeconds = 0) }
            if (active) {
                recordJob = launch {
                    var seconds = 0
                    while (true) {
                        delay(1000)
                        seconds++
                        _state.update { it.copy(recordSeconds = seconds) }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        orientationListener.disable()
        engine.release()
    }
}
