package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapToComponentGroups
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE
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
    service = [ ConsensualRepositoryImpl::class, UsedByPersistence::class ],
    property = [ "corda.marker.only:Boolean=true" ],
    scope = PROTOTYPE
)
class ConsensualRepositoryImpl @Activate constructor(
    @Reference
    private val digestService: DigestService,
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory
) : ConsensualRepository, UsedByPersistence {
    companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
        private val consensualComponentGroupMapper = ConsensualComponentGroupMapper()
        private val logger = contextLogger()
    }

    override fun findTransaction(entityManager: EntityManager, id: String): SignedTransactionContainer? {
        val rows = entityManager.createNativeQuery(
            """
                SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data
                FROM {h-schema}consensual_transaction AS tx
                JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
                WHERE tx.id = :id
                ORDER BY txc.group_idx, txc.leaf_idx
                """,
            Tuple::class.java
        )
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
        return entityManager.createNativeQuery(
            """
            SELECT file_checksum
            FROM {h-schema}consensual_transaction_cpk
            WHERE file_checksum in (:fileChecksums)""",
            Tuple::class.java
        )
            .setParameter("fileChecksums", cpkMetadata.map { it.fileChecksum })
            .resultListAsTuples()
            .mapTo(HashSet()) { r -> r.get(0) as String }
    }

    override fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata> {
        return entityManager.createNativeQuery(
            """
                SELECT signature
                FROM {h-schema}consensual_transaction_signature
                WHERE transaction_id = :transactionId
                ORDER BY signature_idx""",
            Tuple::class.java
        )
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
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
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
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
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
        val rowsUpdated = entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, updated)
            VALUES (:id, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE consensual_transaction_status.status = EXCLUDED.status OR consensual_transaction_status.status = '$UNVERIFIED'"""
        )
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
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_signature(transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (:transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.encoded.hashAsString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
            .logResult("transaction signature [$transactionId, $index]")
    }

    override fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        cpkMetadata: List<CordaPackageSummary>
    ): Int {
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}consensual_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("fileChecksums", cpkMetadata.map { it.fileChecksum })
            .executeUpdate()
    }

    private fun Int.logResult(entity: String): Int {
        if (this == 0) {
            logger.trace {
                "Consensual ledger entity not persisted due to existing row in database: $entity"
            }
        }
        return this
    }

    private fun ByteArray.hashAsString() =
        digestService.hash(this, DigestAlgorithmName.SHA2_256).toString()

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
