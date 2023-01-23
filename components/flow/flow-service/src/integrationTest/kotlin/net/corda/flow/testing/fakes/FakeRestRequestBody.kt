package net.corda.flow.testing.fakes

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.marshalling.MarshallingService

class FakeRestRequestBody: RestRequestBody {
    override fun getRequestBody(): String {
        return ""
    }

    override fun <T> getRequestBodyAs(marshallingService: MarshallingService, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    override fun <T> getRequestBodyAsList(marshallingService: MarshallingService, clazz: Class<T>): List<T> {
        TODO("Not yet implemented")
    }
}