package net.cordapp.demo.obligation.workflow

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RPCRequestData
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import net.cordapp.demo.obligation.contract.ObligationState
import java.math.BigDecimal
import java.util.UUID

data class FindTransactionParameters(val transactionId: String)

data class ObligationStateResult(val amount: BigDecimal, val id: UUID, val participants: List<ByteArray>)

data class ObligationTransactionResult(
    val id: SecureHash,
    val states: List<ObligationStateResult>,
    val signatories: List<ByteArray>
)

// hacky DTO to enable JSON serialization without custom serializers
fun ObligationState.toResult(): ObligationStateResult {
    return ObligationStateResult(this.amount, this.id, this.participants.map { it.encoded })
}

// hacky DTO to enable JSON serialization without custom serializers
fun UtxoLedgerTransaction.toResult(): ObligationTransactionResult {
    return this.let {
        ObligationTransactionResult(
            it.id,
            it.outputContractStates.map { (it as ObligationState).toResult() },
            it.signatories.map { it.encoded })
    }
}

data class FindTransactionResponse(
    val transaction: ObligationTransactionResult?,
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
        log.info("Obligation find transaction flow starting...")
        val txId =
            requestBody.getRequestBodyAs(marshallingService, FindTransactionParameters::class.java).transactionId

        log.info("Utxo finding transaction $txId")
        val result = ledgerService.findLedgerTransaction(SecureHash.parse(txId))
            ?.let { FindTransactionResponse(it.toResult(), null) }
            ?: FindTransactionResponse(null, "Failed to find transaction with id $txId.")

        val resultString = marshallingService.format(result)

        log.info("Obligation finding transaction $txId's result: $resultString")
        return resultString
    }
}