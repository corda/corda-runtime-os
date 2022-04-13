package net.corda.testing.animals

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@Suppress("UNUSED")
@InitiatingFlow
@StartableByRPC
class AnimalFlow(private val jsonArg: String) : Flow<String> {
    /** Someone sends us a json payload */
    class InputMessage {
        var name : String? = null
        var type : String? = null
    }

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Animal flow started")
        var resultMessage = ""
        try {
            val inputs = jsonMarshallingService.parseJson<InputMessage>(jsonArg)
            val result = "A ${inputs.type} called ${inputs.name}"
            val outputFormatter = OutputFormattingFlow(result)
            resultMessage = flowEngine.subFlow(outputFormatter)
            log.info("Animal flow response:  $resultMessage")
        } catch (e: Exception) {
            log.warn(":( could not complete animal flow of '$jsonArg' because:'${e.message}'")
        }
        log.info("Animal flow finished")

        return resultMessage
    }
}
