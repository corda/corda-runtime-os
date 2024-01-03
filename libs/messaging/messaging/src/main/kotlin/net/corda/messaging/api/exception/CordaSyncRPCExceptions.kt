package net.corda.messaging.api.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * A transient exception originating from the server-side of a sync RPC processor.
 *
 * Indicates to the message pattern the error is transient and can be retried.
 *
 * @param requestId the identifier for the request.
 * @param cause the cause of the transient exception.
 */
class CordaTransientServerException(val requestId: String, cause: Exception? = null) :
    CordaRuntimeException("Transient server exception while processing request '$requestId'. Cause: ${cause?.message}", cause)