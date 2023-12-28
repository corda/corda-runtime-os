package net.corda.tracing.brave

import brave.Tracing
import brave.propagation.TraceContext

class BraveRecordInjector(tracing: Tracing) {

    private val recordInjectorListFormat =
        tracing.propagation().injector { param: MutableList<Pair<String, String>>, key: String, value: String ->
            param.removeAll { it.first == key }
            param.add(key to value)
        }

    private val recordInjectorMutableMapFormat =
        tracing.propagation().injector { param: MutableMap<String, Any>, key: String, value: Any ->
            param[key] = value
        }

    fun inject(context: TraceContext, headers: List<Pair<String, String>>): List<Pair<String, String>> {
        val headersWithTracing = headers.toMutableList()

        recordInjectorListFormat.inject(context, headersWithTracing)
        return headersWithTracing
    }

    fun inject(context: TraceContext, headers: MutableMap<String, Any>): MutableMap<String, Any> {
        recordInjectorMutableMapFormat.inject(context, headers)
        return headers
    }
}
