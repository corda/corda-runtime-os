package net.corda.flow.testing.fakes

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.fiber.ClientStartedFlow
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.fiber.InitiatedFlow
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("Unused")
@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFactory::class, FakeFlowFactory::class])
class FakeFlowFactory : FlowFactory {

    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        return ClientStartedFlow(FakeFlow(), FakeClientRequestBody())
    }

    override fun createInitiatedFlow(
        flowStartContext: FlowStartContext,
        requireClose: Boolean,
        sandboxGroupContext: SandboxGroupContext,
        contextProperties: Map<String, String>
    ): FlowLogicAndArgs {
        return InitiatedFlow(FakeInitiatedFlow(), FakeFlowSession())
    }

    private class FakeFlowSession : FlowSession {
        override fun getCounterparty(): MemberX500Name
            = TODO("Not yet implemented")

        override fun getCounterpartyFlowInfo(): FlowInfo {
            TODO("Not yet implemented")
        }

        override fun getContextProperties(): FlowContextProperties
            = TODO("Not yet implemented")

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
