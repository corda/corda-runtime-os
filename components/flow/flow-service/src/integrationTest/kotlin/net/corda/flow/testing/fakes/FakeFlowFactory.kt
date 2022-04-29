package net.corda.flow.testing.fakes

import net.corda.data.flow.event.StartFlow
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.messaging.FlowSession
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("Unused")
@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFactory::class, FakeFlowFactory::class])
class FakeFlowFactory: FlowFactory {
    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): Flow<*> {
        return FakeFlow()
    }

    override fun createInitiatedFlow(
        flowClassName: String,
        flowSession: FlowSession,
        sandboxGroupContext: SandboxGroupContext
    ): Flow<*> {
        return FakeFlow()
    }
}