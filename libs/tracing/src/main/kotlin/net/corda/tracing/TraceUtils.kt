package net.corda.tracing

import org.slf4j.LoggerFactory
import org.slf4j.Logger

object TraceUtils {

    private const val TRACING_HEADER_NAME_TRACING_ID = "X-B3-TraceId"
    private const val TRACING_HEADER_NAME_SPAN_ID = "X-B3-SpanId"
    private const val TRACING_HEADER_NAME_SAMPLED = "X-B3-Sampled"
    private const val TRACING_HEADER_NAME_PARENT_SPAN_ID = "X-B3-ParentSpanId"

    fun extractHeaders(mapOfHeaders: Map<String, Any>): List<Pair<String, String>> {

        var extractedHeaders = mutableListOf<Pair<String, String>>()
        extractHeader(mapOfHeaders, TRACING_HEADER_NAME_TRACING_ID, extractedHeaders)
        extractHeader(mapOfHeaders, TRACING_HEADER_NAME_SPAN_ID, extractedHeaders)
        extractHeader(mapOfHeaders, TRACING_HEADER_NAME_SAMPLED, extractedHeaders)
        extractHeader(mapOfHeaders, TRACING_HEADER_NAME_PARENT_SPAN_ID, extractedHeaders)

        return extractedHeaders
    }

    private fun extractHeader(
        mapOfHeaders: Map<String, Any>,
        headerName: String,
        headers: MutableList<Pair<String, String>>
    ) {
        (mapOfHeaders[headerName] as String?)?.let {value ->
            headers.add(headerName to value)
        }
    }
}