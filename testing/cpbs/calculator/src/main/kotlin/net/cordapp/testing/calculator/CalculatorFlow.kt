package net.cordapp.testing.calculator

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger

class CalculatorFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Calculator starting...")
        var resultMessage = ""
        try {
            val inputs = requestBody.getRequestBodyAs<InputMessage>(jsonMarshallingService)
            val result = (inputs.a ?: 0) + (inputs.b ?: 0)
            log.info("Calculated result ${inputs.a} + ${inputs.b} = ${result}, formatting for response...")
            val outputFormatter = OutputFormattingFlow(result)
            resultMessage = flowEngine.subFlow(outputFormatter)
            log.info("Calculated response:  $resultMessage")
        } catch (e: Exception) {
            log.warn(":( could not complete calculation of '$requestBody' because:'${e.message}'")
        }
        log.info("Calculation completed.")

        return resultMessage
    }
}
