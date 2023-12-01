package net.corda.ledger.utxo.flow.impl.flows.backchain.v1

import net.corda.flow.application.services.FlowConfigService
import net.corda.crypto.core.parseSecureHash
import net.corda.ledger.common.data.transaction.TransactionMetadataInternal
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.TransactionStatus.INVALID
import net.corda.ledger.common.data.transaction.TransactionStatus.UNVERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.VERIFIED
import net.corda.ledger.common.data.transaction.TransactionStatus.DRAFT
import net.corda.ledger.utxo.flow.impl.UtxoLedgerMetricRecorder
import net.corda.ledger.utxo.flow.impl.flows.backchain.InvalidBackchainException
import net.corda.ledger.utxo.flow.impl.flows.backchain.TopologicalSort
import net.corda.ledger.utxo.flow.impl.flows.backchain.dependencies
import net.corda.ledger.utxo.flow.impl.groupparameters.verifier.SignedGroupParametersVerifier
import net.corda.ledger.utxo.flow.impl.persistence.TransactionExistenceStatus
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerGroupParametersPersistenceService
import net.corda.ledger.utxo.flow.impl.persistence.UtxoLedgerPersistenceService
import net.corda.membership.lib.SignedGroupParameters
import net.corda.sandbox.CordaSystemFlow
import net.corda.schema.configuration.ConfigKeys.UTXO_LEDGER_CONFIG
import net.corda.utilities.trace
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

/**
 * V1 changed slightly between 5.0 and 5.1. (5.1 supports distributing SignedGroupParameters.)
 * This change is not managed through flow versioning since flow interoperability is not supported between these versions.
 *
 * This flow will throw a [InvalidBackchainException] if we found any transactions in the database with an [INVALID] status
 * during the back-chain resolution.
 */

