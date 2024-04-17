package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Exception representing a 4XX response from the HTTP server
 */
class CordaHTTPClientErrorException(val statusCode: Int, message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * Exception representing a 5XX response from the HTTP server
 */
class CordaHTTPServerErrorException(val statusCode: Int, message: String?, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)

/**
 * An exception that represents a transient error occurring on the HTTP server. The client can decide whether to retry the request.
 *
 * This exception can be thrown by a HTTP server in a recoverable error condition and trigger client retry logic.
 *
 * @param requestId the identifier for the request.
 * @param cause the cause of the transient exception.
 */
class CordaHTTPServerTransientException(val requestId: String, cause: Throwable? = null) :
    CordaRuntimeException("Transient server exception while processing request '$requestId'. Cause: ${cause?.message}", cause)

/**
 * Exception thrown within the RPC client when a transient error escapes the client retry logic.
 *
 * In this case processing may be retried from within the mediator code.
 */
class CordaHTTPClientSideTransientException(val statusCode: Int, message: String, exception: Throwable? = null) :
    CordaRuntimeException(message, exception)