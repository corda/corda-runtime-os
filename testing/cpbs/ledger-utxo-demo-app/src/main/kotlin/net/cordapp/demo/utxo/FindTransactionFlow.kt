package net.cordapp.demo.utxo

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

data class FindTransactionParameters(val transactionId: String)

data class TestUtxoStateResult(val testField: String, val participants: List<ByteArray>)

data class UtxoTransactionResult(
    val id: SecureHash,
    val states: List<TestUtxoStateResult>,
    val participants: List<ByteArray>
)

// hacky DTO to enable JSON serialization without custom serializers
fun UtxoDemoFlow.TestUtxoState.toResult(): TestUtxoStateResult {
    return TestUtxoStateResult(this.testField, this.participants.map { it.encoded })
}

// hacky DTO to enable JSON serialization without custom serializers
fun UtxoLedgerTransaction.toResult(): UtxoTransactionResult {
    return this.let {
        UtxoTransactionResult(
            it.id,
            it.outputContractStates.map { (it as UtxoDemoFlow.TestUtxoState).toResult() },
            it.signatories.map { it.encoded })
    }
}

data class FindTransactionResponse(
    val transaction: UtxoTransactionResult?,
    val errorMessage: String?
)

class FindTransactionFlow : RPCStartableFlow {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        log.info("Utxo find transaction flow starting...")
        val txId =
            requestBody.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java).transactionId

        log.info("Utxo finding transaction $txId")
        val result = ledgerService.findLedgerTransaction(SecureHash.parse(txId))
            ?.let { FindTransactionResponse(it.toResult(), null) }
            ?: FindTransactionResponse(null, "Failed to find transaction with id $txId.")

        val resultString = marshallingService.format(result)

        log.info("Utxo finding transaction $txId's result: $resultString")
        return resultString
    }
}