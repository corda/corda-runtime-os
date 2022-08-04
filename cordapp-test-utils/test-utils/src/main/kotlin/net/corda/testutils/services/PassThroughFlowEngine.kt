package net.corda.testutils.services

import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.types.MemberX500Name
import java.time.Duration
import java.util.*

class PassThroughFlowEngine(private val x500: MemberX500Name) : FlowEngine {
    override val flowId: UUID
        get() = TODO()
    override val virtualNodeName: MemberX500Name
        get() = x500

    override fun sleep(duration: Duration) = TODO()

    override fun <R> subFlow(subFlow: SubFlow<R>): R {
        return subFlow.call()
    }

}
