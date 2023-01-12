package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

@InitiatingFlow("protocol")
class FakeFlow: RestStartableFlow {

    override fun call(requestBody: RestRequestBody): String {
        return ""
    }
}

@InitiatedBy("protocol")
class FakeInitiatedFlow: ResponderFlow {

    override fun call(session: FlowSession) {

    }
}