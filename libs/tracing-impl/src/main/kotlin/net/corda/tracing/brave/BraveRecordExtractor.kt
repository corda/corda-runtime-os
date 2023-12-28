package net.corda.tracing.brave

import brave.Tracing
import brave.propagation.Propagation
import brave.propagation.TraceContextOrSamplingFlags

class BraveRecordExtractor(tracing: Tracing) {

    private val recordExtractorListFormat = tracing.propagation().extractor(
            Propagation.Getter<List<Pair<String, String>>, String> { headers, key ->
                headers.reversed().firstOrNull { it.first == key }?.second
            }
    )

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
