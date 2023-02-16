package net.corda.sandboxgroupcontext.test

import net.corda.sandbox.type.UsedByFlow
import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.util.UUID

@Component(service = [FlowEngine::class, UsedByFlow::class], scope = PROTOTYPE)
class FakeFlowEngineImpl : FlowEngine, UsedByFlow, SingletonSerializeAsToken {
    override fun getFlowId(): UUID
        = throw UnsupportedOperationException("VICTORY IS MINE!")
    override fun getVirtualNodeName(): MemberX500Name
        = throw UnsupportedOperationException("VICTORY IS MINE!")
    override fun getFlowContextProperties(): FlowContextProperties
        = throw UnsupportedOperationException("VICTORY IS MINE!")

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        throw UnsupportedOperationException("VICTORY IS MINE!")
    }
}
