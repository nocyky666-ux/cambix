package com.shii.dev.cambix.ai

import org.json.JSONArray
import org.json.JSONObject

enum class AdapterType(val label: String) {
    OPENAI("OpenAI-compatible"),
    GEMINI("Gemini"),
    CUSTOM("REST kustom"),
}

enum class AuthType(val label: String) {
    BEARER("Bearer token"),
    HEADER("Header kustom"),
    QUERY("Parameter URL"),
    NONE("Tanpa autentikasi"),
}

/** The API key is not part of this class. It lives encrypted in SecretStore and is never exported. */
data class ProviderConfig(
    val id: String,
    val name: String,
    val adapter: AdapterType,
    val baseUrl: String,
    val endpoint: String,
    val model: String,
    val authType: AuthType,
    val authName: String,
    val headers: Map<String, String>,
    val timeoutSec: Int,
    val maxTokens: Int,
    val temperature: Float,
    val allowHttp: Boolean,
    val bodyTemplate: String,
    val responsePath: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("adapter", adapter.name)
        .put("baseUrl", baseUrl).put("endpoint", endpoint).put("model", model)
        .put("authType", authType.name).put("authName", authName)
        .put("headers", JSONObject(headers))
        .put("timeoutSec", timeoutSec).put("maxTokens", maxTokens).put("temperature", temperature.toDouble())
        .put("allowHttp", allowHttp).put("bodyTemplate", bodyTemplate).put("responsePath", responsePath)

    companion object {
        const val DEFAULT_TEMPLATE =
            "{\"model\": {{model}}, \"max_tokens\": {{max_tokens}}, \"temperature\": {{temperature}}, " +
                "\"messages\": [{\"role\": \"user\", \"content\": {{prompt}}}]}"

        /** Starting values per adapter, so the form is mostly filled in. */
        fun blank(id: String, adapter: AdapterType): ProviderConfig = when (adapter) {
            AdapterType.OPENAI -> ProviderConfig(
                id, "OpenAI-compatible", adapter, "https://api.openai.com/v1", "/chat/completions", "",
                AuthType.BEARER, "", emptyMap(), 60, 800, 0.4f, false, DEFAULT_TEMPLATE, "choices.0.message.content",
            )
            AdapterType.GEMINI -> ProviderConfig(
                id, "Gemini", adapter, "https://generativelanguage.googleapis.com/v1beta", "/models/{model}:generateContent", "",
                AuthType.HEADER, "x-goog-api-key", emptyMap(), 60, 800, 0.4f, false, DEFAULT_TEMPLATE, "candidates.0.content.parts.0.text",
            )
            AdapterType.CUSTOM -> ProviderConfig(
                id, "REST kustom", adapter, "https://", "", "",
                AuthType.BEARER, "", emptyMap(), 60, 800, 0.4f, false, DEFAULT_TEMPLATE, "choices.0.message.content",
            )
        }

        fun fromJson(o: JSONObject, forceId: String? = null): ProviderConfig {
            val headers = LinkedHashMap<String, String>()
            o.optJSONObject("headers")?.let { h ->
                val keys = h.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    headers[k] = h.optString(k)
                }
            }
            val adapter = enumOr(o.optString("adapter"), AdapterType.OPENAI)
            val base = blank(forceId ?: o.optString("id"), adapter)
            return base.copy(
                name = o.optString("name", base.name),
                baseUrl = o.optString("baseUrl", base.baseUrl),
                endpoint = o.optString("endpoint", base.endpoint),
                model = o.optString("model", ""),
                authType = enumOr(o.optString("authType"), base.authType),
                authName = o.optString("authName", base.authName),
                headers = headers,
                timeoutSec = o.optInt("timeoutSec", 60).coerceIn(5, 180),
                maxTokens = o.optInt("maxTokens", 800).coerceIn(16, 8192),
                temperature = o.optDouble("temperature", 0.4).toFloat().coerceIn(0f, 2f),
                allowHttp = o.optBoolean("allowHttp", false),
                bodyTemplate = o.optString("bodyTemplate", base.bodyTemplate),
                responsePath = o.optString("responsePath", base.responsePath),
            )
        }

        fun listToJson(list: List<ProviderConfig>): String {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        private inline fun <reified T : Enum<T>> enumOr(name: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == name } ?: fallback
    }
}
