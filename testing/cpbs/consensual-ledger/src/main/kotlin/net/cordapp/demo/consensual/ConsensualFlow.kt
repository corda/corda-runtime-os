package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.StartableByRPC
import net.corda.v5.application.serialization.JsonMarshallingService
import net.corda.v5.application.serialization.parseJson
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.models.ConsensualStatesLedger

/**
 * Example consensual flow. Currently does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */
@StartableByRPC
class ConsensualFlow(private val jsonArg: String) : Flow<String> {
    data class InputMessage(val number: Int)
    data class ResultMessage(val number: Int)

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var consensualStatesLedger: ConsensualStatesLedger

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): String {
        log.info("Consensual flow demo starting...")
        try {
            val inputs = jsonMarshallingService.parseJson<InputMessage>(jsonArg)
            log.info("Consensual state demo. Inputs: $inputs")
            log.info("flowEngine: $flowEngine")
            // val resultMessage = flowEngine.subFlow(inputs)
            val resultMessage = ResultMessage(number = consensualStatesLedger.double(inputs.number))
            log.info("Success! Response: $resultMessage")
            return jsonMarshallingService.formatJson(resultMessage)
        } catch (e: Exception) {
            log.warn("Failed to process consensual flow for inputs '$jsonArg' because:'${e.message}'")
            throw e
        }
    }
}
