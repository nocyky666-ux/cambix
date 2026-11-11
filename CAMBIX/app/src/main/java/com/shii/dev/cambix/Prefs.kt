package com.shii.dev.cambix

import android.content.Context
import com.shii.dev.cambix.camera.CapturePolicy
import com.shii.dev.cambix.camera.CaptureMode
import com.shii.dev.cambix.camera.EngineSettings

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("cambix", Context.MODE_PRIVATE)

    var grid: Boolean
        get() = sp.getBoolean("grid", false)
        set(value) {
            sp.edit().putBoolean("grid", value).apply()
        }

    /** Seconds kept before and after the shutter press in a Live Photo. */
    var liveHalfSeconds: Float
        get() = sp.getFloat("live_half", 1.5f)
        set(value) {
            sp.edit().putFloat("live_half", value).apply()
        }

    var videoHeight: Int
        get() = sp.getInt("video_height", 1080)
        set(value) {
            sp.edit().putInt("video_height", value).apply()
        }

    var videoFps: Int
        get() = sp.getInt("video_fps", 60)
        set(value) {
            sp.edit().putInt("video_fps", value).apply()
        }

    var videoHevc: Boolean
        get() = sp.getBoolean("video_hevc", false)
        set(value) {
            sp.edit().putBoolean("video_hevc", value).apply()
        }

    var gpuPreview: Boolean
        get() = sp.getBoolean("gpu_preview", true)
        set(value) {
            sp.edit().putBoolean("gpu_preview", value).apply()
        }

    var capturePolicy: CapturePolicy
        get() = try {
            CapturePolicy.valueOf(sp.getString("capture_policy", null) ?: CapturePolicy.PREVIEW_ONLY.name)
        } catch (e: IllegalArgumentException) {
            CapturePolicy.PREVIEW_ONLY
        }
        set(value) {
            sp.edit().putString("capture_policy", value.name).apply()
        }

    var activeLookId: String?
        get() = sp.getString("active_look", null)
        set(value) {
            sp.edit().putString("active_look", value).apply()
        }

    var activeLookIntensity: Float
        get() = sp.getFloat("active_look_intensity", 1f)
        set(value) {
            sp.edit().putFloat("active_look_intensity", value).apply()
        }

    /** 0 hemat, 1 seimbang, 2 tinggi, 3 maksimum. Sets the proxy size Studio edits on. */
    var performance: Int
        get() = sp.getInt("performance", 1)
        set(value) {
            sp.edit().putInt("performance", value.coerceIn(0, 3)).apply()
        }

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
        set(value) {
            sp.edit().putStringSet("favorites", value).apply()
        }

    var lastMode: CaptureMode
        get() = try {
            CaptureMode.valueOf(sp.getString("mode", null) ?: CaptureMode.PHOTO.name)
        } catch (e: IllegalArgumentException) {
            CaptureMode.PHOTO
        }
        set(value) {
            sp.edit().putString("mode", value.name).apply()
        }

    fun engineSettings() =
        EngineSettings(liveHalfSeconds, videoHeight, videoFps, videoHevc, gpuPreview && performance > 0, capturePolicy)
}
