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

    @Suspendable
    override fun call() {
        val predictedTransactions = linkedMapOf<SecureHash, UtxoSignedTransaction>()
        while (true) {
            when (val request = session.receive<TransactionBackchainRequest>()) {
                is TransactionBackchainRequest.Get -> {
                    val transactions = request.transactionIds.map { id ->
                        predictedTransactions[id]?.also { predictedTransactions.remove(id) }
                            ?: utxoLedgerPersistenceService.find(id)
                            ?: throw CordaRuntimeException("Requested transaction does not exist locally")
                    }
                    // sending in batches of 1
                    // TODO Switch to [FlowMessaging.sendAll]
                    transactions.map { session.send(listOf(it)) }
                    transactions
                        .flatMap { it.dependencies }
                        .map {
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