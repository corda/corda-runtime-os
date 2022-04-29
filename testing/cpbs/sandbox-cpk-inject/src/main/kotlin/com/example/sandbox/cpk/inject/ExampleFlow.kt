package com.example.sandbox.cpk.inject

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.base.annotations.Suspendable

@Suppress("unused")
@InitiatingFlow
@StartableByRPC
class ExampleFlow(private val data: String) : Flow<String> {

    @Suspendable
    override fun call(): String {
        return data
    }
}
