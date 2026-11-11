package com.shii.dev.cambix.grade

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.shii.dev.cambix.Prefs
import org.json.JSONObject
import java.io.File
import java.io.IOException

class LookEntry(
    val id: String,
    val name: String,
    val category: LookCategory,
    val look: Look,
    val builtIn: Boolean,
)

/**
 * Everything the user can pick as a look: the original built-ins, LUTs they imported (.cube files kept
 * in app storage) and presets they saved. Ids tell the sources apart: bi:, imp:, pre:.
 */
class LookLibrary private constructor(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)
    private val cubes = HashMap<String, ColorCube>()
    private val lutDir: File get() = File(app.filesDir, "luts").also { it.mkdirs() }
    private val presetDir: File get() = File(app.filesDir, "presets").also { it.mkdirs() }

    fun entries(): List<LookEntry> {
        val out = ArrayList<LookEntry>()
        BuiltIns.all.forEach { out.add(LookEntry(it.id, it.name, it.category, it.look, true)) }
        lutDir.listFiles { f -> f.extension.equals("cube", true) }?.sortedBy { it.name }?.forEach {
            val id = "imp:${it.name}"
            out.add(LookEntry(id, it.nameWithoutExtension.replace('_', ' '), LookCategory.IMPORTED, Look(lutId = id), false))
        }
        presetDir.listFiles { f -> f.extension.equals("json", true) }?.sortedBy { it.name }?.forEach {
            try {
                val o = JSONObject(it.readText())
                out.add(LookEntry("pre:${it.name}", o.optString("name", "Preset"), LookCategory.PRESET, LookJson.fromJson(o.getJSONObject("look")), false))
            } catch (e: Exception) {
                // A damaged preset file is skipped instead of breaking the whole list.
            }
        }
        return out
    }

    fun find(id: String?): LookEntry? = if (id == null) null else entries().firstOrNull { it.id == id }

    fun cube(lutId: String?): ColorCube? {
        if (lutId == null) return null
        synchronized(cubes) {
            cubes[lutId]?.let { return it }
            val cube: ColorCube? = when {
                lutId.startsWith("bi:") -> BuiltIns.byId(lutId.removePrefix("bi:"))?.recipe?.build(33)
                lutId.startsWith("imp:") -> try {
                    ColorCube.parseCube(File(lutDir, lutId.removePrefix("imp:")).readText())
                } catch (e: Exception) {
                    null
                }
                else -> null
            }
            if (cube != null) cubes[lutId] = cube
            return cube
        }
    }

    /** The look the camera applies, from the choice saved in Discover. */
    fun activeLook(): Pair<Look, ColorCube?> {
        val entry = find(prefs.activeLookId) ?: return Pair(Look.NEUTRAL, null)
        val look = if (entry.look.lutId != null) entry.look.copy(lutIntensity = prefs.activeLookIntensity.coerceIn(0f, 1f)) else entry.look
        return Pair(look, cube(look.lutId))
    }

    fun favorites(): Set<String> = prefs.favorites

    fun toggleFavorite(id: String): Boolean {
        val set = prefs.favorites.toMutableSet()
        val added = set.add(id)
        if (!added) set.remove(id)
        prefs.favorites = set
        return added
    }

    /** Throws IllegalArgumentException with a message safe to show, or IOException if the file cannot be read. */
    fun importCube(resolver: ContentResolver, uri: Uri, displayName: String): LookEntry {
        val text = resolver.openInputStream(uri)?.use { s ->
            val bytes = s.readBytes()
            if (bytes.size > MAX_CUBE_BYTES) throw IllegalArgumentException("File LUT terlalu besar (maksimum 20 MB)")
            String(bytes, Charsets.UTF_8)
        } ?: throw IOException("File LUT tidak bisa dibuka")
        ColorCube.parseCube(text)
        val base = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "LUT" }
        var file = File(lutDir, base.replace(' ', '_') + ".cube")
        var n = 2
        while (file.exists()) file = File(lutDir, base.replace(' ', '_') + "_$n.cube").also { n++ }
        file.writeText(text)
        val id = "imp:${file.name}"
        return LookEntry(id, file.nameWithoutExtension.replace('_', ' '), LookCategory.IMPORTED, Look(lutId = id), false)
    }

    fun exportCubeText(entry: LookEntry): String? = cube(entry.look.lutId)?.toCubeText(entry.name)

    fun savePreset(name: String, look: Look): LookEntry {
        val file = File(presetDir, "preset_${System.currentTimeMillis()}.json")
        val clean = name.trim().ifEmpty { "Preset" }.take(40)
        file.writeText(JSONObject().put("name", clean).put("look", LookJson.toJson(look)).toString())
        return LookEntry("pre:${file.name}", clean, LookCategory.PRESET, look, false)
    }

    fun presetJson(entry: LookEntry): String =
        JSONObject().put("name", entry.name).put("look", LookJson.toJson(entry.look)).toString(2)

    fun importPresetJson(text: String): LookEntry {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("File preset bukan JSON yang valid")
        }
        val look = try {
            LookJson.fromJson(o.getJSONObject("look"))
        } catch (e: Exception) {
            throw IllegalArgumentException("File preset tidak berisi data look")
        }
        return savePreset(o.optString("name", "Preset impor"), look)
    }

    fun delete(entry: LookEntry) {
        if (entry.builtIn) return
        val dir = if (entry.id.startsWith("imp:")) lutDir else presetDir
        File(dir, entry.id.substringAfter(':')).delete()
        synchronized(cubes) { cubes.remove(entry.id) }
        if (prefs.activeLookId == entry.id) prefs.activeLookId = null
        prefs.favorites = prefs.favorites - entry.id
    }

    companion object {
        private const val MAX_CUBE_BYTES = 20 * 1024 * 1024

        @Volatile
        private var instance: LookLibrary? = null

        fun get(context: Context): LookLibrary =
            instance ?: synchronized(this) { instance ?: LookLibrary(context).also { instance = it } }
    }
}
