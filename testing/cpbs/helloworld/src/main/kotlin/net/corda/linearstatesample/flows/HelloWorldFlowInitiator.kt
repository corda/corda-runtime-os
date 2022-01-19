package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class HelloWorldFlowInitiator(private val jsonArg: String) : Flow<Boolean> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): Boolean {
        log.info("Hello world is starting...")
        try {
            var getNameFlow = GetNameFlow(jsonArg)
            val name = flowEngine.subFlow(getNameFlow)
            log.info("Hello ${name}!")
        } catch (e: Throwable) {
            log.warn(":( could not deserialize '$jsonArg' because:'${e.message}'")
        }
        log.info("Hello world completed.")
        return true
    }
}

