package net.corda.ledger.persistence.consensual.impl

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapToComponentGroups
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.orm.DatabaseType.HSQLDB
import net.corda.orm.DatabaseTypeProvider
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.utilities.serialization.deserialize
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.OPTIONAL
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
    private val digestService: DigestService,
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory,
    @Reference(cardinality = OPTIONAL)
    databaseTypeProvider: DatabaseTypeProvider?
) : ConsensualRepository, UsedByPersistence {
    companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
        private val consensualComponentGroupMapper = ConsensualComponentGroupMapper()
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val databaseType = databaseTypeProvider?.databaseType

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
        val nativeSQL = when (databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}consensual_transaction AS ct
            USING (VALUES :id, CAST(:privacySalt as VARBINARY(64)), :accountId, CAST(:createdAt as TIMESTAMP))
                AS x(id, privacy_salt, account_id, created)
            ON ct.id = x.id
            WHEN NOT MATCHED THEN
                INSERT (id, privacy_salt, account_id, created)
                VALUES (x.id, x.privacy_salt, x.account_id, x.created)"""
        else -> """
            INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when (databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}consensual_transaction_component AS ctc
            USING (VALUES :transactionId, CAST(:groupIndex AS INT), CAST(:leafIndex AS INT),
                          CAST(:data AS VARBINARY(1048576)), :hash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, group_idx, leaf_idx, data, hash, created)
            ON ctc.transaction_id = x.transaction_id AND ctc.group_idx = x.group_idx AND ctc.leaf_idx = x.leaf_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES (x.transaction_id, x.group_idx, x.leaf_idx, x.data, x.hash, x.created)"""
            else -> """
            INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        return entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}consensual_transaction_status AS cts
            USING (VALUES :id, :status, CAST(:updatedAt AS TIMESTAMP)) AS x(transaction_id, status, updated)
            ON cts.transaction_id = x.transaction_id
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, status, updated)
                VALUES (x.transaction_id, x.status, x.updated)
            WHEN MATCHED AND (cts.status = x.status OR cts.status = '$UNVERIFIED') THEN
                UPDATE SET status = x.status, updated = x.updated"""
            else -> """
            INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, updated)
            VALUES (:id, :status, :updatedAt)
            ON CONFLICT(transaction_id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
                WHERE consensual_transaction_status.status = EXCLUDED.status OR consensual_transaction_status.status = '$UNVERIFIED'"""
        }.trimIndent()
        val rowsUpdated = entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}consensual_transaction_signature AS cts
            USING (VALUES :transactionId, CAST(:signatureIdx AS INT), CAST(:signature AS VARBINARY(1048576)),
                          :publicKeyHash, CAST(:createdAt AS TIMESTAMP))
                AS x(transaction_id, signature_idx, signature, pub_key_hash, created)
            ON cts.transaction_id = x.transaction_id AND cts.signature_idx = x.signature_idx
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, signature_idx, signature, pub_key_hash, created)
                VALUES (x.transaction_id, x.signature_idx, x.signature, x.pub_key_hash, x.created)"""
            else -> """
            INSERT INTO {h-schema}consensual_transaction_signature(transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (:transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        return entityManager.createNativeQuery(nativeSQL)
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
        val nativeSQL = when(databaseType) {
            HSQLDB -> """
            MERGE INTO {h-schema}consensual_transaction_cpk AS ctc
            USING (SELECT :transactionId, file_checksum
                   FROM {h-schema}consensual_cpk
                   WHERE file_checksum IN (:fileChecksums)) AS x(transaction_id, file_checksum)
            ON x.transaction_id = ctc.transaction_id AND x.file_checksum = ctc.file_checksum
            WHEN NOT MATCHED THEN
                INSERT (transaction_id, file_checksum)
                VALUES (x.transaction_id, x.file_checksum)"""
            else -> """
            INSERT INTO {h-schema}consensual_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}consensual_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
        }.trimIndent()
        return entityManager.createNativeQuery(nativeSQL)
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
