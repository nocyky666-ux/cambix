package com.shii.dev.cambix.camera

import android.app.ActivityManager
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import kotlin.math.abs

object SizePicker {
    private const val ASPECT_4_3 = 4f / 3f
    private const val ASPECT_16_9 = 16f / 9f
    private const val TOLERANCE = 0.03f
    private const val MAX_STILL_PIXELS = 12_600_000L
    private const val MAX_PREVIEW_PIXELS = 1920L * 1080L

    private fun area(s: Size): Long = s.width.toLong() * s.height

    private fun matches(s: Size, aspect: Float): Boolean =
        abs(s.width.toFloat() / s.height - aspect) <= TOLERANCE

    private fun largest(
        sizes: Array<Size>?,
        aspect: Float,
        maxPixels: Long,
        maxWidth: Int = Int.MAX_VALUE,
        maxHeight: Int = Int.MAX_VALUE,
        accept: (Size) -> Boolean = { true },
    ): Size? = sizes
        ?.filter { matches(it, aspect) && area(it) <= maxPixels && it.width <= maxWidth && it.height <= maxHeight && accept(it) }
        ?.maxByOrNull { area(it) }

    fun photoPlan(preview: Array<Size>?, jpeg: Array<Size>?): StreamPlan? {
        for (aspect in listOf(ASPECT_4_3, ASPECT_16_9)) {
            val still = largest(jpeg, aspect, MAX_STILL_PIXELS) ?: continue
            val prev = largest(preview, aspect, MAX_PREVIEW_PIXELS, 1920, 1080) ?: continue
            return StreamPlan(prev, still, null, 0, null)
        }
        return null
    }

    fun livePlan(
        preview: Array<Size>?,
        jpeg: Array<Size>?,
        video: Array<Size>?,
        fps: Int,
        codecOk: (Size) -> Boolean,
    ): StreamPlan? {
        for (aspect in listOf(ASPECT_4_3, ASPECT_16_9)) {
            val v = largest(video, aspect, MAX_PREVIEW_PIXELS, 1920, 1080, codecOk) ?: continue
            val p = largest(preview, aspect, MAX_PREVIEW_PIXELS, 1920, 1080) ?: continue
            val s = largest(jpeg, aspect, MAX_STILL_PIXELS) ?: v
            val note = if (aspect == ASPECT_16_9) "Live Photo memakai rasio 16:9 di perangkat ini" else null
            return StreamPlan(p, s, v, fps, null, note)
        }
        return null
    }

    /** Largest 16:9 size not taller than [height] (falling back to 4:3), so "4K" means 3840x2160 only if the camera lists it. */
    fun videoSizeFor(video: Array<Size>?, height: Int): Size? {
        val all = video.orEmpty().filter { it.height <= height }
        val wide = all.filter { matches(it, ASPECT_16_9) }.maxByOrNull { area(it) }
        return wide ?: all.filter { matches(it, ASPECT_4_3) }.maxByOrNull { area(it) }
    }

    fun previewFor(preview: Array<Size>?, video: Size): Size? {
        val aspect = if (matches(video, ASPECT_16_9)) ASPECT_16_9 else ASPECT_4_3
        return largest(preview, aspect, MAX_PREVIEW_PIXELS, 1920, 1080)
    }

    /** Fixed ranges win; among the rest the highest lower bound keeps the frame rate steadiest. */
    fun pickFpsRange(ranges: List<Range<Int>>, fps: Int): Range<Int>? =
        ranges.filter { it.upper == fps }.maxByOrNull { it.lower }
}

object CodecSupport {
    fun avcEncoder(width: Int, height: Int, fps: Int): Boolean = encoder(MediaFormat.MIMETYPE_VIDEO_AVC, width, height, fps)

    fun hevcEncoderPresent(): Boolean = anyEncoder(MediaFormat.MIMETYPE_VIDEO_HEVC)

    fun encoder(mime: String, width: Int, height: Int, fps: Int): Boolean {
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                    (info.getCapabilitiesForType(mime).videoCapabilities?.areSizeAndRateSupported(width, height, fps.toDouble()) == true)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun anyEncoder(mime: String): Boolean = try {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
        }
    } catch (e: Exception) {
        false
    }
}

/** True only when the camera range list, the camera frame duration and the AVC encoder all agree. */
fun videoFpsSupported(
    map: StreamConfigurationMap?,
    ranges: List<Range<Int>>,
    size: Size,
    fps: Int,
    mime: String = MediaFormat.MIMETYPE_VIDEO_AVC,
): Boolean {
    if (map == null) return false
    val listed = map.getOutputSizes(MediaRecorder::class.java)?.any { it.width == size.width && it.height == size.height } == true
    if (!listed || ranges.none { it.upper >= fps }) return false
    val minDuration = map.getOutputMinFrameDuration(MediaRecorder::class.java, size)
    val durationOk = minDuration <= 0L || minDuration <= 1_000_000_000L / fps + 700_000L
    return durationOk && CodecSupport.encoder(mime, size.width, size.height, fps)
}

