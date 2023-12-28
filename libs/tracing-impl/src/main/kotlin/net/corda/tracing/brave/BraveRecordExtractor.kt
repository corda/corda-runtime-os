package net.corda.tracing.brave

import brave.Tracing
import brave.propagation.Propagation
import brave.propagation.TraceContextOrSamplingFlags

// This class is responsible for extracting a trace context based on a set of trace headers.
class BraveRecordExtractor(tracing: Tracing) {

    // Headers can be stored in any format. This instance contains the logic to extract the trace headers from a
    // list
    private val recordExtractorListFormat = tracing.propagation().extractor(
            Propagation.Getter<List<Pair<String, String>>, String> { headers, key ->
                headers.reversed().firstOrNull { it.first == key }?.second
            }
    )

    // Headers can be stored in any format. This instance contains the logic to extract the trace headers from a
    // mutable map
    private val recordExtractorMutableMapFormat = tracing.propagation().extractor(
        Propagation.Getter<MutableMap<String, Any>, String> { headers, key -> headers[key] as String? }
    )

    fun extract(headers: List<Pair<String, String>>): TraceContextOrSamplingFlags? {
        return recordExtractorListFormat.extract(headers)
    }

    fun extract(headers: MutableMap<String, Any>): TraceContextOrSamplingFlags? {
        return recordExtractorMutableMapFormat.extract(headers)
    }
}
