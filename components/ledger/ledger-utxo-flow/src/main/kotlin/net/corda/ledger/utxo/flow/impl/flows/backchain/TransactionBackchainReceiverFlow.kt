package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.flow.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.messaging.sendAndReceive
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.loggerFor
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction

@CordaSystemFlow
class TransactionBackchainReceiverFlow(
    private val resolvingTransactionId: SecureHash,
    private val originalTransactionsToRetrieve: Set<SecureHash>,
    private val session: FlowSession
) : SubFlow<TopologicalSort> {

    private companion object {
        val log = loggerFor<TransactionBackchainReceiverFlow>()
    }

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @Suspendable
    override fun call(): TopologicalSort {
        // Using a [Set] here ensures that if two or more transactions at the same level in the graph are dependent on the same transaction
        // then when the second and subsequent transactions see this dependency to add it to [transactionsToRetrieve] it will only exist
        // once and be retrieved once due to the properties of a [Set].
        val transactionsToRetrieve = LinkedHashSet(originalTransactionsToRetrieve)

        val sortedTransactionIds = TopologicalSort()

        while (transactionsToRetrieve.isNotEmpty()) {
            // For now, we'll assume a batch size of 1
            val batch = setOf(transactionsToRetrieve.first())

            log.trace {
                "Backchain resolution of $resolvingTransactionId - Requesting the content of transactions $batch from transaction backchain"
            }

            val retrievedTransactions = session.sendAndReceive<List<UtxoSignedTransaction>>(
                TransactionBackchainRequest.Get(batch)
            )

            log.trace { "Backchain resolution of $resolvingTransactionId - Received content for transactions $batch" }

            for (retrievedTransaction in retrievedTransactions) {

                val retrievedTransactionId = retrievedTransaction.id

                require(retrievedTransactionId in batch) {
                    "Backchain resolution of $resolvingTransactionId - Received transaction $retrievedTransactionId which was not " +
                            "requested in the last batch $batch"
                }

                val (status, _) = utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction, UNVERIFIED)

                transactionsToRetrieve.remove(retrievedTransactionId)

                when (status) {
                    TransactionExistenceStatus.DOES_NOT_EXIST -> log.trace {
                        "Backchain resolution of $resolvingTransactionId - Persisted transaction $retrievedTransactionId as " +
                                "unverified"
                    }
                    TransactionExistenceStatus.UNVERIFIED -> log.trace {
                        "Backchain resolution of $resolvingTransactionId - Transaction $retrievedTransactionId already exists as " +
                                "unverified"
                    }
                    TransactionExistenceStatus.VERIFIED -> log.trace {
                        "Backchain resolution of $resolvingTransactionId - Transaction $retrievedTransactionId already exists as " +
                                "verified"
                    }
                }

                if (status != TransactionExistenceStatus.VERIFIED) {
                    addUnseenDependenciesToRetrieve(retrievedTransaction, sortedTransactionIds, transactionsToRetrieve)
                }
            }
        }

        if (sortedTransactionIds.isNotEmpty()) {
            session.send(TransactionBackchainRequest.Stop)
        }

        return sortedTransactionIds
    }

    private fun addUnseenDependenciesToRetrieve(
        retrievedTransaction: UtxoSignedTransaction,
        sortedTransactionIds: TopologicalSort,
        transactionsToRetrieve: LinkedHashSet<SecureHash>
    ) {
        if (retrievedTransaction.id !in sortedTransactionIds.transactionIds) {
            retrievedTransaction.dependencies.let { dependencies ->
                val unseenDependencies = dependencies - sortedTransactionIds.transactionIds
                log.trace {
                    "Backchain resolution of $resolvingTransactionId - Adding dependencies for transaction ${retrievedTransaction.id} " +
                            "dependencies: $unseenDependencies to transactions to retrieve"
                }
                sortedTransactionIds.add(retrievedTransaction.id, unseenDependencies)
                transactionsToRetrieve.addAll(unseenDependencies)
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainReceiverFlow

        if (resolvingTransactionId != other.resolvingTransactionId) return false
        if (originalTransactionsToRetrieve != other.originalTransactionsToRetrieve) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = resolvingTransactionId.hashCode()
        result = 31 * result + originalTransactionsToRetrieve.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }


}