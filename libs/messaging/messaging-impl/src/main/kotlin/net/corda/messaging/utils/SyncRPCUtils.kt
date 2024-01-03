package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.rest.ResponseCode
import net.corda.web.api.Endpoint
import net.corda.web.api.WebContext
import org.slf4j.Logger

fun handleProcessorException(
    log: Logger,
    endpoint: Endpoint,
    ex: Exception,
    context: WebContext
): WebContext {
    when (ex) {
        is CordaHTTPServerTransientException -> {
            "Transient error processing RPC request for $endpoint: ${ex.message}".also { msg ->
                log.warn(msg, ex)
                context.result(msg)
            }
            context.status(ResponseCode.SERVICE_UNAVAILABLE)
        }

        else -> {
            "Failed to process RPC request for $endpoint".also { message ->
                log.warn(message, ex)
                context.result(message)
            }
            context.status(ResponseCode.INTERNAL_SERVER_ERROR)
        }
    }
    return context
}