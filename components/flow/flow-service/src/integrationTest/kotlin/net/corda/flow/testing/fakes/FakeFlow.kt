package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession

class FakeFlow: RPCStartableFlow<String> {
    override fun call(): String {
        return ""
    }

    override fun call(requestBody: String): String {
        return ""
    }
}

class FakeInitiatedFlow: ResponderFlow<String> {
    override fun call(): String {
        return ""
    }

    override fun call(session: FlowSession) {

    }
}