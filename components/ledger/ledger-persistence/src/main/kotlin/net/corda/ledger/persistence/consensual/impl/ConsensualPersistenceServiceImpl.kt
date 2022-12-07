package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.ledger.persistence.consensual.ConsensualPersistenceService
import net.corda.ledger.persistence.consensual.ConsensualTransactionReader
import net.corda.orm.utils.transaction
import net.corda.utilities.time.Clock
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import javax.persistence.EntityManager

class ConsensualPersistenceServiceImpl constructor(
    private val entityManager: EntityManager,
    private val repository: ConsensualRepository,
    private val sandboxDigestService: DigestService,
    private val utcClock: Clock
) : ConsensualPersistenceService {

    override fun findTransaction(id: String): SignedTransactionContainer? {
        return entityManager.transaction { em ->
            repository.findTransaction(em, id)
        }
    }

    override fun persistTransaction(transaction: ConsensualTransactionReader): List<CordaPackageSummary> {
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
            val cpkMetadata = transaction.cpkMetadata
            val persistedCpkCount = repository.persistTransactionCpk(
                em,
                transactionIdString,
                cpkMetadata
            )
            return if (persistedCpkCount < cpkMetadata.size) {
                val persistedCpks = repository.findTransactionCpkChecksums(em, cpkMetadata)
                cpkMetadata.filterNot { persistedCpks.contains(it.fileChecksum) }
            } else {
                emptyList()
            }
        }
    }
}
