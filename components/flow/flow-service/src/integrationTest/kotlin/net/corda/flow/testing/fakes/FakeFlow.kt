package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow

class FakeFlow: RPCStartableFlow<String> {
    override fun call(): String {
        return ""
    }

    override fun call(requestBody: String): String {
        return ""
    }
}

class FakeInitiatedFlow: Flow<String> {
    override fun call(): String {
        return ""
    }
}