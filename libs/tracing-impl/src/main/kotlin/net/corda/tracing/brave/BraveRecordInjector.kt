package net.corda.tracing.brave

import brave.Tracing
import brave.propagation.TraceContext

// This class is responsible for adding the trace headers based on an existing trace context.
class BraveRecordInjector(tracing: Tracing) {

    // Headers can be stored in any format. This instance contains the logic to insert the trace headers to a
    // mutable list
    private val recordInjectorListFormat =
        tracing.propagation().injector { param: MutableList<Pair<String, String>>, key: String, value: String ->
            param.removeAll { it.first == key }
            param.add(key to value)
        }

    // Headers can be stored in any format. This instance contains the logic to insert the trace headers to a
    // mutable map
    private val recordInjectorMutableMapFormat =
        tracing.propagation().injector { param: MutableMap<String, Any>, key: String, value: Any ->
            param[key] = value
        }

    fun inject(context: TraceContext, headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val headersWithTracing = headers.toMutableList()

        recordInjectorListFormat.inject(context, headersWithTracing)
        return headersWithTracing
    }

    fun inject(context: TraceContext, headers: Map<String, Any>): Map<String, Any> {
        val headersWithTracing = headers.toMutableMap()

        recordInjectorMutableMapFormat.inject(context, headersWithTracing)
        return headersWithTracing
    }
}
