package com.shii.dev.cambix.camera

import android.graphics.Rect
import android.media.MediaFormat
import android.util.Range
import android.util.Size

enum class CaptureMode { PRO, VIDEO, PHOTO, LIVE }

enum class FlashMode { OFF, AUTO, ON }

/** Everything below is read from CameraCharacteristics; nothing is assumed. */
class CameraCaps(
    val cameraId: String,
    val facingBack: Boolean,
    val sensorOrientation: Int,
    val hardwareLevel: String,
    val activeArray: Rect,
    val hasFlash: Boolean,
    val zoomMin: Float,
    val zoomMax: Float,
    val zoomByRatio: Boolean,
    val manualSensor: Boolean,
    val isoMin: Int,
    val isoMax: Int,
    val exposureMinNs: Long,
    val exposureMaxNs: Long,
    val minFocusDiopters: Float,
    val evMin: Int,
    val evMax: Int,
    val evStep: Float,
    val rawSupported: Boolean,
    val afModes: Set<Int>,
    val fpsRanges: List<Range<Int>>,
    val videoStabilization: Boolean,
    val opticalStabilization: Boolean,
)

class StreamPlan(
    val previewSize: Size,
    val stillSize: Size?,
    val videoSize: Size?,
    val videoFps: Int,
    val fpsRange: Range<Int>?,
    val note: String? = null,
    val rawSize: Size? = null,
    val hevc: Boolean = false,
) {
    fun copyWith(stillSize: Size? = this.stillSize, videoFps: Int = this.videoFps, fpsRange: Range<Int>? = this.fpsRange, note: String? = this.note) =
        StreamPlan(previewSize, stillSize, videoSize, videoFps, fpsRange, note, rawSize, hevc)
}

/** One resolution and frame-rate pair, and whether this camera and encoder agree to it. */
class VideoOption(val height: Int, val fps: Int, val supported: Boolean)

class ActiveConfig(
    val mode: CaptureMode,
    val caps: CameraCaps,
    val plan: StreamPlan,
    val recordsAudio: Boolean,
    val videoOptions: List<VideoOption> = emptyList(),
    val hevcSupported: Boolean = false,
    val gpuPreview: Boolean = false,
)

class LiveClip(
    val samples: List<EncodedSample>,
    val anchorUs: Long,
    val format: MediaFormat,
)

enum class CapturePolicy { PREVIEW_ONLY, APPLY, BOTH }

class EngineSettings(
    val liveHalfSeconds: Float = 1.5f,
    val videoHeight: Int = 1080,
    val videoFps: Int = 60,
    val hevc: Boolean = false,
    val gpuPreview: Boolean = true,
    val capturePolicy: CapturePolicy = CapturePolicy.PREVIEW_ONLY,
)
