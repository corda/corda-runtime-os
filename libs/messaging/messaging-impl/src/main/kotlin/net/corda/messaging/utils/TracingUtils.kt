package net.corda.messaging.utils

import net.corda.messaging.api.mediator.MediatorMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal object TracingUtils {

    private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    private const val TRACING_HEADER_NAME_TRACING_ID = "X-B3-TraceId"
    private const val TRACING_HEADER_NAME_SPAN_ID = "X-B3-SpanId"
    private const val TRACING_HEADER_NAME_SAMPLED = "X-B3-Sampled"
    private const val TRACING_HEADER_NAME_PARENT_SPAN_ID = "X-B3-ParentSpanId"

    fun <T : Any> extractTracingHeaders(message: MediatorMessage<T>): List<Pair<String, String>> {

        var extractedHeaders = mutableListOf<Pair<String, String>>()
        extractHeader(message, TRACING_HEADER_NAME_TRACING_ID, extractedHeaders)
        extractHeader(message, TRACING_HEADER_NAME_SPAN_ID, extractedHeaders)
        extractHeader(message, TRACING_HEADER_NAME_SAMPLED, extractedHeaders)
        extractHeader(message, TRACING_HEADER_NAME_PARENT_SPAN_ID, extractedHeaders)

        return extractedHeaders
    }

    private fun<T: Any> extractHeader(
        message: MediatorMessage<T>,
        headerName: String,
        headers: MutableList<Pair<String, String>>
    ) {
        val value = message.getPropertyOrNull<String>(headerName)
        if (value != null) {
            headers.add(headerName to value)
        } else {
            log.warn("Header not found: $headerName")
        }
    }
}
