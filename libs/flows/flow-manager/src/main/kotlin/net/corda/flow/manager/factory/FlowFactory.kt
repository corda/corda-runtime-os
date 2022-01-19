package net.corda.flow.manager.factory

import net.corda.data.flow.event.StartRPCFlow
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow

interface FlowFactory {
    fun createFlow(startFlowEvent: StartRPCFlow, sandboxGroupContext: SandboxGroupContext): Flow<*>
}