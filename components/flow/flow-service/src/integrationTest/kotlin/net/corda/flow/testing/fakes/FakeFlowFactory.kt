package net.corda.flow.testing.fakes

import net.corda.data.flow.FlowStartContext
import net.corda.data.flow.event.StartFlow
import net.corda.flow.fiber.FlowLogicAndArgs
import net.corda.flow.pipeline.factory.FlowFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.messaging.FlowInfo
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.UntrustworthyData
import net.corda.v5.base.types.MemberX500Name
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@Suppress("Unused")
@ServiceRanking(Int.MAX_VALUE)
@Component(service = [FlowFactory::class, FakeFlowFactory::class])
class FakeFlowFactory: FlowFactory {
    override fun createFlow(startFlowEvent: StartFlow, sandboxGroupContext: SandboxGroupContext): FlowLogicAndArgs {
        return FlowLogicAndArgs.RPCStartedFlow(FakeFlow(), "")
    }

    override fun createInitiatedFlow(
        flowStartContext: FlowStartContext,
        sandboxGroupContext: SandboxGroupContext
    ): FlowLogicAndArgs {
        return FlowLogicAndArgs.InitiatedFlow(FakeInitiatedFlow(), FakeFlowSession())
    }

    private class FakeFlowSession: FlowSession {
        override val counterparty: MemberX500Name
            get() = TODO("Not yet implemented")

        override fun getCounterpartyFlowInfo(): FlowInfo {
            TODO("Not yet implemented")
        }

        override fun <R : Any> sendAndReceive(receiveType: Class<R>, payload: Any): UntrustworthyData<R> {
            TODO("Not yet implemented")
        }

        override fun <R : Any> receive(receiveType: Class<R>): UntrustworthyData<R> {
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