package net.corda.sandboxgroupcontext.test

import net.corda.v5.application.flows.FlowContextProperties
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import java.util.UUID

@Component(service = [FlowEngine::class, SingletonSerializeAsToken::class], scope = PROTOTYPE)
class FakeFlowEngineImpl : FlowEngine, SingletonSerializeAsToken {
    override val flowId: UUID
        get() = throw UnsupportedOperationException("VICTORY IS MINE!")
    override val virtualNodeName: MemberX500Name
        get() = throw UnsupportedOperationException("VICTORY IS MINE!")
    override val flowContextProperties: FlowContextProperties
        get() = throw UnsupportedOperationException("VICTORY IS MINE!")

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        throw UnsupportedOperationException("VICTORY IS MINE!")
    }
}
