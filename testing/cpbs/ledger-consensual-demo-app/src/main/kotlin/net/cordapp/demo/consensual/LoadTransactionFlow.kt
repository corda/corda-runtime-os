package net.cordapp.demo.consensual

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualSignedTransaction

data class FetchTransactionParameters(val transactionId: String)

data class TestConsensualStateResult(val testField: String, val participants: List<ByteArray>)

data class ConsensualTransactionResult(
    val id: SecureHash,
    val states: List<TestConsensualStateResult>,
    val participants: List<ByteArray>
)

// hacky DTO to enable JSON serialization without custom serializers
fun ConsensualDemoFlow.TestConsensualState.toResult(): TestConsensualStateResult {
    return TestConsensualStateResult(this.testField, this.participants.map { it.encoded })
}

// hacky DTO to enable JSON serialization without custom serializers
fun ConsensualSignedTransaction.toResult(): ConsensualTransactionResult {
    return this.toLedgerTransaction().let {
        ConsensualTransactionResult(
            it.id,
            it.states.map { (it as ConsensualDemoFlow.TestConsensualState).toResult() },
            it.requiredSignatories.map { it.encoded })
    }
}

data class FetchTransactionResponse(
    val transaction: ConsensualTransactionResult?,
    val errorMessage: String?
)

class LoadTransactionFlow : RPCStartableFlow {

    @CordaInject
    lateinit var ledgerService: ConsensualLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @Suspendable
    override fun call(requestBody: RPCRequestData): String {
        val txId =
            requestBody.getRequestBodyAs(marshallingService, FetchTransactionParameters::class.java).transactionId

        val result =
            ledgerService.fetchTransaction(SecureHash.parse(txId))
                ?.let { FetchTransactionResponse(it.toResult(), null) }
                ?: FetchTransactionResponse(null, "Failed to find transaction with id $txId.")

        return marshallingService.format(result)
    }
}