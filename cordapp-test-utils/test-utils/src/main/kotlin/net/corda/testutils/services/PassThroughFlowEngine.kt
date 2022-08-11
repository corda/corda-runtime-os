package net.corda.testutils.services

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration
import java.util.UUID

class PassThroughFlowEngine(private val member: MemberX500Name) : FlowEngine {
    override val flowId: UUID
        get() = TODO()
    override val virtualNodeName: MemberX500Name
        get() = member

    override fun sleep(duration: Duration) = TODO()

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        return subFlow.call()
    }

}
