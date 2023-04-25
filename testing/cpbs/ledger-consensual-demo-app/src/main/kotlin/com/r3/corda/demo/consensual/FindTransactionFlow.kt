package com.r3.corda.demo.consensual

import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.consensual.ConsensualLedgerService
import net.corda.v5.ledger.consensual.transaction.ConsensualLedgerTransaction
import com.r3.corda.demo.consensual.contract.TestConsensualState

data class FindTransactionParameters(val transactionId: String)

data class TestConsensualStateResult(val testField: String, val participants: List<ByteArray>)

data class ConsensualTransactionResult(
    val id: SecureHash,
    val states: List<TestConsensualStateResult>,
    val participants: List<ByteArray>
)

// hacky DTO to enable JSON serialization without custom serializers
fun TestConsensualState.toResult(): TestConsensualStateResult {
    return TestConsensualStateResult(this.testField, this.participants.map { it.encoded })
}

// hacky DTO to enable JSON serialization without custom serializers
fun ConsensualLedgerTransaction.toResult(): ConsensualTransactionResult {
    return this.let {
        ConsensualTransactionResult(
            it.id,
            it.states.map { (it as TestConsensualState).toResult() },
            it.requiredSignatories.map { it.encoded })
    }
}

data class FindTransactionResponse(
    val transaction: ConsensualTransactionResult?,
    val errorMessage: String?
)

class FindTransactionFlow : ClientStartableFlow {

    @CordaInject
    lateinit var ledgerService: ConsensualLedgerService

    @CordaInject
    lateinit var marshallingService: JsonMarshallingService

    @CordaInject
    lateinit var digestService: DigestService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val txId =
            requestBody.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java).transactionId

        val result = ledgerService.findLedgerTransaction(digestService.parseSecureHash(txId))
            ?.let { FindTransactionResponse(it.toResult(), null) }
            ?: FindTransactionResponse(null, "Failed to find transaction with id $txId.")

        return marshallingService.format(result)
    }
}