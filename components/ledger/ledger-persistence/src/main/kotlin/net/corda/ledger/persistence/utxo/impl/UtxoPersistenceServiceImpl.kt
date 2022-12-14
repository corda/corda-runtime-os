package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.consensual.data.transaction.ConsensualComponentGroup
import net.corda.ledger.persistence.utxo.UtxoPersistenceService
import net.corda.ledger.persistence.utxo.UtxoRepository
import net.corda.ledger.persistence.utxo.UtxoTransactionReader
import net.corda.orm.utils.transaction
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import javax.persistence.EntityManager

class UtxoPersistenceServiceImpl constructor(
    private val entityManager: EntityManager,
    private val repository: UtxoRepository,
    private val sandboxDigestService: DigestService,
    private val utcClock: Clock
) : UtxoPersistenceService {

    override fun findTransaction(id: String, transactionStatus: TransactionStatus): SignedTransactionContainer? {
        return entityManager.transaction { em ->
            val status = repository.findTransactionStatus(em, id)
            if (status == transactionStatus.value) {
                repository.findTransaction(em, id)
            } else {
                null
            }
        }
    }

    override fun persistTransaction(transaction: UtxoTransactionReader) {
        val nowUtc = utcClock.instant()

        entityManager.transaction { em ->
            val transactionIdString = transaction.id.toString()

            // Insert the Transaction
            repository.persistTransaction(
                em,
                transactionIdString,
                transaction.privacySalt.bytes,
                transaction.account,
                nowUtc
            )

            // Insert the Transactions components
            transaction.rawGroupLists.mapIndexed { groupIndex, leaves ->
                leaves.mapIndexed { leafIndex, data ->
                    repository.persistTransactionComponentLeaf(
                        em,
                        transactionIdString,
                        groupIndex,
                        leafIndex,
                        data,
                        sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString(),
                        nowUtc
                    )
                }
            }

            // Insert relevancy information
            transaction.relevantStatesIndexes.forEach { relevantStateIndex ->
                repository.persistTransactionRelevantStates(
                    em,
                    transactionIdString,
                    ConsensualComponentGroup.OUTPUT_STATES.ordinal,
                    relevantStateIndex,
                    consumed = false,
                    nowUtc
                )
            }

            // Insert the Transactions signatures
            transaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
                repository.persistTransactionSignature(
                    em,
                    transactionIdString,
                    index,
                    digitalSignatureAndMetadata,
                    nowUtc
                )
            }

            // Insert the transactions current status
            repository.persistTransactionStatus(
                em,
                transactionIdString,
                transaction.status,
                nowUtc
            )

            // Insert the CPK details liked to this transaction
            // TODOs: The CPK file meta does not exist yet, this will be implemented by
            // https://r3-cev.atlassian.net/browse/CORE-7626
        }
    }

    override fun persistTransactionIfDoesNotExist(
        transaction: SignedTransactionContainer,
        transactionStatus: TransactionStatus,
        account: String
    ): Pair<String?, List<CordaPackageSummary>> {
        val nowUtc = utcClock.instant()

        return entityManager.transaction { em ->
            val transactionIdString = transaction.id.toString()

            val status = repository.findTransactionStatus(em, transactionIdString)

            if (status != null) {
                return@transaction status to emptyList()
            }

            // Insert the Transaction
            repository.persistTransaction(
                em,
                transactionIdString,
                transaction.wireTransaction.privacySalt.bytes,
                account,
                nowUtc
            )

            // Insert the Transactions components
            transaction.wireTransaction.componentGroupLists.mapIndexed { groupIndex, leaves ->
                leaves.mapIndexed { leafIndex, data ->
                    repository.persistTransactionComponentLeaf(
                        em,
                        transactionIdString,
                        groupIndex,
                        leafIndex,
                        data,
                        sandboxDigestService.hash(data, DigestAlgorithmName.SHA2_256).toString(),
                        nowUtc
                    )
                }
            }

            // Insert the Transactions signatures
            transaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
                repository.persistTransactionSignature(
                    em,
                    transactionIdString,
                    index,
                    digitalSignatureAndMetadata,
                    nowUtc
                )
            }

            // Insert the transactions current status
            repository.persistTransactionStatus(
                em,
                transactionIdString,
                transactionStatus,
                nowUtc
            )

            // Insert the CPK details liked to this transaction
            // TODOs: The CPK file meta does not exist yet, this will be implemented by
            // https://r3-cev.atlassian.net/browse/CORE-7626

            return null to emptyList()
        }
    }

    override fun updateStatus(id: String, transactionStatus: TransactionStatus) {
        entityManager.transaction { em ->
            repository.persistTransactionStatus(em, id, transactionStatus, utcClock.instant())
        }
    }
}
