package net.cordapp.demo.utxo

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

data class PeekTransactionParameters(val transactionId: String)


data class PeekTransactionResponse(
    val inputs: List<TestUtxoStateResult>,
    val outputs: List<TestUtxoStateResult>,
    val errorMessage: String?
)

class PeekTransactionFlow : RPCStartableFlow {

    private companion object {
        val log = loggerFor<PeekTransactionFlow>()
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Utxo peek transaction flow starting...")
        val requestObject =
            requestBody.getRequestBodyAs(marshallingService, PeekTransactionParameters::class.java)

        val txId = requestObject.transactionId

        log.info("Utxo finding transaction $txId")
        val ledgerTransaction = ledgerService.findLedgerTransaction(SecureHash.parse(txId))


        val resultString = marshallingService.format(extractStates(ledgerTransaction))

        log.info("Utxo finding transaction $txId's result: $resultString")
        return resultString
    }

    @Suspendable
    private fun extractStates(ledgerTransaction: UtxoLedgerTransaction?): PeekTransactionResponse {
        if (ledgerTransaction == null ){
            return PeekTransactionResponse(emptyList(), emptyList(),
            "Failed to load transaction.")
        }
        val inputStates =
            try {
                ledgerTransaction.getInputStateAndRefs(UtxoDemoFlow.TestUtxoState::class.java)
                    .map { it.state.contractState.toResult() }
            } catch (e: Exception){
                return PeekTransactionResponse(inputs = emptyList(), outputs = emptyList(),
                    "Failed to load inputs: ${e.message}" )
            }

        val outputStates =
            try {
                ledgerTransaction.getOutputStateAndRefs(UtxoDemoFlow.TestUtxoState::class.java)
                    .map { it.state.contractState.toResult() }
            } catch (e: Exception){
                return PeekTransactionResponse(inputs = emptyList(), outputs = emptyList(),
                    "Failed to load outputs: ${e.message}" )
            }

        return PeekTransactionResponse(inputStates, outputStates, null)
    }
}