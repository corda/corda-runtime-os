package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.application.services.json.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

@InitiatingFlow
@StartableByRPC
class CalculatorFlow(private val jsonArg: String) : Flow<String> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Calculator starting...")
        var resultMessage = ""
        try {
            val inputs = jsonMarshallingService.parseJson<InputMessage>(jsonArg)
            val result = (inputs.a ?: 0) + (inputs.b ?: 0)
            log.info("Calculated result ${inputs.a} + ${inputs.b} = ${result}, formatting for response...")
            var outputFormatter = OutputFormattingFlow(result)
            resultMessage = flowEngine.subFlow(outputFormatter)
            log.info("Calculated response:  ${resultMessage}")
        } catch (e: Throwable) {
            log.warn(":( could not complete calculation of '$jsonArg' because:'${e.message}'")
        }
        log.info("Calculation completed.")

        return resultMessage
    }
}

