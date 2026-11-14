package com.cardiosuivi.app

import com.cardiosuivi.app.ai.AiException
import com.cardiosuivi.app.ai.AnthropicClient
import com.cardiosuivi.app.ai.OpenAiClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Tests des clients HTTP des fournisseurs d'IA contre un serveur simulé (aucun appel réel). */
class AiClientsTest {
    private lateinit var server: MockWebServer
    private val http = OkHttpClient()

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() = server.shutdown()

    private fun base() = server.url("/").toString().trimEnd('/')

    @Test
    fun anthropicRequestShapeAndParsing() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"id":"m1","type":"message","role":"assistant","stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"1. Données observées\nOK"}]}""",
        ))
        val out = AnthropicClient(http, base()).complete("sys", "question", "sk-ant-test", "claude-opus-5-5")
        assertEquals("1. Données observées\nOK", out)
        val req = server.takeRequest()
        assertEquals("/v1/messages", req.path)
        assertEquals("sk-ant-test", req.getHeader("x-api-key"))
        assertEquals("2026-10-21", req.getHeader("anthropic-version"))
        assertEquals("server-side-fallback-2026-07-01", req.getHeader("anthropic-beta"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("claude-opus-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("sys", body["system"]!!.jsonPrimitive.content)
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("temperature"))
    }

    @Test
    fun anthropicRefusalAndErrors() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"stop_reason":"refusal","content":[]}"""))
        val e1 = assertThrows(AiException::class.java) { kotlinx.coroutines.runBlocking { AnthropicClient(http, base()).complete("s", "u", "k", "claude-opus-5-5") } }
        assertTrue(e1.message!!.contains("décliné"))

        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key"}}"""))
        val e2 = assertThrows(AiException::class.java) { kotlinx.coroutines.runBlocking { AnthropicClient(http, base()).complete("s", "u", "k", "claude-opus-5-5") } }
        assertTrue(e2.message!!.contains("Clé d'API invalide"))
        assertTrue(e2.message!!.contains("invalid x-api-key"))
    }

    @Test
    fun openAiRequestShapeAndParsing() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[{"message":{"role":"assistant","content":"Réponse"}}]}"""))
        val out = OpenAiClient(http, base()).complete("sys", "question", "sk-test", "gpt-5")
        assertEquals("Réponse", out)
        val req = server.takeRequest()
        assertEquals("/v1/chat/completions", req.path)
        assertEquals("Bearer sk-test", req.getHeader("Authorization"))
        val msgs = Json.parseToJsonElement(req.body.readUtf8()).jsonObject["messages"]!!.toString()
        assertTrue(msgs.contains("\"system\"") && msgs.contains("question"))
    }

    @Test
    fun invalidKeyGivesReadableErrorWithoutRequest() {
        val e = assertThrows(AiException::class.java) { kotlinx.coroutines.runBlocking { AnthropicClient(http, base()).complete("s", "u", "clé\u00a0collée", "claude-opus-5-5") } }
        assertTrue(e.message!!.contains("caractères non autorisés"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun openAiRateLimit() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{}"))
        val e = assertThrows(AiException::class.java) { kotlinx.coroutines.runBlocking { OpenAiClient(http, base()).complete("s", "u", "k", "gpt-5") } }
        assertTrue(e.message!!.contains("Limite"))
    }
}
