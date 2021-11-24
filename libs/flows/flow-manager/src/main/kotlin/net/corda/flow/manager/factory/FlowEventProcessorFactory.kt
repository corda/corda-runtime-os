package net.corda.flow.manager.factory

import net.corda.flow.manager.FlowEventProcessor
import net.corda.flow.manager.FlowSandboxService
import net.corda.virtualnode.sandboxgroup.SandboxGroupContext

interface FlowEventProcessorFactory {
    fun create(): FlowEventProcessor
}