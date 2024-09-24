package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.libs.persistence.common.mapToComponentGroups
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 * The component only exists to be created inside a PERSISTENCE sandbox. We denote it
 * as "corda.marker.only" to force the sandbox to create it, despite it implementing
 * only the [UsedByPersistence] marker interface.
 */
@Component(
    service = [ ConsensualRepository::class, UsedByPersistence::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class ConsensualRepositoryImpl @Activate constructor(
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference
    private val queryProvider: ConsensualQueryProvider
) : ConsensualRepository, UsedByPersistence {
    companion object {
        private val consensualComponentGroupMapper = ConsensualComponentGroupMapper()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    override fun findTransaction(entityManager: EntityManager, id: String): SignedTransactionContainer? {
        val rows = entityManager.createNativeQuery(queryProvider.findTransaction, Tuple::class.java)
            .setParameter("id", id)
            .resultListAsTuples()

        if (rows.isEmpty()) return null

        val wireTransaction = wireTransactionFactory.create(
            rows.mapToComponentGroups(consensualComponentGroupMapper),
            PrivacySaltImpl(rows.first()[1] as ByteArray)
        )

        return SignedTransactionContainer(
            wireTransaction,
            findTransactionSignatures(entityManager, id)
        )
    }

    override fun findTransactionCpkChecksums(
        entityManager: EntityManager,
        cpkMetadata: List<CordaPackageSummary>
    ): Set<String> {
        return entityManager.createNativeQuery(queryProvider.findTransactionCpkChecksums, Tuple::class.java)
            .setParameter("fileChecksums", cpkMetadata.map { it.fileChecksum })
            .resultListAsTuples()
            .mapTo(HashSet()) { r -> r.get(0) as String }
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(queryProvider.findTransactionSignatures, Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> serializationService.deserialize(r.get(0) as ByteArray) }
    }

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(queryProvider.persistTransaction)
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction [$id]")
    }

    @Suppress("LongParameterList")
    override fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    ): Int {
        return entityManager.createNativeQuery(queryProvider.persistTransactionComponentLeaf)
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction component [$transactionId, $groupIndex, $leafIndex]")
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: TransactionStatus,
        timestamp: Instant
    ): Int {
        // Insert/update status. Update ignored unless: UNVERIFIED -> * | VERIFIED -> VERIFIED | INVALID -> INVALID
        val rowsUpdated = entityManager.createNativeQuery(queryProvider.persistTransactionStatus)
            .setParameter("id", transactionId)
            .setParameter("status", status.value)
            .setParameter("updatedAt", timestamp)
            .executeUpdate()
            .logResult("transaction status [$transactionId, ${status.value}]")

        check(rowsUpdated == 1 || status == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $status (illegal state that shouldn't happen)"
        }

        return rowsUpdated
    }

    override fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    ): Int {
        return entityManager.createNativeQuery(queryProvider.persistTransactionSignature)
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.toString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction signature [$transactionId, $index]")
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        cpkMetadata: List<CordaPackageSummary>
    ): Int {
        val fileChecksums = cpkMetadata.map(CordaPackageSummary::getFileChecksum)
        return entityManager.createNativeQuery(queryProvider.persistTransactionCpk)
            .setParameter("transactionId", transactionId)
            .setParameter("fileChecksums", fileChecksums)
            .executeUpdate()
            .logResult("transaction CPK(s) [$transactionId, $fileChecksums]")
    }

    private fun Int.logResult(entity: String): Int {
        if (this == 0) {
            logger.debug {
                "Consensual ledger entity not persisted due to existing row in database: $entity"
            }
        }
        return this
    }

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
