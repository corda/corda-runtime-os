package net.corda.flow.manager.factory

import net.corda.flow.manager.FlowEventProcessor

interface FlowEventProcessorFactory {
    fun create(): FlowEventProcessor
}