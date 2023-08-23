package net.corda.tracing.impl

import net.corda.tracing.TracingService

object TracingState {
    lateinit var currentTraceService: TracingService
}
