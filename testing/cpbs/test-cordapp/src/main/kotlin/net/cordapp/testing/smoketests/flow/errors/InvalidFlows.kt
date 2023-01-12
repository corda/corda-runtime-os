package net.cordapp.testing.smoketests.flow.errors

import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.RestStartableFlow
import net.corda.v5.base.annotations.Suspendable

@Suppress("unused")
class PrivateConstructorFlow private constructor() : RestStartableFlow {
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        throw IllegalStateException("Should not reach this point")
    }
}

@Suppress("unused")
class NoDefaultConstructorFlow(private val message: String) : RestStartableFlow {
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        throw IllegalStateException(message)
    }
}
