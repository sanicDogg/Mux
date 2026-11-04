package com.example.mux.data.forwarder

import com.example.mux.data.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Implementation of [DataForwarder] using Ktor HTTP Client.
 * Handles non-blocking execution, HTTP content negotiation with kotlinx.serialization JSON,
 * connection timeouts, and returning clean [Result] success/failure states.
 */
class KtorDataForwarder(
    private val settingsRepository: SettingsRepository? = null,
    private val customClient: HttpClient? = null
) : DataForwarder {

    val client: HttpClient by lazy {
        customClient ?: HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    override suspend fun forwardNotification(payload: String): Result<Unit> {
        val targetUrl = settingsRepository?.getServerUrl() ?: DEFAULT_FALLBACK_URL
        return forwardDataToEndpoint(payload, targetUrl)
    }

    override suspend fun forwardDeviceState(payload: String): Result<Unit> {
        val targetUrl = settingsRepository?.getDeviceStateUrl() ?: DEFAULT_DEVICE_STATE_URL
        return forwardDataToEndpoint(payload, targetUrl)
    }

    override suspend fun forwardDataToEndpoint(payload: String, endpointUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val url = endpointUrl.trim()
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid target URL: '$endpointUrl'. Must start with http:// or https://")
                )
            }

            try {
                val apiKey = settingsRepository?.getApiKey()
                val response: HttpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(payload)
                    if (!apiKey.isNullOrBlank()) {
                        header("X-Api-Key", apiKey)
                    }
                }

                if (response.status.isSuccess()) {
                    Result.success(Unit)
                } else {
                    Result.failure(
                        IllegalStateException("Server returned HTTP failure status code ${response.status.value}: ${response.status.description}")
                    )
                }
            } catch (e: io.ktor.client.plugins.HttpRequestTimeoutException) {
                Result.failure(Exception("HTTP request timed out connecting to $url", e))
            } catch (e: java.net.SocketTimeoutException) {
                Result.failure(Exception("Socket timed out connecting to $url", e))
            } catch (e: java.net.ConnectException) {
                Result.failure(Exception("Connection failed connecting to $url: ${e.message}", e))
            } catch (e: IOException) {
                Result.failure(Exception("Network I/O error forwarding payload to $url: ${e.message}", e))
            } catch (e: Throwable) {
                Result.failure(Exception("Failed to forward payload to $url: ${e.message}", e))
            }
        }

    fun close() {
        if (customClient == null) {
            client.close()
        }
    }

    companion object {
        const val DEFAULT_FALLBACK_URL = "http://192.168.1.100:8080/api/notifications"
        const val DEFAULT_DEVICE_STATE_URL = "http://192.168.1.100:8080/api/device-state"
    }
}