fun readCaps(manager: CameraManager, id: String): Pair<CameraCaps, StreamConfigurationMap?> {
    val ch = manager.getCameraCharacteristics(id)
    val level = when (ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }
    val capabilities = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
    val zoomRange: Range<Float>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) else null
    val maxDigital = ch.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
    val iso = ch.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    val exposure = ch.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    val ev = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
    val stabModes = ch.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
    val oisModes = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
    val caps = CameraCaps(
        cameraId = id,
        facingBack = ch.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK,
        sensorOrientation = ch.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
        hardwareLevel = level,
        activeArray = ch.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 4000, 3000),
        hasFlash = ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
        zoomMin = zoomRange?.lower ?: 1f,
        zoomMax = zoomRange?.upper ?: maxDigital,
        zoomByRatio = zoomRange != null,
        manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in capabilities && iso != null && exposure != null,
        isoMin = iso?.lower ?: 100,
        isoMax = iso?.upper ?: 100,
        exposureMinNs = exposure?.lower ?: 1_000_000L,
        exposureMaxNs = exposure?.upper ?: 1_000_000L,
        minFocusDiopters = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
        evMin = ev?.lower ?: 0,
        evMax = ev?.upper ?: 0,
        evStep = ch.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f,
        rawSupported = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities,
        afModes = ch.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toSet() ?: emptySet(),
        fpsRanges = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList() ?: emptyList(),
        videoStabilization = stabModes?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true,
        opticalStabilization = oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true,
    )
    return Pair(caps, ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP))
}

fun pickCameraId(manager: CameraManager, back: Boolean): String? {
    val want = if (back) CameraCharacteristics.LENS_FACING_BACK else CameraCharacteristics.LENS_FACING_FRONT
    return manager.cameraIdList.firstOrNull { id ->
        val ch = manager.getCameraCharacteristics(id)
        ch.get(CameraCharacteristics.LENS_FACING) == want &&
            ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                ?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) == true
    }
}

class VideoRow(val label: String, val supported: Boolean)

class LensReport(
    val title: String,
    val level: String,
    val maxPhotoMp: Float,
    val rawSupported: Boolean,
    val manualSensor: Boolean,
    val zoomText: String,
    val flash: Boolean,
    val video: List<VideoRow>,
)

class DeviceReport(
    val device: String,
    val android: String,
    val ramGb: Float,
    val h264: Boolean,
    val hevc: Boolean,
    val lenses: List<LensReport>,
) {
    fun asText(): String = buildString {
        appendLine("CAMBIX 1.0.0 | $device | Android $android | RAM ${"%.1f".format(ramGb)} GB")
        appendLine("Encoder H.264: ${yn(h264)} | HEVC: ${yn(hevc)}")
        for (l in lenses) {
            appendLine("${l.title}: level ${l.level}, foto maks ${"%.1f".format(l.maxPhotoMp)} MP, RAW ${yn(l.rawSupported)}, manual ${yn(l.manualSensor)}, zoom ${l.zoomText}, flash ${yn(l.flash)}")
            appendLine(l.video.joinToString(", ") { "${it.label} ${yn(it.supported)}" })
        }
    }

    private fun yn(b: Boolean) = if (b) "ya" else "tidak"
}

object DeviceInspector {
    fun inspect(context: Context): DeviceReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val lenses = ArrayList<LensReport>()
        for (back in listOf(true, false)) {
            val id: String? = try {
                pickCameraId(manager, back)
            } catch (e: Exception) {
                null
            }
            if (id == null) continue
            try {
                val (caps, map) = readCaps(manager, id)
                val jpeg = map?.getOutputSizes(ImageFormat.JPEG)
                val maxMp = (jpeg?.maxOfOrNull { it.width.toLong() * it.height } ?: 0L) / 1_000_000f
                val rows = listOf(
                    Triple("1080p30", Size(1920, 1080), 30),
                    Triple("1080p60", Size(1920, 1080), 60),
                    Triple("4K30", Size(3840, 2160), 30),
                    Triple("4K60", Size(3840, 2160), 60),
                ).map { (label, size, fps) -> VideoRow(label, videoFpsSupported(map, caps.fpsRanges, size, fps)) }
                lenses.add(
                    LensReport(
                        title = if (back) "Kamera belakang" else "Kamera depan",
                        level = caps.hardwareLevel,
                        maxPhotoMp = maxMp,
                        rawSupported = caps.rawSupported,
                        manualSensor = caps.manualSensor,
                        zoomText = "%.1fx - %.1fx".format(caps.zoomMin, caps.zoomMax),
                        flash = caps.hasFlash,
                        video = rows,
                    )
                )
            } catch (e: Exception) {
                // A lens that cannot be inspected is left out instead of being guessed.
            }
        }
        return DeviceReport(
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = Build.VERSION.RELEASE,
            ramGb = mem.totalMem / 1_073_741_824f,
            h264 = CodecSupport.encoder(MediaFormat.MIMETYPE_VIDEO_AVC, 1280, 720, 30),
            hevc = CodecSupport.hevcEncoderPresent(),
            lenses = lenses,
        )
    }
}

fun largestRawSize(map: StreamConfigurationMap?): Size? =
    map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }

/** Sizes the engine asks the camera for, per mode. */
fun streamSizes(map: StreamConfigurationMap?): Triple<Array<Size>?, Array<Size>?, Array<Size>?> =
    Triple(
        map?.getOutputSizes(SurfaceTexture::class.java),
        map?.getOutputSizes(ImageFormat.JPEG),
        map?.getOutputSizes(MediaCodec::class.java),
    )
