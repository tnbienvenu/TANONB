package com.cardiosuivi.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Interface commune aux fournisseurs d'IA (architecture extensible à d'autres fournisseurs). */
interface AiClient {
    suspend fun complete(system: String, user: String, apiKey: String, model: String): String
}

internal val aiJson = Json { ignoreUnknownKeys = true }

fun defaultHttpClientForApp(): OkHttpClient = defaultHttpClient()

internal fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .callTimeout(200, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

/** Une clé d'API ne contient que des caractères ASCII imprimables (sinon : copier-coller erroné). */
fun isValidApiKey(key: String): Boolean = key.isNotEmpty() && key.length <= 512 && key.all { it.code in 0x21..0x7e }

internal fun requireValidKey(key: String) {
    if (!isValidApiKey(key)) throw AiException("Clé d'API invalide : caractères non autorisés (vérifiez le copier-coller).")
}

internal fun httpError(code: Int, body: String?): AiException {
    val detail = runCatching {
        aiJson.parseToJsonElement(body ?: "").jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()
    val msg = when (code) {
        400 -> "Requête refusée par le fournisseur (vérifiez le nom du modèle)."
        401, 403 -> "Clé d'API invalide ou non autorisée."
        404 -> "Modèle ou service introuvable (vérifiez le nom du modèle)."
        429 -> "Limite d'utilisation atteinte chez le fournisseur ; réessayez plus tard."
        in 500..599 -> "Service du fournisseur momentanément indisponible."
        else -> "Erreur du fournisseur (HTTP $code)."
    }
    return AiException(if (detail != null) "$msg\nDétail : $detail" else msg)
}

/**
 * Client HTTP de l'API Messages d'Anthropic (POST /v1/messages).
 * Pour les modèles Claude récents, la réflexion adaptative est active par défaut ; l'effort est fixé à « medium »
 * et le repli serveur (« fallbacks ») est activé pour les modèles qui le prennent en charge.
 */
class AnthropicClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://api.anthropic.com",
) : AiClient {

    private fun supportsFallbacks(model: String) =
        model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5-5") || model.startsWith("claude-fable-5")

    private fun supportsEffort(model: String) =
        model.startsWith("claude-opus-5") || model.startsWith("claude-sonnet-5") || model.startsWith("claude-fable-5") ||
            model.startsWith("claude-haiku-5") || model.startsWith("claude-opus-4")

    override suspend fun complete(system: String, user: String, apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        requireValidKey(apiKey)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 16000)
            put("system", system)
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "user"); put("content", user) })
            }
            if (supportsEffort(model)) putJsonObject("output_config") { put("effort", "medium") }
            if (supportsFallbacks(model)) put("fallbacks", "default")
        }
        val req = Request.Builder()
            .url("$baseUrl/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2026-10-21")
            .apply { if (supportsFallbacks(model)) header("anthropic-beta", "server-side-fallback-2026-07-01") }
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val (code, text) = try {
            http.newCall(req).execute().use { it.code to it.body?.string() }
        } catch (e: IOException) {
            throw AiException("Connexion au service impossible. Vérifiez votre connexion Internet.", e)
        }
        if (code !in 200..299) throw httpError(code, text)
        parseAnthropic(text ?: throw AiException("Réponse vide du fournisseur."))
    }

    companion object {
        fun parseAnthropic(text: String): String {
            val root = runCatching { aiJson.parseToJsonElement(text).jsonObject }.getOrElse { throw AiException("Réponse illisible du fournisseur.", it) }
            val stop = root["stop_reason"]?.jsonPrimitive?.contentOrNull
            if (stop == "refusal") throw AiException("Le modèle a décliné cette demande. Reformulez votre question ou utilisez l'analyse locale.")
            val content = root["content"] as? JsonArray ?: throw AiException("Réponse sans contenu.")
            val out = content.mapNotNull { b ->
                val o = b.jsonObject
                if (o["type"]?.jsonPrimitive?.contentOrNull == "text") o["text"]?.jsonPrimitive?.contentOrNull else null
            }.joinToString("\n").trim()
            if (out.isEmpty()) throw AiException("Réponse vide du modèle.")
            return if (stop == "max_tokens") "$out\n\n[Réponse tronquée par la limite de longueur.]" else out
        }
    }
}

/** Client HTTP de l'API Chat Completions d'OpenAI (POST /v1/chat/completions). */
class OpenAiClient(
    private val http: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = "https://api.openai.com",
) : AiClient {
    override suspend fun complete(system: String, user: String, apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        requireValidKey(apiKey)
        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }
        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val (code, text) = try {
            http.newCall(req).execute().use { it.code to it.body?.string() }
        } catch (e: IOException) {
            throw AiException("Connexion au service impossible. Vérifiez votre connexion Internet.", e)
        }
        if (code !in 200..299) throw httpError(code, text)
        parseOpenAi(text ?: throw AiException("Réponse vide du fournisseur."))
    }

    companion object {
        fun parseOpenAi(text: String): String {
            val root: JsonObject = runCatching { aiJson.parseToJsonElement(text).jsonObject }.getOrElse { throw AiException("Réponse illisible du fournisseur.", it) }
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: throw AiException("Réponse sans contenu.")
            val msg = choice["message"]?.jsonObject
            msg?.get("refusal")?.jsonPrimitive?.contentOrNull?.let { throw AiException("Le modèle a décliné cette demande : $it") }
            val content = msg?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
            if (content.isNullOrEmpty()) throw AiException("Réponse vide du modèle.")
            return content
        }
    }
}
