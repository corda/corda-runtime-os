package net.corda.flow.testing.fakes

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.fiber.RestStartedFlow
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("Unused")
@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFactory::class, FakeFlowFactory::class])
class FakeFlowFactory : FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        return RestStartedFlow(FakeFlow(), FakeRestRequestBody())
    }

    override fun createInitiatedFlow(
        flowStartContext: FlowStartContext,
        sandboxGroupContext: SandboxGroupContext,
        contextProperties: Map<String, String>
    ): FlowLogicAndArgs {
        return InitiatedFlow(FakeInitiatedFlow(), FakeFlowSession())
    }

    private class FakeFlowSession : FlowSession {
        override val counterparty: MemberX500Name
            get() = TODO("Not yet implemented")

        override val contextProperties: FlowContextProperties
            get() = TODO("Not yet implemented")

        override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): R {
            TODO("Not yet implemented")
        }

        override fun <R : Any> receive(receiveType: Class<R>): R {
            TODO("Not yet implemented")
        }

        override fun send(payload: Any) {
            TODO("Not yet implemented")
        }

        override fun close() {
            TODO("Not yet implemented")
        }
    }
}
