package net.cordapp.testing.smoketests.flow.errors

import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.Suspendable

@Suppress("unused")
class PrivateConstructorFlow private constructor() : ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        throw IllegalStateException("Should not reach this point")
    }
}

@Suppress("unused")
class NoDefaultConstructorFlow(private val message: String) : ClientStartableFlow {
    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        throw IllegalStateException(message)
    }
}
