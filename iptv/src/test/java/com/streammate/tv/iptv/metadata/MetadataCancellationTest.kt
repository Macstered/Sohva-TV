package com.streammate.tv.iptv.metadata

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class MetadataCancellationTest {
    @Test fun identicalReadersShareWorkAndOneReaderCanLeave(): Unit = runBlocking {
        val requests = MetadataRequests<String>()
        val response = CompletableDeferred<String>()
        val entered = CompletableDeferred<Unit>()
        var calls = 0
        val fetch: suspend () -> String = { calls++; entered.complete(Unit); response.await() }
        val first = async(start = CoroutineStart.UNDISPATCHED) { requests.read("same", fetch) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { requests.read("same", fetch) }
        withTimeout(1000) { entered.await() }
        first.cancelAndJoin()
        response.complete("ready")
        assertEquals("ready", second.await())
        assertEquals(1, calls)
    }

    @Test fun lastReaderLeavingCancelsTheActualHttpCall(): Unit = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val cancelled = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.complete(Unit) }
            }).build()
            val requests = MetadataRequests<kotlinx.serialization.json.JsonElement>()
            val reader = launch { requests.read("obsolete") { client.getMetadataJson(Request.Builder().url(server.url("/")).build(), "synthetic") } }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
            reader.cancelAndJoin()
            withTimeout(1000) { cancelled.await() }
            assertEquals(1, server.requestCount)
        }
    }
}
