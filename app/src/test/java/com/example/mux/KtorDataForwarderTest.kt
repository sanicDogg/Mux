package com.example.mux

import com.example.mux.data.forwarder.KtorDataForwarder
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KtorDataForwarderTest {

    @Test
    fun testInvalidUrlReturnsFailure() = runTest {
        val forwarder = KtorDataForwarder()
        val result = forwarder.forwardDataToEndpoint("{}", "invalid-url")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Invalid target URL") == true)
        forwarder.close()
    }

    @Test
    fun testSuccessfulForwardingWithMockEngine() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"status":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val forwarder = KtorDataForwarder(customClient = mockClient)
        val result = forwarder.forwardDataToEndpoint("""{"test":"payload"}""", "http://localhost:8080/api")

        assertTrue(result.isSuccess)
    }

    @Test
    fun testHttpErrorReturnFailure() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error":"Internal Server Error"}""",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val mockClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val forwarder = KtorDataForwarder(customClient = mockClient)
        val result = forwarder.forwardDataToEndpoint("""{"test":"payload"}""", "http://localhost:8080/api")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("500") == true)
    }
}
