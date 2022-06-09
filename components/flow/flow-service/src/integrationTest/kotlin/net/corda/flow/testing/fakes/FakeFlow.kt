package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

class FakeFlow: RPCStartableFlow {

    override fun call(requestBody: RPCRequestData): String {
        return ""
    }
}

class FakeInitiatedFlow: ResponderFlow {

    override fun call(session: FlowSession) {

    }
}