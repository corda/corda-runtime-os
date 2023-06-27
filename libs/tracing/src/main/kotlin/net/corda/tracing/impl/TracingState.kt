package net.corda.tracing.impl

import net.corda.tracing.TracingService

object TracingState {
    var currentTraceService: TracingService = NoopTracingService()
}
