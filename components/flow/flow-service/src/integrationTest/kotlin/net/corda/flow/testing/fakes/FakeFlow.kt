package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

@InitiatingFlow("protocol")
class FakeFlow: RPCStartableFlow {

    override fun call(requestBody: RPCRequestData): String {
        return ""
    }
}

@InitiatedBy("protocol")
class FakeInitiatedFlow: ResponderFlow {

    override fun call(session: FlowSession) {

    }
}