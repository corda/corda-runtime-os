package net.cordapp.testing.smoketests.flow.errors

import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.base.annotations.Suspendable

@Suppress("unused")
class PrivateConstructorFlow private constructor() : RPCStartableFlow {
    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        throw IllegalStateException("Should not reach this point")
    }
}

@Suppress("unused")
class NoDefaultConstructorFlow(private val message: String) : RPCStartableFlow {
    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        throw IllegalStateException(message)
    }
}
