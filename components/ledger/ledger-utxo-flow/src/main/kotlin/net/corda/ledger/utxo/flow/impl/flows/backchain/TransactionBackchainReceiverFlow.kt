package net.corda.ledger.utxo.flow.impl.flows.backchain

import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.sandbox.CordaSystemFlow
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

@CordaSystemFlow
class TransactionBackchainReceiverFlow(
    private val initialTransactionIds: Set<SecureHash>,
    private val originalTransactionsToRetrieve: Set<SecureHash>,
    private val session: FlowSession
) : SubFlow<TopologicalSort> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
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
                "Backchain resolution of $initialTransactionIds - Requesting the content of transactions $batch from transaction backchain"
            }

            @Suppress("unchecked_cast")
            val retrievedTransactions = session.sendAndReceive(
                List::class.java,
                TransactionBackchainRequest.Get(batch)
            ) as List<UtxoSignedTransaction>

            log.trace { "Backchain resolution of $initialTransactionIds - Received content for transactions $batch" }

            for (retrievedTransaction in retrievedTransactions) {

                val retrievedTransactionId = retrievedTransaction.id

                require(retrievedTransactionId in batch) {
                    "Backchain resolution of $initialTransactionIds - Received transaction $retrievedTransactionId which was not " +
                            "requested in the last batch $batch"
                }

                val (status, _) = utxoLedgerPersistenceService.persistIfDoesNotExist(retrievedTransaction, UNVERIFIED)

                transactionsToRetrieve.remove(retrievedTransactionId)

                when (status) {
                    TransactionExistenceStatus.DOES_NOT_EXIST -> log.trace {
                        "Backchain resolution of $initialTransactionIds - Persisted transaction $retrievedTransactionId as " +
                                "unverified"
                    }
                    TransactionExistenceStatus.UNVERIFIED -> log.trace {
                        "Backchain resolution of $initialTransactionIds - Transaction $retrievedTransactionId already exists as " +
                                "unverified"
                    }
                    TransactionExistenceStatus.VERIFIED -> log.trace {
                        "Backchain resolution of $initialTransactionIds - Transaction $retrievedTransactionId already exists as " +
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
                    "Backchain resolution of $initialTransactionIds - Adding dependencies for transaction ${retrievedTransaction.id} " +
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

        if (initialTransactionIds != other.initialTransactionIds) return false
        if (originalTransactionsToRetrieve != other.originalTransactionsToRetrieve) return false
        if (session != other.session) return false

        return true
    }

    override fun hashCode(): Int {
        var result = initialTransactionIds.hashCode()
        result = 31 * result + originalTransactionsToRetrieve.hashCode()
        result = 31 * result + session.hashCode()
        return result
    }


}