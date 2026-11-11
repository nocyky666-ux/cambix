package com.shii.dev.cambix.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.DngCreator
import android.media.Image
import android.media.MediaFormat
import com.shii.dev.cambix.gl.GlPipeline
import com.shii.dev.cambix.gl.MonitorFlags
import com.shii.dev.cambix.grade.ColorCube
import com.shii.dev.cambix.grade.Look
import com.shii.dev.cambix.media.MediaSaver
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

interface EngineListener {
    fun onConfigured(config: ActiveConfig)
    fun onClosed()
    fun onError(message: String)
    fun onNotice(message: String)
    fun onShutter()
    fun onBusy(busy: Boolean)
    fun onSaved(uri: Uri, live: Boolean)
    fun onVideoSaved(uri: Uri)
    fun onRecording(active: Boolean)
}

/**
 * All camera work runs on one HandlerThread. The public functions only post to it. Opening,
 * configuring and tearing down are async, so they go through reconcile(): it compares what the UI
 * wants with what is actually open and starts at most one transition at a time.
 */
class CameraEngine(
    private val context: Context,
    private val listener: EngineListener,
) {
    private enum class Phase { PREVIEW, STILL, VIDEO }

    private class LiveJob {
        var jpeg: ByteArray? = null
        var clip: LiveClip? = null
        var clipMissing = false
    }

    private class RawJob(val orientation: Int, val stamp: String) {
        var image: Image? = null
        var result: TotalCaptureResult? = null
    }

    private val thread = HandlerThread("cambix-camera").also { it.start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { r -> handler.post(r) }
    private val io = Executors.newSingleThreadExecutor()
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var wantTexture: SurfaceTexture? = null
    private var wantBack = true
    private var wantMode = CaptureMode.PHOTO
    private var settings = EngineSettings()
    private var deviceRotation = 0

    private var device: CameraDevice? = null
    private var caps: CameraCaps? = null
    private var streamMap: StreamConfigurationMap? = null
    private var session: CameraCaptureSession? = null
    private var activeTexture: SurfaceTexture? = null
    private var activeMode: CaptureMode? = null
    private var activeBack = true
    private var plan: StreamPlan? = null
    private var transition = false
    private var failed = false
    private var needsReconfigure = false
    private var preparedRotation = 0
    private var forcedStill: Size? = null

    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var live: LiveEncoder? = null
    private var recorder: MediaRecorder? = null
    private var recorderFile: File? = null
    private var recordsAudio = false
    private var recording = false
    private var repeating: CaptureRequest.Builder? = null
    private var busy = false
    private var liveJob: LiveJob? = null

    private var gl: GlPipeline? = null
    private var glActive = false
    private var look: Look = Look.NEUTRAL
    private var cube: ColorCube? = null
    private var monitor = MonitorFlags()
    private var rawEnabled = false
    private var rawReader: ImageReader? = null
    private var rawJob: RawJob? = null
    private var characteristics: CameraCharacteristics? = null
    private var pendingSaves = 0
    private var shotLook: Look = Look.NEUTRAL
    private var shotCube: ColorCube? = null
    private var shotStamp = ""

    private var sessionCloseAction: (() -> Unit)? = null
    private var closingSession: CameraCaptureSession? = null
    private var afterDeviceClosed: (() -> Unit)? = null

    private var precapture = 0
    private var afterPrecapture: (() -> Unit)? = null

    private var zoom = 1f
    private var flash = FlashMode.OFF
    private var ev = 0
    private var proExposureManual = false
    private var proIso = 100
    private var proExposureNs = 8_000_000L
    private var proFocusManual = false
    private var proFocus = 0f

    // ---- public API (any thread) ----

    fun start(texture: SurfaceTexture, back: Boolean, mode: CaptureMode) {
        handler.post {
            wantTexture = texture
            wantBack = back
            wantMode = mode
            failed = false
            reconcile()
        }
    }

    fun stop() {
        handler.post {
            wantTexture = null
            if (recording) finishRecording()
            reconcile()
        }
    }

    fun release() {
        handler.post {
            wantTexture = null
            if (recording) finishRecording()
            reconcile()
        }
        handler.postDelayed({
            thread.quitSafely()
            io.shutdown()
        }, 3000)
    }

    fun setMode(mode: CaptureMode) {
        handler.post {
            if (recording) return@post
            wantMode = mode
            failed = false
            reconcile()
        }
    }

    fun setSettings(s: EngineSettings) {
        handler.post {
            val old = settings
            settings = s
            val videoChanged = s.videoHeight != old.videoHeight || s.videoFps != old.videoFps || s.hevc != old.hevc
            val gpuChanged = s.gpuPreview != old.gpuPreview
            if (((videoChanged && activeMode == CaptureMode.VIDEO) || gpuChanged) && activeMode != null && !recording) {
                needsReconfigure = true
                reconcile()
            }
        }
    }

    fun setLook(newLook: Look, newCube: ColorCube?) {
        handler.post {
            look = newLook
            cube = newCube
            gl?.update(look, cube, monitor)
        }
    }

    fun setMonitor(flags: MonitorFlags) {
        handler.post {
            monitor = flags
            gl?.update(look, cube, monitor)
        }
    }

    fun setRawEnabled(enabled: Boolean) {
        handler.post {
            if (rawEnabled == enabled) return@post
            rawEnabled = enabled
            if (activeMode == CaptureMode.PRO && !recording) {
                needsReconfigure = true
                reconcile()
            }
        }
    }

    fun audioPermissionChanged() {
        handler.post {
            if (activeMode == CaptureMode.VIDEO && !recording) {
                needsReconfigure = true
                reconcile()
            }
        }
    }

    fun setDeviceRotation(degrees: Int) {
        handler.post {
            if (degrees == deviceRotation) return@post
            deviceRotation = degrees
            handler.removeCallbacks(rotationReconfigure)
            if (activeMode == CaptureMode.VIDEO && !recording) handler.postDelayed(rotationReconfigure, 800)
        }
    }

    fun setZoom(ratio: Float) {
        handler.post {
            val c = caps ?: return@post
            zoom = ratio.coerceIn(c.zoomMin, c.zoomMax)
            updateRepeating()
        }
    }

    fun setFlash(mode: FlashMode) {
        handler.post {
            flash = mode
            updateRepeating()
        }
    }

    fun setEv(steps: Int) {
        handler.post {
            ev = steps
            updateRepeating()
        }
    }

    fun setPro(exposureManual: Boolean, iso: Int, exposureNs: Long, focusManual: Boolean, focusDiopters: Float) {
        handler.post {
            proExposureManual = exposureManual
            proIso = iso
            proExposureNs = exposureNs
            proFocusManual = focusManual
            proFocus = focusDiopters
            updateRepeating()
        }
    }

    fun tapFocus(u: Float, v: Float) {
        handler.post {
            val c = caps
            val b = repeating
            val s = session
            if (c == null || b == null || s == null) return@post
            if (activeMode == CaptureMode.PRO && proFocusManual) return@post
            val region = meteringRegion(c, u, v)
            try {
                b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(region))
                b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(region))
                if (CameraMetadata.CONTROL_AF_MODE_AUTO in c.afModes) {
                    b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                    s.capture(b.build(), previewCallback, handler)
                    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE)
                }
                b.set(CaptureRequest.CONTROL_AF_MODE, pickAf(c, activeMode == CaptureMode.VIDEO))
                s.setRepeatingRequest(b.build(), previewCallback, handler)
            } catch (e: Exception) {
                // The session is closing; the next configure starts clean.
            }
        }
    }

    fun capturePhoto() {
        handler.post {
            val mode = activeMode
            if (busy || recording || session == null || jpegReader == null || mode == null || mode == CaptureMode.VIDEO) return@post
            setBusy(true)
            handler.postDelayed(captureWatchdog, 9000)
            val manualExposure = mode == CaptureMode.PRO && proExposureManual
            val needsFlash = flash != FlashMode.OFF && caps?.hasFlash == true && !manualExposure
            if (needsFlash) runPrecaptureThen { fireStill() } else fireStill()
        }
    }

    fun startRecording() {
        handler.post {
            val r = recorder
            val s = session
            val d = device
            val ps = previewSurface
            if (activeMode != CaptureMode.VIDEO || r == null || s == null || d == null || ps == null || recording || busy) return@post
            try {
                val b = d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                b.addTarget(ps)
                b.addTarget(r.surface)
                recording = true
                applyParams(b, Phase.VIDEO)
                repeating = b
                s.setRepeatingRequest(b.build(), previewCallback, handler)
                r.start()
                listener.onRecording(true)
            } catch (e: Exception) {
                recording = false
                listener.onNotice("Gagal memulai rekaman: ${e.message ?: "kesalahan kamera"}")
                needsReconfigure = true
                reconcile()
            }
        }
    }

    fun stopRecording() {
        handler.post {
            finishRecording()
            reconcile()
        }
    }

    // ---- state machine ----

    private fun reconcile() {
        if (transition) return
        val tex = wantTexture
        if (tex == null) {
            if (device != null || session != null) tearDown { reconcile() }
            return
        }
        if (failed || tex.isReleased) return
        if (device == null) {
            openDevice()
            return
        }
        if (activeBack != wantBack) {
            tearDown { reconcile() }
            return
        }
        if (session == null || needsReconfigure || activeMode != wantMode || activeTexture !== tex) {
            configureSession(tex)
        }
    }

    @SuppressLint("MissingPermission")
    private fun openDevice() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail("Izin kamera belum diberikan.")
            return
        }
        val back = wantBack
        try {
            val id = pickCameraId(manager, back)
            if (id == null) {
                fail(if (back) "Kamera belakang tidak ditemukan." else "Kamera depan tidak ditemukan.")
                return
            }
            val (c, map) = readCaps(manager, id)
            characteristics = manager.getCameraCharacteristics(id)
            caps = c
            streamMap = map
            forcedStill = null
            zoom = if (1f in c.zoomMin..c.zoomMax) 1f else c.zoomMin
            transition = true
            manager.openCamera(id, deviceCallback, handler)
        } catch (e: SecurityException) {
            fail("Izin kamera ditolak sistem.")
        } catch (e: CameraAccessException) {
            fail("Kamera tidak bisa dibuka (kode ${e.reason}).")
        } catch (e: IllegalArgumentException) {
            fail("Kamera tidak bisa dibuka: ${e.message}")
        }
    }

    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            transition = false
            device = camera
            activeBack = caps?.facingBack ?: true
            reconcile()
        }

        override fun onDisconnected(camera: CameraDevice) {
            deviceLost(camera, "Kamera terputus. Mungkin sedang dipakai aplikasi lain.")
        }

        override fun onError(camera: CameraDevice, error: Int) {
            deviceLost(camera, errorText(error))
        }

        override fun onClosed(camera: CameraDevice) {
            val action = afterDeviceClosed
            afterDeviceClosed = null
            action?.invoke()
        }
    }

    private fun deviceLost(camera: CameraDevice, message: String) {
        try {
            camera.close()
        } catch (_: Exception) {
        }
        device = null
        session = null
        repeating = null
        sessionCloseAction = null
        closingSession = null
        afterDeviceClosed = null
        recording = false
        releaseModeResources()
        previewSurface?.release()
        previewSurface = null
        activeTexture = null
        activeMode = null
        fail(message)
    }

    private fun closeSession(then: () -> Unit) {
        val s = session
        session = null
        repeating = null
        if (s == null) {
            then()
            return
        }
        closingSession = s
        sessionCloseAction = then
        try {
            s.close()
        } catch (e: Exception) {
            runSessionCloseAction()
            return
        }
        handler.postDelayed({ if (sessionCloseAction === then) runSessionCloseAction() }, 1500)
    }

    private fun runSessionCloseAction() {
        val action = sessionCloseAction
        sessionCloseAction = null
        closingSession = null
        action?.invoke()
    }

    private fun closeDevice(then: () -> Unit) {
        val d = device
        device = null
        if (d == null) {
            then()
            return
        }
        afterDeviceClosed = then
        try {
            d.close()
        } catch (e: Exception) {
            afterDeviceClosed = null
            then()
            return
        }
        handler.postDelayed({
            if (afterDeviceClosed === then) {
                afterDeviceClosed = null
                then()
            }
        }, 1500)
    }

    private fun tearDown(then: () -> Unit) {
        transition = true
        closeSession {
            releaseModeResources()
            previewSurface?.release()
            previewSurface = null
            activeTexture = null
            activeMode = null
            closeDevice {
                transition = false
                listener.onClosed()
                then()
            }
        }
    }

    private fun configureSession(tex: SurfaceTexture) {
        val dev = device ?: return
        val c = caps ?: return
        val mode = wantMode
        transition = true
        needsReconfigure = false
        closeSession {
            if (device !== dev) {
                transition = false
                return@closeSession
            }
            try {
                releaseModeResources()
                if (tex.isReleased) {
                    fail("Layar kamera sudah ditutup.")
                    return@closeSession
                }
                val p = buildPlan(c, mode)
                if (p == null) {
                    fail("Kamera ini tidak punya ukuran stream yang cocok untuk mode ${modeName(mode)}.")
                    return@closeSession
                }
                plan = p
                previewSurface?.release()
                previewSurface = null
                val ps = createPreviewSurface(tex, p)
                previewSurface = ps
                val surfaces = ArrayList<Surface>()
                surfaces.add(ps)
                when (mode) {
                    CaptureMode.PHOTO, CaptureMode.PRO -> {
                        val r = newReader(p.stillSize!!)
                        jpegReader = r
                        surfaces.add(r.surface)
                        val rawSize = p.rawSize
                        if (rawSize != null) {
                            val rr = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2)
                            rr.setOnImageAvailableListener(rawListener, handler)
                            rawReader = rr
                            surfaces.add(rr.surface)
                        }
                    }
                    CaptureMode.LIVE -> {
                        val r = newReader(p.stillSize!!)
                        jpegReader = r
                        surfaces.add(r.surface)
                        val v = p.videoSize!!
                        val bitrate = (v.width.toLong() * v.height * LIVE_FPS * 0.2).toInt().coerceIn(6_000_000, 20_000_000)
                        val enc = LiveEncoder(v.width, v.height, LIVE_FPS, bitrate) { msg -> handler.post { fail(msg) } }
                        live = enc
                        surfaces.add(enc.start())
                    }
                    CaptureMode.VIDEO -> {
                        val rec = prepareRecorder(p)
                        recorder = rec
                        surfaces.add(rec.surface)
                    }
                }
                preparedRotation = deviceRotation
                createSession(dev, surfaces, tex, mode)
            } catch (e: Exception) {
                releaseModeResources()
                fail("Gagal menyiapkan ${modeName(mode)}: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun createSession(dev: CameraDevice, surfaces: List<Surface>, tex: SurfaceTexture, mode: CaptureMode) {
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                if (device !== dev) {
                    try {
                        s.close()
                    } catch (_: Exception) {
                    }
                    return
                }
                session = s
                activeTexture = tex
                activeMode = mode
                transition = false
                startRepeating()
                val c = caps
                val p = plan
                if (c != null && p != null) {
                    val options = if (mode == CaptureMode.VIDEO) videoOptionsFor(c) else emptyList()
                    val hevcOk = mode == CaptureMode.VIDEO && CodecSupport.encoder(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080, 30)
                    listener.onConfigured(ActiveConfig(mode, c, p, recordsAudio, options, hevcOk, glActive))
                }
                reconcile()
            }

            override fun onConfigureFailed(s: CameraCaptureSession) {
                sessionFailed(mode)
            }

            override fun onClosed(s: CameraCaptureSession) {
                if (s === closingSession) runSessionCloseAction()
            }
        }
        val outputs = surfaces.map { OutputConfiguration(it) }
        dev.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, callback))
    }

    private fun sessionFailed(mode: CaptureMode) {
        transition = false
        releaseModeResources()
        val p = plan
        if (mode == CaptureMode.PRO && p != null && p.rawSize != null) {
            rawEnabled = false
            listener.onNotice("RAW tidak bisa dipakai bersama pratinjau di kamera ini, RAW dimatikan.")
            needsReconfigure = true
            reconcile()
            return
        }
        // Guaranteed stream combinations only cover a JPEG up to the video size, so retry with that.
        if (mode == CaptureMode.LIVE && p != null && p.stillSize != null && p.videoSize != null &&
            forcedStill == null && p.stillSize != p.videoSize
        ) {
            forcedStill = p.videoSize
            listener.onNotice("Resolusi foto Live Photo diturunkan agar sesi kamera stabil.")
            needsReconfigure = true
            reconcile()
            return
        }
        fail("Kamera menolak konfigurasi mode ${modeName(mode)}. Coba mode lain.")
    }

    private fun releaseModeResources() {
        try {
            jpegReader?.close()
        } catch (_: Exception) {
        }
        jpegReader = null
        try {
            rawReader?.close()
        } catch (_: Exception) {
        }
        rawReader = null
        rawJob?.image?.close()
        rawJob = null
        gl?.release()
        gl = null
        glActive = false
        live?.stop()
        live = null
        recorder?.let {
            try {
                it.reset()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        recorder = null
        recorderFile?.delete()
        recorderFile = null
        liveJob = null
        pendingSaves = 0
        handler.removeCallbacks(captureWatchdog)
        setBusy(false)
    }

    /** GPU path first; if EGL or the shader fails, the camera feeds the TextureView directly as before. */
    private fun createPreviewSurface(tex: SurfaceTexture, p: StreamPlan): Surface {
        gl?.release()
        gl = null
        glActive = false
        if (settings.gpuPreview) {
            // The GL output is already upright, so the destination buffer is portrait.
            tex.setDefaultBufferSize(p.previewSize.height, p.previewSize.width)
            val pipeline = GlPipeline(tex, p.previewSize.width, p.previewSize.height)
            val surface = pipeline.start()
            if (surface != null) {
                pipeline.update(look, cube, monitor)
                gl = pipeline
                glActive = true
                return surface
            }
            listener.onNotice("Filter live GPU tidak tersedia di perangkat ini, memakai pratinjau biasa.")
        }
        tex.setDefaultBufferSize(p.previewSize.width, p.previewSize.height)
        return Surface(tex)
    }

    private fun videoOptionsFor(c: CameraCaps): List<VideoOption> {
        val map = streamMap ?: return emptyList()
        val sizes = map.getOutputSizes(MediaRecorder::class.java)
        return listOf(1080 to 30, 1080 to 60, 2160 to 30, 2160 to 60).map { (h, fps) ->
            val size = SizePicker.videoSizeFor(sizes, h)
            VideoOption(h, fps, size != null && size.height == h && videoFpsSupported(map, c.fpsRanges, size, fps))
        }
    }

    // ---- planning ----

    private fun buildPlan(c: CameraCaps, mode: CaptureMode): StreamPlan? {
        val map = streamMap ?: return null
        val (preview, jpeg, codecSizes) = streamSizes(map)
        return when (mode) {
            CaptureMode.PHOTO -> SizePicker.photoPlan(preview, jpeg)
            CaptureMode.PRO -> {
                val base = SizePicker.photoPlan(preview, jpeg)
                val raw = if (rawEnabled && c.rawSupported) largestRawSize(map) else null
                if (base != null && raw != null) StreamPlan(base.previewSize, base.stillSize, null, 0, null, null, raw) else base
            }
            CaptureMode.LIVE -> {
                val base = SizePicker.livePlan(preview, jpeg, codecSizes, LIVE_FPS) { s ->
                    CodecSupport.avcEncoder(s.width, s.height, LIVE_FPS)
                }
                base?.copyWith(stillSize = forcedStill ?: base.stillSize, fpsRange = SizePicker.pickFpsRange(c.fpsRanges, LIVE_FPS))
            }
            CaptureMode.VIDEO -> videoPlan(c, map, preview)
        }
    }

    private fun videoPlan(c: CameraCaps, map: StreamConfigurationMap, preview: Array<Size>?): StreamPlan? {
        val sizes = map.getOutputSizes(MediaRecorder::class.java)
        val heights = if (settings.videoHeight >= 2160) listOf(2160, 1080) else listOf(1080)
        val rates = if (settings.videoFps >= 60) listOf(60, 30) else listOf(30)
        val hevcOk = settings.hevc && CodecSupport.encoder(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080, 30)
        val mimes = if (hevcOk) listOf(MediaFormat.MIMETYPE_VIDEO_HEVC, MediaFormat.MIMETYPE_VIDEO_AVC) else listOf(MediaFormat.MIMETYPE_VIDEO_AVC)
        for (h in heights) {
            val size = SizePicker.videoSizeFor(sizes, h) ?: continue
            if (h == 2160 && size.height < 2160) continue
            for (fps in rates) {
                for (mime in mimes) {
                    if (!videoFpsSupported(map, c.fpsRanges, size, fps, mime)) continue
                    val p = SizePicker.previewFor(preview, size) ?: continue
                    val notes = ArrayList<String>()
                    if (h < settings.videoHeight) notes.add("4K tidak tersedia, memakai ${size.height}p")
                    if (fps < settings.videoFps) notes.add("${settings.videoFps} fps tidak tersedia di ${size.height}p, memakai $fps fps")
                    if (settings.hevc && mime != MediaFormat.MIMETYPE_VIDEO_HEVC) notes.add("H.265 tidak tersedia pada pilihan ini, memakai H.264")
                    val note = notes.joinToString(". ").takeIf { it.isNotEmpty() }
                    return StreamPlan(p, null, size, fps, SizePicker.pickFpsRange(c.fpsRanges, fps), note, null, mime == MediaFormat.MIMETYPE_VIDEO_HEVC)
                }
            }
        }
        return null
    }

    private fun newReader(size: Size): ImageReader {
        val r = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2)
        r.setOnImageAvailableListener(imageListener, handler)
        return r
    }

    @Suppress("DEPRECATION")
    private fun prepareRecorder(p: StreamPlan): MediaRecorder {
        val size = p.videoSize ?: throw IllegalStateException("Ukuran video kosong")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        try {
            val file = File(context.cacheDir, "rec_${System.currentTimeMillis()}.mp4")
            val audio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (audio) rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setOutputFile(file.absolutePath)
            val bitsPerPixel = if (p.hevc) 0.12 else 0.19
            rec.setVideoEncodingBitRate((size.width.toLong() * size.height * p.videoFps * bitsPerPixel).toInt().coerceIn(8_000_000, 100_000_000))
            rec.setVideoFrameRate(p.videoFps)
            rec.setVideoSize(size.width, size.height)
            rec.setVideoEncoder(if (p.hevc) MediaRecorder.VideoEncoder.HEVC else MediaRecorder.VideoEncoder.H264)
            if (audio) {
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioEncodingBitRate(128_000)
                rec.setAudioSamplingRate(44_100)
            }
            rec.setOrientationHint(outputOrientation())
            rec.prepare()
            recorderFile = file
            recordsAudio = audio
            return rec
        } catch (e: Exception) {
            try {
                rec.release()
            } catch (_: Exception) {
            }
            throw e
        }
    }

    // ---- requests ----

    private fun startRepeating() {
        val d = device ?: return
        val s = session ?: return
        val ps = previewSurface ?: return
        val mode = activeMode ?: return
        try {
            val video = mode == CaptureMode.VIDEO
            val b = d.createCaptureRequest(if (video) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(ps)
            if (mode == CaptureMode.LIVE) live?.inputSurface?.let { b.addTarget(it) }
            if (video && recording) recorder?.surface?.let { b.addTarget(it) }
            applyParams(b, if (video) Phase.VIDEO else Phase.PREVIEW)
            repeating = b
            s.setRepeatingRequest(b.build(), previewCallback, handler)
        } catch (e: CameraAccessException) {
            fail("Preview kamera gagal dimulai (kode ${e.reason}).")
        } catch (e: IllegalStateException) {
            fail("Sesi kamera sudah tertutup.")
        }
    }

    private fun updateRepeating() {
        val b = repeating ?: return
        val s = session ?: return
        val video = activeMode == CaptureMode.VIDEO
        try {
            applyParams(b, if (video) Phase.VIDEO else Phase.PREVIEW)
            s.setRepeatingRequest(b.build(), previewCallback, handler)
        } catch (e: Exception) {
            // The session is closing; the next configure rebuilds the request.
        }
    }

    private fun applyParams(b: CaptureRequest.Builder, phase: Phase) {
        val c = caps ?: return
        val mode = activeMode ?: wantMode
        if (c.zoomByRatio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
        } else if (zoom > 1.001f) {
            b.set(CaptureRequest.SCALER_CROP_REGION, cropWindow(c, zoom))
        }

        val manualFocus = mode == CaptureMode.PRO && proFocusManual && c.minFocusDiopters > 0f
        if (manualFocus) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, proFocus.coerceIn(0f, c.minFocusDiopters))
        } else {
            b.set(CaptureRequest.CONTROL_AF_MODE, pickAf(c, phase == Phase.VIDEO))
        }

        val manualExposure = mode == CaptureMode.PRO && proExposureManual && c.manualSensor
        if (manualExposure) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, proIso.coerceIn(c.isoMin, c.isoMax))
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, proExposureNs.coerceIn(c.exposureMinNs, c.exposureMaxNs))
        } else {
            val useFlash = c.hasFlash && phase != Phase.VIDEO && mode != CaptureMode.PRO
            val aeMode = if (!useFlash) {
                CameraMetadata.CONTROL_AE_MODE_ON
            } else {
                when (flash) {
                    FlashMode.ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                    FlashMode.AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                    FlashMode.OFF -> CameraMetadata.CONTROL_AE_MODE_ON
                }
            }
            b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev.coerceIn(c.evMin, c.evMax))
        }

        if (phase != Phase.STILL) {
            plan?.fpsRange?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }
        if (mode == CaptureMode.VIDEO && c.videoStabilization) {
            b.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        } else if (c.opticalStabilization) {
            b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)
        }
    }

    private fun pickAf(c: CameraCaps, video: Boolean): Int {
        val wanted = if (video) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO else CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        return when {
            wanted in c.afModes -> wanted
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in c.afModes -> CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            CameraMetadata.CONTROL_AF_MODE_AUTO in c.afModes -> CameraMetadata.CONTROL_AF_MODE_AUTO
            else -> CameraMetadata.CONTROL_AF_MODE_OFF
        }
    }

    private fun cropWindow(c: CameraCaps, z: Float): Rect {
        val a = c.activeArray
        val w = (a.width() / z).toInt()
        val h = (a.height() / z).toInt()
        val l = a.left + (a.width() - w) / 2
        val t = a.top + (a.height() - h) / 2
        return Rect(l, t, l + w, t + h)
    }

    /** UI is portrait-locked, so the preview is the sensor image rotated by sensorOrientation. */
    private fun meteringRegion(c: CameraCaps, u: Float, v: Float): MeteringRectangle {
        val a = c.activeArray
        val z = max(1f, zoom)
        val cw = a.width() / z
        val ch = a.height() / z
        val left = a.left + (a.width() - cw) / 2f
        val top = a.top + (a.height() - ch) / 2f
        val uu = u.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val nx: Float
        val ny: Float
        when (c.sensorOrientation) {
            90 -> {
                nx = vv
                ny = 1f - uu
            }
            270 -> {
                nx = 1f - vv
                ny = uu
            }
            180 -> {
                nx = 1f - uu
                ny = 1f - vv
            }
            else -> {
                nx = uu
                ny = vv
            }
        }
        val side = (minOf(a.width(), a.height()) * 0.16f).toInt()
        val x = (left + nx * cw - side / 2f).toInt().coerceIn(a.left, a.right - side)
        val y = (top + ny * ch - side / 2f).toInt().coerceIn(a.top, a.bottom - side)
        return MeteringRectangle(x, y, side, side, MeteringRectangle.METERING_WEIGHT_MAX - 1)
    }

    private fun outputOrientation(): Int {
        val c = caps ?: return 90
        val rot = deviceRotation
        return if (c.facingBack) (c.sensorOrientation - rot + 360) % 360 else (c.sensorOrientation + rot) % 360
    }

    // ---- flash precapture ----

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            if (precapture == 0) return
            val ae = result.get(CaptureResult.CONTROL_AE_STATE)
            if (precapture == 1) {
                if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED) {
                    precapture = 2
                }
            } else if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                finishPrecapture()
            }
        }
    }

    private val precaptureTimeout = Runnable { if (precapture != 0) finishPrecapture() }

    private fun runPrecaptureThen(next: () -> Unit) {
        val b = repeating
        val s = session
        if (b == null || s == null) {
            next()
            return
        }
        afterPrecapture = next
        precapture = 1
        handler.postDelayed(precaptureTimeout, 1200)
        try {
            b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            s.capture(b.build(), previewCallback, handler)
            b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE)
        } catch (e: Exception) {
            finishPrecapture()
        }
    }

    private fun finishPrecapture() {
        handler.removeCallbacks(precaptureTimeout)
        precapture = 0
        val action = afterPrecapture
        afterPrecapture = null
        action?.invoke()
    }

    // ---- stills and Live Photo ----

    private val captureWatchdog = Runnable {
        if (busy) {
            liveJob = null
            rawJob?.image?.close()
            rawJob = null
            pendingSaves = 0
            setBusy(false)
            listener.onNotice("Pengambilan foto terlalu lama dan dibatalkan.")
        }
    }

    private val rotationReconfigure = Runnable {
        if (activeMode == CaptureMode.VIDEO && !recording && preparedRotation != deviceRotation) {
            needsReconfigure = true
            reconcile()
        }
    }

    private fun fireStill() {
        val d = device
        val s = session
        val reader = jpegReader
        val mode = activeMode
        if (d == null || s == null || reader == null || mode == null) {
            setBusy(false)
            return
        }
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(reader.surface)
            applyParams(b, Phase.STILL)
            b.set(CaptureRequest.JPEG_ORIENTATION, outputOrientation())
            b.set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            shotLook = look
            shotCube = cube
            shotStamp = MediaSaver.stamp()
            val rr = rawReader
            if (rr != null && mode == CaptureMode.PRO) {
                b.addTarget(rr.surface)
                rawJob?.image?.close()
                rawJob = RawJob(outputOrientation(), shotStamp)
                pendingSaves = 2
            } else {
                pendingSaves = 1
            }
            if (mode == CaptureMode.LIVE) startLiveJob()
            listener.onShutter()
            s.capture(b.build(), stillCallback, handler)
        } catch (e: Exception) {
            liveJob = null
            rawJob?.image?.close()
            rawJob = null
            pendingSaves = 0
            handler.removeCallbacks(captureWatchdog)
            setBusy(false)
            listener.onNotice("Gagal mengambil foto: ${e.message ?: "kesalahan kamera"}")
        }
    }

    private val stillCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val job = rawJob ?: return
            job.result = result
            maybeFinishRaw(job)
        }

        override fun onCaptureFailed(s: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            liveJob = null
            rawJob?.image?.close()
            rawJob = null
            pendingSaves = 0
            handler.removeCallbacks(captureWatchdog)
            setBusy(false)
            listener.onNotice("Pengambilan foto gagal. Coba lagi.")
        }
    }

    private val imageListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try {
            reader.acquireNextImage()
        } catch (e: Exception) {
            null
        } ?: return@OnImageAvailableListener
        val bytes = try {
            val buffer = image.planes[0].buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        } finally {
            image.close()
        }
        onJpegReady(bytes)
    }

    private val rawListener = ImageReader.OnImageAvailableListener { reader ->
        val image = try {
            reader.acquireNextImage()
        } catch (e: Exception) {
            null
        } ?: return@OnImageAvailableListener
        val job = rawJob
        if (job == null) {
            image.close()
            return@OnImageAvailableListener
        }
        job.image?.close()
        job.image = image
        maybeFinishRaw(job)
    }

    /** The DNG needs both the RAW image and the capture result that describes it, which arrive separately. */
    private fun maybeFinishRaw(job: RawJob) {
        if (rawJob !== job) return
        val image = job.image ?: return
        val result = job.result ?: return
        rawJob = null
        val chars = characteristics
        if (chars == null) {
            image.close()
            endSave()
            return
        }
        io.execute {
            try {
                val dng = DngCreator(chars, result)
                try {
                    dng.setOrientation(exifOrientation(job.orientation))
                    MediaSaver.saveRaw(context, "CAMBIX_${job.stamp}.dng") { out -> dng.writeImage(out, image) }
                } finally {
                    dng.close()
                }
                handler.post { endSave() }
            } catch (e: Exception) {
                handler.post {
                    listener.onNotice("Gagal menyimpan RAW: ${e.message ?: "penyimpanan penuh?"}")
                    endSave()
                }
            } finally {
                image.close()
            }
        }
    }

    private fun exifOrientation(degrees: Int): Int = when (degrees) {
        90 -> 6
        180 -> 3
        270 -> 8
        else -> 1
    }

    private fun endSave() {
        pendingSaves = (pendingSaves - 1).coerceAtLeast(0)
        if (pendingSaves == 0) {
            handler.removeCallbacks(captureWatchdog)
            setBusy(false)
        }
    }

    private fun startLiveJob() {
        val job = LiveJob()
        liveJob = job
        val enc = live
        if (enc == null) {
            job.clipMissing = true
            return
        }
        val half = (settings.liveHalfSeconds * 1_000_000f).toLong()
        enc.requestClip(half, half) { clip ->
            handler.post {
                if (liveJob === job) {
                    if (clip == null) job.clipMissing = true else job.clip = clip
                    maybeFinishLive(job)
                }
            }
        }
    }

    private fun onJpegReady(bytes: ByteArray) {
        val job = liveJob
        if (activeMode == CaptureMode.LIVE && job != null) {
            job.jpeg = bytes
            maybeFinishLive(job)
            return
        }
        val lk = shotLook
        val cb = shotCube
        val policy = settings.capturePolicy
        val stamp = shotStamp
        io.execute {
            try {
                val (uri, warning) = saveShot(bytes, lk, cb, policy, stamp)
                handler.post {
                    if (warning != null) listener.onNotice(warning)
                    listener.onSaved(uri, false)
                    endSave()
                }
            } catch (e: Exception) {
                handler.post {
                    listener.onNotice("Gagal menyimpan foto: ${e.message ?: "penyimpanan penuh?"}")
                    endSave()
                }
            }
        }
    }

    /** With a look active the save policy decides between original, graded, or both files. */
    private fun saveShot(bytes: ByteArray, lk: Look, cb: ColorCube?, policy: CapturePolicy, stamp: String): Pair<Uri, String?> {
        val original = "CAMBIX_$stamp.jpg"
        if (policy == CapturePolicy.PREVIEW_ONLY || lk.isNeutral) {
            return Pair(MediaSaver.saveImage(context, bytes, original), null)
        }
        val graded = try {
            PhotoFinisher.apply(bytes, lk, cb)
        } catch (e: Throwable) {
            null
        } ?: return Pair(MediaSaver.saveImage(context, bytes, original), "Filter gagal diterapkan (memori kurang), foto asli yang disimpan.")
        return if (policy == CapturePolicy.BOTH) {
            MediaSaver.saveImage(context, bytes, original)
            Pair(MediaSaver.saveImage(context, graded, "CAMBIX_${stamp}_EDIT.jpg"), null)
        } else {
            Pair(MediaSaver.saveImage(context, graded, original), null)
        }
    }

    private fun maybeFinishLive(job: LiveJob) {
        if (liveJob !== job) return
        val jpeg = job.jpeg ?: return
        val clip = job.clip
        if (clip == null && !job.clipMissing) return
        liveJob = null
        val orientation = outputOrientation()
        io.execute {
            try {
                val (uri, isLive) = saveLive(jpeg, clip, orientation)
                handler.post {
                    if (!isLive) listener.onNotice("Gerakan Live Photo tidak terekam, disimpan sebagai foto biasa.")
                    listener.onSaved(uri, isLive)
                    endSave()
                }
            } catch (e: Exception) {
                handler.post {
                    listener.onNotice("Gagal menyimpan Live Photo: ${e.message ?: "penyimpanan penuh?"}")
                    endSave()
                }
            }
        }
    }

    /** Falls back to the plain JPEG when the clip cannot be muxed, so the shot is never lost. */
    private fun saveLive(jpeg: ByteArray, clip: LiveClip?, orientation: Int): Pair<Uri, Boolean> {
        val stamp = MediaSaver.stamp()
        if (clip != null) {
            val tmp = File(context.cacheDir, "live_${System.nanoTime()}.mp4")
            try {
                val presentationUs = ClipWriter.writeMp4(tmp, clip, orientation)
                val combined = MotionPhoto.build(jpeg, tmp.readBytes(), presentationUs)
                return Pair(MediaSaver.saveImage(context, combined, "CAMBIX_LIVE_$stamp.jpg"), true)
            } catch (e: Exception) {
                // fall through to the still-only save below
            } finally {
                tmp.delete()
            }
        }
        return Pair(MediaSaver.saveImage(context, jpeg, "CAMBIX_$stamp.jpg"), false)
    }

    // ---- video ----

    private fun finishRecording() {
        val r = recorder
        val file = recorderFile
        if (!recording || r == null) return
        recording = false
        try {
            session?.stopRepeating()
            session?.abortCaptures()
        } catch (_: Exception) {
        }
        var ok = true
        try {
            r.stop()
        } catch (e: RuntimeException) {
            ok = false
        }
        recorderFile = null
        listener.onRecording(false)
        needsReconfigure = true
        if (ok && file != null) {
            io.execute {
                try {
                    val uri = MediaSaver.saveVideo(context, file, "CAMBIX_${MediaSaver.stamp()}.mp4")
                    handler.post { listener.onVideoSaved(uri) }
                } catch (e: Exception) {
                    handler.post { listener.onNotice("Gagal menyimpan video: ${e.message ?: "penyimpanan penuh?"}") }
                } finally {
                    file.delete()
                }
            }
        } else {
            file?.delete()
            listener.onNotice("Rekaman terlalu singkat, tidak disimpan.")
        }
    }

    // ---- helpers ----

    private fun setBusy(value: Boolean) {
        if (busy != value) {
            busy = value
            listener.onBusy(value)
        }
    }

    private fun fail(message: String) {
        failed = true
        transition = false
        listener.onError(message)
    }

    private fun modeName(m: CaptureMode) = when (m) {
        CaptureMode.PRO -> "Pro"
        CaptureMode.VIDEO -> "Video"
        CaptureMode.PHOTO -> "Foto"
        CaptureMode.LIVE -> "Live Photo"
    }

    private fun errorText(error: Int) = when (error) {
        CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "Kamera sedang dipakai aplikasi lain."
        CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "Terlalu banyak kamera yang aktif."
        CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "Kamera dinonaktifkan oleh kebijakan perangkat."
        CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "Kamera mengalami gangguan. Coba buka ulang."
        CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "Layanan kamera Android bermasalah."
        else -> "Kesalahan kamera (kode $error)."
    }

    companion object {
        const val LIVE_FPS = 30
    }
}
