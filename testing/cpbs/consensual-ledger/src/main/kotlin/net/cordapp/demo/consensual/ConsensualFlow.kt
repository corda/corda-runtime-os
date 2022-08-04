package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.ledger.consensual.ConsensualLedgerService

/**
 * Example consensual flow. Currently, does almost nothing other than verify that
 * we can inject the ledger service. Eventually it should do a two-party IOUState
 * agreement.
 */

class ConsensualFlow : RPCStartableFlow {
    data class InputMessage(val number: Int)
    data class ResultMessage(val text: String)

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var consensualLedgerService: ConsensualLedgerService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Consensual flow demo starting...")
        try {
            val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, InputMessage::class.java)
            log.info("Consensual state demo. Inputs: $inputs")
            log.info("flowEngine: $flowEngine")
            val resultMessage = ResultMessage(text = consensualLedgerService.getTransactionBuilder().toString())
            log.info("Success! Response: $resultMessage")
            return jsonMarshallingService.format(resultMessage)
        } catch (e: Exception) {
            log.warn("Failed to process consensual flow for request body '$requestBody' because:'${e.message}'")
            throw e
        }
    }
}
