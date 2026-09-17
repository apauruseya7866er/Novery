package com.emptycastle.novery.data.translate

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slice-07.2a: translation engine + manager.
 * The API engine is verified against a real local HTTP server speaking
 * OpenAI-compatible chat completions (JDK built-in, no mocks).
 */
class TranslationEngineTest {

    private class FakeEngine(
        private val configured: Boolean = true,
        private val prefix: String = "T:"
    ) : TranslationEngine {
        var calls = 0
        override fun isConfigured() = configured
        override suspend fun translate(
            paragraphs: List<String>,
            sourceLang: String,
            targetLang: String
        ): Result<List<String>> {
            calls++
            return Result.success(paragraphs.map { "$prefix$it" })
        }
    }

    @Test
    fun manager_batchesCachesAndStreams() = runBlocking {
        val engine = FakeEngine()
        val manager = TranslationManager(engine, maxBatchParagraphs = 2)
        val input = listOf("a", "b", "c", "d", "e")
        val partials = mutableListOf<Pair<Int, String>>()
        val out = manager.translateChapter(input, "auto", "English") { i, t ->
            partials.add(i to t)
        }
        assertEquals(listOf("T:a", "T:b", "T:c", "T:d", "T:e"), out)
        assertEquals(3, engine.calls) // 2 + 2 + 1
        assertEquals(5, partials.size)
        assertEquals(5, manager.cacheSize())

        // Second run: fully cached, engine untouched.
        engine.calls = 0
        val out2 = manager.translateChapter(input, "auto", "English")
        assertEquals(out, out2)
        assertEquals(0, engine.calls)
    }

    @Test
    fun manager_unconfigured_returnsOriginals() = runBlocking {
        val manager = TranslationManager(FakeEngine(configured = false))
        val input = listOf("a", "b")
        assertEquals(input, manager.translateChapter(input, "auto", "English"))
    }

    @Test
    fun manager_blanksPassThrough() = runBlocking {
        val engine = FakeEngine()
        val manager = TranslationManager(engine)
        val out = manager.translateChapter(listOf("", "x"), "auto", "English")
        assertEquals(listOf("", "T:x"), out)
        assertEquals(1, engine.calls)
    }

    @Test
    fun manager_lruCapRespected() = runBlocking {
        val manager = TranslationManager(FakeEngine(), maxCacheEntries = 3)
        manager.translateChapter(listOf("a", "b", "c", "d"), "auto", "English")
        assertEquals(3, manager.cacheSize())
    }

    @Test
    fun alignResults_numbered() {
        val reply = "1. Uno\n2) Dos\n3 - Tres"
        assertEquals(
            listOf("Uno", "Dos", "Tres"),
            ApiTranslationEngine.alignResults(reply, listOf("a", "b", "c"))
        )
    }

    @Test
    fun alignResults_missingSlotsKeepOriginal() {
        val reply = "2. Only two"
        assertEquals(
            listOf("a", "Only two"),
            ApiTranslationEngine.alignResults(reply, listOf("a", "b"))
        )
    }

    @Test
    fun alignResults_empty() {
        assertTrue(ApiTranslationEngine.alignResults("", listOf("a")).size == 1)
    }

    private fun fakeClient(handler: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> handler(chain.request()) }
            .build()
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    @Test
    fun apiEngine_roundtrip() = runBlocking {
        var sawModel = false
        val client = fakeClient { request ->
            val body = request.body?.let { bufferBody(it) } ?: ""
            sawModel = body.contains("\"model\":\"test-model\"")
            jsonResponse(
                request, 200,
                """{"choices":[{"message":{"role":"assistant","content":"1. Hola\n2. Mundo"}}]}"""
            )
        }
        val engine = ApiTranslationEngine(
            endpoint = "https://x/v1/chat/completions",
            apiKey = "",
            model = "test-model",
            http = client
        )
        assertTrue(engine.isConfigured())
        val out = engine.translate(listOf("Hello", "World"), "English", "Spanish")
        assertTrue(out.isSuccess)
        assertEquals(listOf("Hola", "Mundo"), out.getOrNull())
        assertTrue(sawModel)
    }

    private fun bufferBody(body: okhttp3.RequestBody): String {
        val buffer = okio.Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun apiEngine_unconfigured_fails() = runBlocking {
        val engine = ApiTranslationEngine(endpoint = "", apiKey = "", model = "")
        assertTrue(!engine.isConfigured())
        assertTrue(engine.translate(listOf("x"), "a", "b").isFailure)
    }

    @Test
    fun apiEngine_httpError_isFailure() = runBlocking {
        val client = fakeClient { request -> jsonResponse(request, 401, "") }
        val engine = ApiTranslationEngine(
            endpoint = "https://x/",
            apiKey = "bad",
            model = "m",
            http = client
        )
        assertTrue(engine.translate(listOf("x"), "a", "b").isFailure)
    }
}
