package com.r3.corda.demo.utxo

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import com.r3.corda.demo.utxo.contract.TestUtxoState
import org.slf4j.LoggerFactory

data class FindTransactionParameters(val transactionId: String)

data class TestUtxoStateResult(val testField: String, val participants: List<ByteArray>)

data class UtxoTransactionResult(
    val id: SecureHash,
    val states: List<TestUtxoStateResult>,
    val participants: List<ByteArray>
)

// hacky DTO to enable JSON serialization without custom serializers
fun TestUtxoState.toResult(): TestUtxoStateResult {
    return TestUtxoStateResult(this.testField, this.participants.map { it.encoded })
}

// hacky DTO to enable JSON serialization without custom serializers
fun UtxoLedgerTransaction.toResult(): UtxoTransactionResult {
    return this.let {
        UtxoTransactionResult(
            it.id,
            it.outputContractStates.map { (it as TestUtxoState).toResult() },
            it.signatories.map { it.encoded })
    }
}

data class FindTransactionResponse(
    val transaction: UtxoTransactionResult?,
    val errorMessage: String?
)

class FindTransactionFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("Utxo find transaction flow starting...")
        val txId =
            requestBody.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java).transactionId

        log.info("Utxo finding transaction $txId")
        val result = ledgerService.findLedgerTransaction(digestService.parseSecureHash(txId))
            ?.let { FindTransactionResponse(it.toResult(), null) }
            ?: FindTransactionResponse(null, "Failed to find transaction with id $txId.")

        val resultString = marshallingService.format(result)

        log.info("Utxo finding transaction $txId's result: $resultString")
        return resultString
    }
}