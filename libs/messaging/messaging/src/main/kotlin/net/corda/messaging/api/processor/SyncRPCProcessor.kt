package net.corda.messaging.api.processor

import net.corda.messaging.api.exception.CordaHTTPServerTransientException

/**
 * This interface defines a contract for processors that handle HTTP requests of type [REQUEST] from a synchronous HTTP subscription.
 *
 * The subscription executes the [process] function synchronously and returns a HTTP response to the caller containing the
 * serialized [RESPONSE] object.
 *
 * Implementations of [process] can throw a [CordaHTTPServerTransientException] when a transient error occurs during processing.
 * This will trigger the subscription to return a HTTP response with status `503`. The HTTP client is responsible for handling retries.
 *
 * Implementations of [process] must be idempotent to handle scenarios where the same request is attempted multiple times. Repeated
 * processing of the same request should produce the same result. For example, deduplication measures can be implemented in the [process]
 * function. This is not handled by the synchronous HTTP subscription.
 */
interface SyncRPCProcessor<REQUEST, RESPONSE> {

    /**
     * Process a [request] and return a response of type [RESPONSE].
     *
     * @param request a HTTP request of type [REQUEST].
     * @return the result of the processing of the type [RESPONSE].
     * @throws [CordaHTTPServerTransientException] if a transient error occurs during processing that can be retried by a HTTP client.
     */
    fun process(request: REQUEST) : RESPONSE?

    val requestClass: Class<REQUEST>
    val responseClass: Class<RESPONSE>
}
