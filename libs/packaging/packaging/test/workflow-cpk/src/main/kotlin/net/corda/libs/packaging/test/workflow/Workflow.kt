package net.corda.libs.packaging.test.workflow

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow(protocol = "packaging-test")
@RPCStartableFlow
class PackagingTestFlow : Flow<Unit> {

    @Suspendable
    override fun call() {}
}

@InitiatedBy(protocol = "packaging-test")
class PackagingTestFlowResponder(private val otherSide : FlowSession) : Flow<Boolean> {
    @Suspendable
    override fun call(): Boolean {
        return true
    }
}
