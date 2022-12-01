package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.receive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class TransactionBackchainSenderFlow(private val session: FlowSession) : SubFlow<Unit> {

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    // splitting into two flows where the predicted transactions are returned from the flow would make testing easier
    @Suspendable
    override fun call() {
        // might want to send transaction ids from resolving flow that were in the dependencies but already verified so that they can be
        // removed from the predicted collection of transactions
        val predictedTransactions = linkedMapOf<SecureHash, UtxoSignedTransaction>()
        while (true) {
            when (val request = session.receive<TransactionBackchainRequest>()) {
                is TransactionBackchainRequest.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        predictedTransactions[id]?.also { predictedTransactions.remove(id) }
                            ?: utxoLedgerPersistenceService.find(id)
                            // should send a Payload.Failure back for the non-existent transaction
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    // sending in batches of 1
                    // TODO Switch to [FlowMessaging.sendAll]
                    transactions.map { session.send(listOf(it)) }
                    transactions
                        .flatMap { it.dependencies }
                        .map {
                            // should send a Payload.Failure back for the non-existent transaction
                            // a dependency not existing locally is a FATAL error, something seriously wrong has occurred.
                            utxoLedgerPersistenceService.find(it)
                                ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                        }
                        .associateBy(UtxoSignedTransaction::id)
                        .toMap(predictedTransactions)

                }
                is TransactionBackchainRequest.Stop -> return
            }
        }
    }
}