@CordaSystemFlow
class TransactionBackchainReceiverFlowV1(
    private val initialTransactionIds: Set<SecureHash>,
    private val originalTransactionsToRetrieve: Set<SecureHash>,
    private val session: FlowSession
) : SubFlow<TopologicalSort> {

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val BACKCHAIN_BATCH_CONFIG_PATH = "backchain.batchSize"
    }

    @CordaInject
    lateinit var utxoLedgerPersistenceService: UtxoLedgerPersistenceService

    @CordaInject
    lateinit var utxoLedgerMetricRecorder: UtxoLedgerMetricRecorder

    @CordaInject
    lateinit var utxoLedgerGroupParametersPersistenceService: UtxoLedgerGroupParametersPersistenceService

    @CordaInject
    lateinit var signedGroupParametersVerifier: SignedGroupParametersVerifier

    @CordaInject
    lateinit var flowConfigService: FlowConfigService

    @Suspendable
    override fun call(): TopologicalSort {
        // Using a [Set] here ensures that if two or more transactions at the same level in the graph are dependent on the same transaction
        // then when the second and subsequent transactions see this dependency to add it to [transactionsToRetrieve] it will only exist
        // once and be retrieved once due to the properties of a [Set].
        val transactionsToRetrieve = LinkedHashSet(originalTransactionsToRetrieve)

        val sortedTransactionIds = TopologicalSort()

        val batchSize = flowConfigService.getConfig(UTXO_LEDGER_CONFIG).getInt(BACKCHAIN_BATCH_CONFIG_PATH)

        val existingTransactionIdsInDb = mutableMapOf<SecureHash, TransactionStatus>()

        while (transactionsToRetrieve.isNotEmpty()) {

            handleExistingTransactionsAndTheirDependencies(existingTransactionIdsInDb, transactionsToRetrieve, sortedTransactionIds)

            val batch = transactionsToRetrieve.take(batchSize)

            if (batch.isEmpty()) {
                log.trace { "Backchain batch is empty, will stop receiving transactions now." }
                break
            }

            log.trace {
                "Backchain resolution of $initialTransactionIds - Requesting the content of transactions $batch from transaction backchain"
            }

            @Suppress("unchecked_cast")
            val retrievedTransactions = session.sendAndReceive(
                List::class.java,
                TransactionBackchainRequestV1.Get(batch.toSet())
            ) as List<UtxoSignedTransaction>

            log.trace { "Backchain resolution of $initialTransactionIds - Received content for transactions $batch" }

            for (retrievedTransaction in retrievedTransactions) {

                val retrievedTransactionId = retrievedTransaction.id

                require(retrievedTransactionId in batch) {
                    "Backchain resolution of $initialTransactionIds - Received transaction $retrievedTransactionId which was not " +
                            "requested in the last batch $batch"
                }

                retrieveGroupParameters(retrievedTransaction)
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

        session.send(TransactionBackchainRequestV1.Stop)

        utxoLedgerMetricRecorder.recordTransactionBackchainLength(sortedTransactionIds.size)

        return sortedTransactionIds
    }

    @Suspendable
    private fun retrieveGroupParameters(
        retrievedTransaction: UtxoSignedTransaction
    ) {
        val (groupParametersHash, groupParameters) = fetchGroupParametersAndHashForTransaction(retrievedTransaction)

        if (groupParameters != null) {
            return
        }

        log.trace {
            "Backchain resolution of $initialTransactionIds - Retrieving group parameters ($groupParametersHash) " +
                    "for transaction (${retrievedTransaction.id})"
        }
        val retrievedSignedGroupParameters = session.sendAndReceive(
            SignedGroupParameters::class.java,
            TransactionBackchainRequestV1.GetSignedGroupParameters(groupParametersHash)
        )
        if (retrievedSignedGroupParameters.hash != groupParametersHash) {
            val message =
                "Backchain resolution of $initialTransactionIds - Requested group parameters: $groupParametersHash, " +
                        "but received: ${retrievedSignedGroupParameters.hash}"
            log.warn(message)
            throw CordaRuntimeException(message)
        }
        signedGroupParametersVerifier.verifySignature(
            retrievedSignedGroupParameters
        )

        utxoLedgerGroupParametersPersistenceService.persistIfDoesNotExist(retrievedSignedGroupParameters)
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
                    val ignoredDependencies = dependencies - unseenDependencies
                    "Backchain resolution of $initialTransactionIds - Adding dependencies for transaction ${retrievedTransaction.id} " +
                            "dependencies: $unseenDependencies to transactions to retrieve. Ignoring dependencies: $ignoredDependencies " +
                            "as they have already been seen."
                }
                sortedTransactionIds.add(retrievedTransaction.id, dependencies)
                transactionsToRetrieve.addAll(unseenDependencies)
            }
        }
    }

    @Suppress("NestedBlockDepth")
    @Suspendable
    private fun handleExistingTransactionsAndTheirDependencies(
        existingTransactionIdsInDb: MutableMap<SecureHash, TransactionStatus>,
        transactionsToRetrieve: LinkedHashSet<SecureHash>,
        sortedTransactionIds: TopologicalSort
    ) {
        var transactionsToCheck = transactionsToRetrieve.toMutableList()

        while (transactionsToCheck.isNotEmpty()) {
            val transactionsFromDb = utxoLedgerPersistenceService.findTransactionIdsAndStatuses(transactionsToCheck)

            // Check if we have any invalid transactions. If yes, we can't continue the back-chain resolution.
            val invalidTransactions = transactionsFromDb.filterValues { it == INVALID }.keys
            if (invalidTransactions.isNotEmpty()) {
                throw InvalidBackchainException(
                    "Found the following invalid transaction(s) during back-chain resolution: " +
                            "$invalidTransactions. Back-chain resolution cannot be continued."
                )
            }

            // Store the Verified/Unverified transactions from the database
            existingTransactionIdsInDb.putAll(transactionsFromDb)

            // Remove the transaction from the "to retrieve" and "to check" collections if they are in our DB,
            // and they are verified
            transactionsToCheck.removeIf { existingTransactionIdsInDb[it] == VERIFIED }
            transactionsToRetrieve.removeIf { existingTransactionIdsInDb[it] == VERIFIED }

            val newTransactionsToCheck = mutableListOf<SecureHash>()
            transactionsToCheck.forEach { transactionId ->
                if (existingTransactionIdsInDb[transactionId] != null) {
                    // Fetch the transaction object from the database, so we can get the dependencies
                    // Also fetch its status because it might have changed since the last time
                    val (transactionFromDb, status) = utxoLedgerPersistenceService.findSignedTransactionWithStatus(
                        transactionId,
                        UNVERIFIED
                    )
                        // If we can't fetch the transaction from the DB we skip it
                        // and just keep it in the "to retrieve" set, so it will be fetched later
                        ?: return@forEach

                    when (status) {
                        VERIFIED -> {
                            // If the status changed from UNVERIFIED -> VERIFIED we just remove it from the "to retrieve" set
                            // as we don't need to fetch it anymore
                            log.trace {
                                "Transaction with ID $transactionId had an unverified status originally it is verified. Skipping."
                            }
                            transactionsToRetrieve.remove(transactionId)
                        }

                        INVALID, DRAFT -> {
                            // If the status changed from UNVERIFIED -> INVALID we cannot go on with the resolution
                            // Also transitioning back to Draft is impossible.

                            throw InvalidBackchainException(
                                "Found the following $status transaction during back-chain resolution: " +
                                        "$transactionId. Backchain resolution cannot be continued."
                            )
                        }

                        UNVERIFIED -> {
                            // If the status is still UNVERIFIED we can continue as expected
                            if (transactionFromDb != null) {
                                // Add the dependencies we haven't seen yet to the "to retrieve" and "to check" sets.
                                // They might be removed later on from the "to retrieve" set if they are in the DB.
                                // Also add them to the topological sort, so it can be verified later on
                                if (transactionId !in sortedTransactionIds.transactionIds) {
                                    transactionFromDb.dependencies.let { dependencies ->
                                        val unseenDependencies = dependencies - sortedTransactionIds.transactionIds
                                        sortedTransactionIds.add(transactionId, dependencies)
                                        transactionsToRetrieve.addAll(unseenDependencies)
                                        newTransactionsToCheck.addAll(unseenDependencies)
                                    }
                                }

                                // Once we added the unverified transactions' dependencies into the "to retrieve" set
                                // we can remove the parent transaction from the set as we don't need to get that from
                                // the initiator but only if we know its group parameters
                                if (fetchGroupParametersAndHashForTransaction(transactionFromDb).second != null) {
                                    transactionsToRetrieve.remove(transactionId)
                                }
                            }
                        }
                    }
                }
            }
            transactionsToCheck = newTransactionsToCheck
        }
    }

    @Suspendable
    private fun fetchGroupParametersAndHashForTransaction(
        transaction: UtxoSignedTransaction
    ): Pair<SecureHash, SignedGroupParameters?> {
        val groupParametersHash = parseSecureHash(requireNotNull(
            (transaction.metadata as TransactionMetadataInternal).getMembershipGroupParametersHash()
        ) {
            "Transaction with ID ${transaction.id} does not have group parameters in its metadata."
        })

        return Pair(groupParametersHash, utxoLedgerGroupParametersPersistenceService.find(groupParametersHash))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionBackchainReceiverFlowV1

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
