package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.RPCRequestData

class FakeRPCRequestData: RPCRequestData {
    override fun getRequestBody(): String {
        return ""
    }
}