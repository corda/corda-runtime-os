package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.serialization.JsonMarshallingService

class FakeRPCRequestData: RPCRequestData {
    override fun getRequestBody(): String {
        return ""
    }

    override fun <T> getRequestBodyAs(jsonMarshallingService: JsonMarshallingService, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }
}