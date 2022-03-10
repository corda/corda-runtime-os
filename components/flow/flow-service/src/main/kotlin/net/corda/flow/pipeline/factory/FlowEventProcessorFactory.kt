package net.corda.flow.pipeline.factory

import net.corda.flow.pipeline.FlowEventProcessor

interface FlowEventProcessorFactory {
    fun create(): FlowEventProcessor
}

