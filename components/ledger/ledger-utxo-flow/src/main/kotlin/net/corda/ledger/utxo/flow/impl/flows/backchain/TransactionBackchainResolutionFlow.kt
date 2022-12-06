package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.flow.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.trace
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

// need to send stop even if the resolving node doesn't need a backchain since the peer session doesn't know this
// they'll just be waiting to receive the next sessions
// can be slightly optimised to only wait to do backchain resolution if the transaction has dependencies, but can't do more than that.
// If the sessions are made initiating flows then the peer session doesn't need to do any sort of waiting.
@CordaSystemFlow
class TransactionBackchainResolutionFlow(private val transaction: UtxoSignedTransaction, private val session: FlowSession) : SubFlow<Unit> {

    private companion object {
        val log = loggerFor<TransactionBackchainResolutionFlow>()
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var transactionBackchainVerifier: TransactionBackchainVerifier

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call() {
        val dependencies = transaction.dependencies
        // Needs to be replaced with an optimised/specialised query method
        // Assuming [find] requires transactions to be verified.
        val alreadyVerifiedTransactions = dependencies.filter { utxoLedgerPersistenceService.find(it, VERIFIED) != null }.toSet()
        val originalTransactionsToRetrieve = dependencies - alreadyVerifiedTransactions
        if (originalTransactionsToRetrieve.isNotEmpty()) {
            log.debug {
                "Backchain resolution of ${transaction.id} - Transaction needs to resolve its dependencies of " +
                        "$originalTransactionsToRetrieve in its backchain, starting transaction backchain resolution"
            }
            val topologicalSort = flowEngine.subFlow(
                TransactionBackchainReceiverFlow(
                    resolvingTransactionId = transaction.id,
                    originalTransactionsToRetrieve,
                    session
                )
            )
            log.debug {
                "Backchain resolution of ${transaction.id} - Retrieved dependencies of $originalTransactionsToRetrieve from its " +
                        "backchain, beginning verification before storing the transactions locally"
            }
            try {
                transactionBackchainVerifier.verify(transaction.id, topologicalSort)
                log.debug {
                    "Backchain resolution of ${transaction.id} - Completed, resolved ${topologicalSort.size} transactions"
                }
            } catch (e: Exception) {
                log.warn("Backchain resolution of ${transaction.id} - Failed due to a transaction in its backchain failing verification.")
                throw e
            }
        } else {
            if (dependencies.isEmpty()) {
                log.trace {
                    "Backchain resolution of ${transaction.id} - Has no dependencies in its backchain, skipping transaction backchain " +
                            "resolution"
                }
            } else {
                log.trace {
                    "Backchain resolution of ${transaction.id} - Has dependencies $dependencies which have already been verified " +
                            "locally, skipping transaction backchain resolution"
                }
            }
            session.send(TransactionBackchainRequest.Stop)
        }
    }
}