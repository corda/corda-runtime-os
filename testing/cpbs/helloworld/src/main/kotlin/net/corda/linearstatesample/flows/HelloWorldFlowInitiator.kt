package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.base.annotations.Suspendable

@InitiatingFlow
@StartableByRPC
class HelloWorldFlowInitiator(private val jsonArg: String) : Flow<Boolean> {
    @Suspendable
    override fun call() : Boolean {
        println("Hello World! Arg: $jsonArg")
        return true
    }
}
