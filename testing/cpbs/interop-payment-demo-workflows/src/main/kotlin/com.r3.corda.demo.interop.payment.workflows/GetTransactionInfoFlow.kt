package com.r3.corda.demo.interop.payment.workflows

import com.r3.corda.demo.interop.payment.states.DehydratedPaymentState
import com.r3.corda.demo.interop.payment.states.PaymentState
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction
import org.slf4j.LoggerFactory


private class GetTransactionInfoFlowParameters(
    val transactionId: String
)

private class TransactionInfo(
    val inputStates: List<DehydratedPaymentState>,
    val outputStates: List<DehydratedPaymentState>
)

private class GetTransactionInfoFlowResult(
    val error: String?,
    val transactionInfo: TransactionInfo?
)

class GetTransactionInfoFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var digestService: DigestService


    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        log.info("GetTransactionInfo.call() called")

        val transactionId = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            GetTransactionInfoFlowParameters::class.java
        ).transactionId

        val transaction = ledgerService.findLedgerTransaction(digestService.parseSecureHash(transactionId))

        if (transaction == null) {
            val responseObject = GetTransactionInfoFlowResult(
                error = "Failed to find transaction with id: $transactionId",
                transactionInfo = null
            )
            return jsonMarshallingService.format(responseObject)
        }

        return jsonMarshallingService.format(getTransactionInfo(transaction))
    }

    @Suspendable
    private fun getTransactionInfo(transaction: UtxoLedgerTransaction): GetTransactionInfoFlowResult {
        val inputStates = try {
            transaction.getInputStateAndRefs(PaymentState::class.java).map { it.state.contractState.dehydrate() }
        } catch (e: Exception) {
            return GetTransactionInfoFlowResult(
                error = "Failed to get transaction input states: ${e.message}",
                transactionInfo = null
            )
        }

        val outputStates = try {
            transaction.getOutputStateAndRefs(PaymentState::class.java).map { it.state.contractState.dehydrate() }
        } catch (e: Exception) {
            return GetTransactionInfoFlowResult(
                error = "Failed to get transaction output states: ${e.message}",
                transactionInfo = null
            )
        }

        return GetTransactionInfoFlowResult(
            error = null,
            transactionInfo = TransactionInfo(inputStates, outputStates)
        )
    }
}
