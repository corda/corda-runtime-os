package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.MarshallingService

class FakeClientRequestBody: ClientRequestBody {
    override fun getRequestBody(): String {
        return ""
    }

    override fun <T : Any> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
        TODO("Not yet implemented")
    }
}