package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

@InitiatingFlow(protocol = "protocol")
class FakeFlow: ClientStartableFlow {

    override fun call(requestBody: RestRequestBody): String {
        return ""
    }
}

@InitiatedBy(protocol = "protocol")
class FakeInitiatedFlow: ResponderFlow {

    override fun call(session: FlowSession) {

    }
}