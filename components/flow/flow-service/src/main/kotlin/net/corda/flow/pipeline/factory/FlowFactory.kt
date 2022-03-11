package net.corda.flow.pipeline.factory

import net.corda.data.flow.event.StartFlow
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowSession

interface FlowFactory {
    fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): Flow<*>
    fun createInitiatedFlow(sandboxGroupContext: SandboxGroupContext, flowClassName: String, flowSession: FlowSession): Flow<*>
}