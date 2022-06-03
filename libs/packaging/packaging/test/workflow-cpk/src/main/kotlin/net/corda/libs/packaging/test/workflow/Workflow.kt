package net.corda.libs.packaging.test.workflow

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow(protocol = "packaging-test")
class PackagingTestFlow : RPCStartableFlow<Unit> {

    @Suspendable
    override fun call(requestBody: String) : String {
        return ""
    }
}

@InitiatedBy(protocol = "packaging-test")
class PackagingTestFlowResponder(private val otherSide : FlowSession) : ResponderFlow<Boolean> {
    @Suspendable
    override fun call(session: FlowSession) {

    }
}
