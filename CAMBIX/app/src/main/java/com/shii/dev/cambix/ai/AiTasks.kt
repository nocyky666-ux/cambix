package com.shii.dev.cambix.ai

import com.shii.dev.cambix.grade.Look
import org.json.JSONObject

class LookSuggestion(val look: Look, val tips: String)

/** What CAMBIX asks a vision model for: a gentle correction of the photo plus a few composition tips. */
object AiTasks {
    fun prompt(lutIds: List<String>): String =
        "Kamu asisten editing foto. Lihat foto ini dan balas HANYA dengan satu objek JSON, tanpa teks lain.\n" +
            "Kunci angka (boleh dihilangkan jika tidak perlu): exposure (-1.5..1.5), contrast (-1..1), highlights (-1..1), " +
            "shadows (-1..1), temperature (-1..1), tint (-1..1), saturation (-1..1), vibrance (-1..1), vignette (-1..1), fade (0..1).\n" +
            "Kunci \"lut\": salah satu dari [${lutIds.joinToString(", ")}] atau kosong.\n" +
            "Kunci \"tips\": maksimal 3 kalimat bahasa Indonesia tentang komposisi (garis horizon, posisi subjek, aturan sepertiga, pemotongan).\n" +
            "Pilih koreksi yang halus dan alami. Jangan mengubah wajah atau identitas orang."

    suspend fun suggest(client: AiClient, provider: ProviderConfig, jpeg: ByteArray, base: Look, lutIds: List<String>): LookSuggestion =
        parse(client.ask(provider, prompt(lutIds), jpeg), base, lutIds.toSet())

    fun parse(reply: String, base: Look, lutIds: Set<String>): LookSuggestion {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("Balasan AI tidak berisi JSON yang bisa dibaca.")
        val json = try {
            JSONObject(reply.substring(start, end + 1))
        } catch (e: Exception) {
            throw AiException("JSON dari AI rusak.")
        }
        fun num(key: String, lo: Float, hi: Float): Float? =
            if (json.has(key) && !json.isNull(key)) json.optDouble(key).takeIf { !it.isNaN() }?.toFloat()?.coerceIn(lo, hi) else null

        val lut = json.optString("lut", "").trim()
        val look = base.copy(
            exposure = num("exposure", -1.5f, 1.5f) ?: base.exposure,
            contrast = num("contrast", -1f, 1f) ?: base.contrast,
            highlights = num("highlights", -1f, 1f) ?: base.highlights,
            shadows = num("shadows", -1f, 1f) ?: base.shadows,
            temperature = num("temperature", -1f, 1f) ?: base.temperature,
            tint = num("tint", -1f, 1f) ?: base.tint,
            saturation = num("saturation", -1f, 1f) ?: base.saturation,
            vibrance = num("vibrance", -1f, 1f) ?: base.vibrance,
            vignette = num("vignette", -1f, 1f) ?: base.vignette,
            fade = num("fade", 0f, 1f) ?: base.fade,
            lutId = if (lut in lutIds) "bi:$lut" else base.lutId,
            lutIntensity = if (lut in lutIds) 1f else base.lutIntensity,
        )
        return LookSuggestion(look, json.optString("tips", "").trim().take(500))
    }
}
