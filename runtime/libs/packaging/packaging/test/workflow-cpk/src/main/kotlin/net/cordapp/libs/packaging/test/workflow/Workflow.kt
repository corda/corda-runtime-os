package net.cordapp.libs.packaging.test.workflow

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow(protocol = "packaging-test")
class PackagingTestFlow : ClientStartableFlow {

    @Suspendable
    override fun call(requestBody: ClientRequestBody) : String {
        return ""
    }
}

@InitiatedBy(protocol = "packaging-test")
class PackagingTestFlowResponder(private val otherSide : FlowSession) : ResponderFlow {
    @Suspendable
    override fun call(session: FlowSession) {

    }
}
