package com.shii.dev.cambix.grade

import org.json.JSONArray
import org.json.JSONObject

/** Preset and project files. Unknown or missing keys fall back to the neutral value, so old files keep loading. */
object LookJson {
    fun toJson(l: Look): JSONObject {
        val o = JSONObject()
        o.put("exposure", l.exposure.toDouble()).put("contrast", l.contrast.toDouble())
            .put("highlights", l.highlights.toDouble()).put("shadows", l.shadows.toDouble())
            .put("whites", l.whites.toDouble()).put("blacks", l.blacks.toDouble())
            .put("temperature", l.temperature.toDouble()).put("tint", l.tint.toDouble())
            .put("saturation", l.saturation.toDouble()).put("vibrance", l.vibrance.toDouble())
            .put("fade", l.fade.toDouble()).put("vignette", l.vignette.toDouble()).put("grain", l.grain.toDouble())
            .put("sharpen", l.sharpen.toDouble()).put("clarity", l.clarity.toDouble())
            .put("glow", l.glow.toDouble()).put("aberration", l.aberration.toDouble())
            .put("sunStrength", l.sunStrength.toDouble()).put("sunX", l.sunX.toDouble()).put("sunY", l.sunY.toDouble())
            .put("sunRadius", l.sunRadius.toDouble()).put("sunWarmth", l.sunWarmth.toDouble())
            .put("lutIntensity", l.lutIntensity.toDouble())
        if (l.lutId != null) o.put("lutId", l.lutId)
        val bands = JSONArray()
        l.hsl.forEach { bands.put(JSONArray().put(it.hue.toDouble()).put(it.sat.toDouble()).put(it.lum.toDouble())) }
        o.put("hsl", bands)
        return o
    }

    fun fromJson(o: JSONObject): Look {
        fun f(key: String, fallback: Float) = o.optDouble(key, fallback.toDouble()).toFloat()
        val bands = o.optJSONArray("hsl")
        val hsl = List(8) { i ->
            val a = bands?.optJSONArray(i)
            if (a == null) HslBand() else HslBand(a.optDouble(0, 0.0).toFloat(), a.optDouble(1, 0.0).toFloat(), a.optDouble(2, 0.0).toFloat())
        }
        return Look(
            exposure = f("exposure", 0f), contrast = f("contrast", 0f), highlights = f("highlights", 0f),
            shadows = f("shadows", 0f), whites = f("whites", 0f), blacks = f("blacks", 0f),
            temperature = f("temperature", 0f), tint = f("tint", 0f), saturation = f("saturation", 0f),
            vibrance = f("vibrance", 0f), fade = f("fade", 0f), vignette = f("vignette", 0f), grain = f("grain", 0f),
            sharpen = f("sharpen", 0f), clarity = f("clarity", 0f), glow = f("glow", 0f), aberration = f("aberration", 0f),
            sunStrength = f("sunStrength", 0f), sunX = f("sunX", 0.8f), sunY = f("sunY", 0.2f),
            sunRadius = f("sunRadius", 0.5f), sunWarmth = f("sunWarmth", 0.7f),
            hsl = hsl,
            lutId = if (o.has("lutId")) o.optString("lutId") else null,
            lutIntensity = f("lutIntensity", 1f),
        )
    }
}
