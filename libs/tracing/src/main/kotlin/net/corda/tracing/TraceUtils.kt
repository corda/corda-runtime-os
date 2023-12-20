package net.corda.tracing

import net.corda.messaging.api.mediator.MediatorMessage

object TraceUtils {

    private const val TRACING_HEADER_NAME_TRACING_ID = "X-B3-TraceId" // see `brave.propagation.B3Propagation.TRACE_ID`
    private const val TRACING_HEADER_NAME_SPAN_ID = "X-B3-SpanId"
    private const val TRACING_HEADER_NAME_SAMPLED = "X-B3-Sampled"
    private const val TRACING_HEADER_NAME_PARENT_SPAN_ID = "X-B3-ParentSpanId"

    fun MediatorMessage<*>.extractTracingHeaders(): List<Pair<String, String>> {

        val immutableProperties: Map<String, Any> = properties

        val extractedHeaders = mutableListOf<Pair<String, String>>()
        extractHeader(immutableProperties, TRACING_HEADER_NAME_TRACING_ID, extractedHeaders)
        extractHeader(immutableProperties, TRACING_HEADER_NAME_SPAN_ID, extractedHeaders)
        extractHeader(immutableProperties, TRACING_HEADER_NAME_SAMPLED, extractedHeaders)
        extractHeader(immutableProperties, TRACING_HEADER_NAME_PARENT_SPAN_ID, extractedHeaders)

        return extractedHeaders
    }
    
    private fun extractHeader(
        mapOfHeaders: Map<String, Any>,
        headerName: String,
        headers: MutableList<Pair<String, String>>
    ) {
        (mapOfHeaders[headerName] as String?)?.let { value ->
            headers.add(headerName to value)
        }
    }
}