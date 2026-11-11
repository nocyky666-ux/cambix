package com.shii.dev.cambix.ai

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** The message is always safe to show: the key is stripped from anything the server echoes back. */
class AiException(message: String) : Exception(message)

class AiClient(private val store: ProviderStore) {

    suspend fun ask(p: ProviderConfig, prompt: String, jpeg: ByteArray?): String =
        withContext(Dispatchers.IO) { askBlocking(p, prompt, jpeg) }

    suspend fun listModels(p: ProviderConfig): List<String> =
        withContext(Dispatchers.IO) { listModelsBlocking(p) }

    suspend fun test(p: ProviderConfig): String = withContext(Dispatchers.IO) {
        if (p.adapter == AdapterType.CUSTOM) {
            askBlocking(p, "Balas satu kata: siap", null)
            "Tersambung. Server membalas permintaan uji."
        } else {
            val models = listModelsBlocking(p)
            "Tersambung. ${models.size} model tersedia."
        }
    }

    private fun askBlocking(p: ProviderConfig, prompt: String, jpeg: ByteArray?): String {
        val key = store.apiKey(p.id)
        val b64 = jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val (headers, query) = auth(p, key)
        val path = p.endpoint.replace("{model}", p.model)
        val url = withQuery(join(p.baseUrl, path), query)
        val body = when (p.adapter) {
            AdapterType.OPENAI -> openAiBody(p, prompt, b64)
            AdapterType.GEMINI -> geminiBody(p, prompt, b64)
            AdapterType.CUSTOM -> fill(p.bodyTemplate, p, prompt, b64)
        }
        val text = http(p, "POST", url, headers, body, key)
        return extractText(p, text)
    }

    private fun listModelsBlocking(p: ProviderConfig): List<String> {
        if (p.adapter == AdapterType.CUSTOM) throw AiException("Adapter kustom tidak punya daftar model. Isi nama model secara manual.")
        val key = store.apiKey(p.id)
        val (headers, query) = auth(p, key)
        val url = withQuery(join(p.baseUrl, "models"), query)
        val json = JSONObject(http(p, "GET", url, headers, null, key))
        val out = ArrayList<String>()
        if (p.adapter == AdapterType.OPENAI) {
            val arr = json.optJSONArray("data") ?: JSONArray()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.optString("id")?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        } else {
            val arr = json.optJSONArray("models") ?: JSONArray()
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("name")?.removePrefix("models/")?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
            }
        }
        return out.sorted()
    }

    private fun openAiBody(p: ProviderConfig, prompt: String, b64: String?): String {
        val content: Any = if (b64 == null) {
            prompt
        } else {
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
        }
        return JSONObject()
            .put("model", p.model)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            .put("max_tokens", p.maxTokens)
            .put("temperature", p.temperature.toDouble())
            .toString()
    }

    private fun geminiBody(p: ProviderConfig, prompt: String, b64: String?): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (b64 != null) parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("maxOutputTokens", p.maxTokens).put("temperature", p.temperature.toDouble()))
            .toString()
    }

    /** Placeholders are replaced by ready-quoted JSON strings, so the template author does not add quotes. */
    private fun fill(template: String, p: ProviderConfig, prompt: String, b64: String?): String = template
        .replace("{{prompt}}", JSONObject.quote(prompt))
        .replace("{{image_base64}}", JSONObject.quote(b64 ?: ""))
        .replace("{{model}}", JSONObject.quote(p.model))
        .replace("{{max_tokens}}", p.maxTokens.toString())
        .replace("{{temperature}}", p.temperature.toString())

    private fun extractText(p: ProviderConfig, raw: String): String {
        val json = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw AiException("Balasan server bukan JSON.")
        }
        val found: Any? = when (p.adapter) {
            AdapterType.OPENAI -> JsonPath.find(json, "choices.0.message.content")
            AdapterType.GEMINI -> geminiText(json)
            AdapterType.CUSTOM -> JsonPath.find(json, p.responsePath)
        }
        val text = when (found) {
            is String -> found
            is JSONArray -> (0 until found.length()).joinToString("") { found.optJSONObject(it)?.optString("text") ?: "" }
            else -> null
        }
        if (text.isNullOrBlank()) throw AiException("Balasan kosong atau path respons tidak cocok.")
        return text
    }

    private fun geminiText(json: JSONObject): String? {
        val parts = JsonPath.find(json, "candidates.0.content.parts") as? JSONArray ?: return null
        return (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text") ?: "" }
    }

    private fun auth(p: ProviderConfig, key: String?): Pair<Map<String, String>, String> {
        val headers = LinkedHashMap<String, String>(p.headers)
        var query = ""
        if (!key.isNullOrBlank()) {
            when (p.authType) {
                AuthType.BEARER -> headers["Authorization"] = "Bearer $key"
                AuthType.HEADER -> headers[p.authName.ifBlank { "x-api-key" }] = key
                AuthType.QUERY -> query = URLEncoder.encode(p.authName.ifBlank { "key" }, "UTF-8") + "=" + URLEncoder.encode(key, "UTF-8")
                AuthType.NONE -> {}
            }
        }
        return Pair(headers, query)
    }

    private fun join(base: String, path: String): String {
        val b = base.trim().trimEnd('/')
        return if (path.isBlank()) b else b + "/" + path.trim().trimStart('/')
    }

    private fun withQuery(url: String, query: String): String =
        if (query.isEmpty()) url else url + (if (url.contains('?')) "&" else "?") + query

    private fun http(p: ProviderConfig, method: String, url: String, headers: Map<String, String>, body: String?, key: String?): String {
        val https = url.startsWith("https://", ignoreCase = true)
        val http = url.startsWith("http://", ignoreCase = true)
        if (!https && !(http && p.allowHttp)) {
            throw AiException("Alamat harus HTTPS. Aktifkan HTTP hanya untuk server lokal milik Anda.")
        }
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiException("Alamat tidak valid.")
        }
        try {
            val timeout = p.timeoutSec.coerceIn(5, 180) * 1000
            conn.requestMethod = method
            conn.connectTimeout = timeout
            conn.readTimeout = timeout
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { readLimited(it) } ?: ""
            if (code !in 200..299) throw AiException("Server menjawab HTTP $code. ${clean(text, key).take(240)}")
            return text
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("Koneksi gagal: ${clean(e.message ?: e.javaClass.simpleName, key)}")
        } finally {
            conn.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        stream.use { s ->
            while (out.size() < MAX_RESPONSE) {
                val n = s.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        return out.toString("UTF-8")
    }

    private fun clean(text: String, key: String?): String =
        if (key.isNullOrBlank()) text else text.replace(key, "***")

    private companion object {
        const val MAX_RESPONSE = 4 * 1024 * 1024
    }
}

object JsonPath {
    /** Walks "choices.0.message.content" style paths through objects and arrays. */
    fun find(root: Any?, path: String): Any? {
        var current: Any? = root
        for (segment in path.split('.').filter { it.isNotBlank() }) {
            val node = current
            val next: Any? = when (node) {
                is JSONObject -> node.opt(segment)
                is JSONArray -> {
                    val index = segment.toIntOrNull()
                    if (index == null) null else node.opt(index)
                }
                else -> null
            }
            if (next == null || next == JSONObject.NULL) return null
            current = next
        }
        return current
    }
}
