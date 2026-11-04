package com.example.mux.data.forwarder

/**
 * Interface for components responsible for forwarding serialized JSON payload strings
 * to remote network endpoints or servers.
 */
interface DataForwarder {
    /**
     * Forwards a raw JSON payload to the configured notification endpoint.
     *
     * @param payload The raw JSON string payload to send.
     * @return [Result.success] if HTTP response status is 2xx, or [Result.failure] with error detail.
     */
    suspend fun forwardNotification(payload: String): Result<Unit>

    /**
     * Forwards a raw JSON payload to a specific target endpoint URL.
     *
     * @param payload The raw JSON string payload to send.
     * @param endpointUrl The specific HTTP/HTTPS URL destination.
     * @return [Result.success] if HTTP response status is 2xx, or [Result.failure] with error detail.
     */
    suspend fun forwardDataToEndpoint(payload: String, endpointUrl: String): Result<Unit>

    /**
     * Forwards a raw JSON payload to the configured device-state channel endpoint.
     *
     * @param payload The raw JSON string payload to send.
     * @return [Result.success] if HTTP response status is 2xx, or [Result.failure] with error detail.
     */
    suspend fun forwardDeviceState(payload: String): Result<Unit>
}
