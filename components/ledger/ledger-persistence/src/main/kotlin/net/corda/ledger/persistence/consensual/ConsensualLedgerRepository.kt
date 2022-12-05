package net.corda.ledger.persistence.consensual

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.persistence.common.mapTuples
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.crypto.DigestService
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.DigestAlgorithmName
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
    service = [ ConsensualLedgerRepository::class, UsedByPersistence::class ],
    property = [ "corda.marker.only:Boolean=true" ],
    scope = PROTOTYPE
)
class ConsensualLedgerRepository @Activate constructor(
    @Reference
    private val digestService: DigestService,
    @Reference
    private val serializationService: SerializationService,
    @Reference
    private val wireTransactionFactory: WireTransactionFactory
) : UsedByPersistence {
    private companion object {
        private val UNVERIFIED = TransactionStatus.UNVERIFIED.value
        private val componentGroupListsTuplesMapper = ComponentGroupListsTuplesMapper()
    }

    /** Reads [SignedTransactionContainer] with given [id] from database. */
    fun findTransaction(entityManager: EntityManager, id: String): SignedTransactionContainer? {
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
            rows.mapTuples(componentGroupListsTuplesMapper),
            PrivacySaltImpl(rows.first()[1] as ByteArray)
        )

        return SignedTransactionContainer(
            wireTransaction,
            findSignatures(entityManager, id)
        )
    }

    /** Reads [DigitalSignatureAndMetadata] for signed transaction with given [transactionId] from database. */
    private fun findSignatures(
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

    /** Persists [signedTransaction] data to database and link it to existing CPKs. */
    fun persistTransaction(
        entityManager: EntityManager,
        signedTransaction: SignedTransactionContainer,
        status: TransactionStatus,
        account: String
    ) {
        val transactionId = signedTransaction.id.toString()
        val wireTransaction = signedTransaction.wireTransaction
        val now = Instant.now()
        persistTransaction(entityManager, now, wireTransaction, account)
        // Persist component group lists
        wireTransaction.componentGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, bytes ->
                persistComponentLeaf(entityManager, now, transactionId, groupIndex, leafIndex, bytes)
            }
        }
        persistTransactionStatus(entityManager, now, transactionId, status)
        // Persist signatures
        signedTransaction.signatures.forEachIndexed { index, digitalSignatureAndMetadata ->
            persistSignature(entityManager, now, transactionId, index, digitalSignatureAndMetadata)
        }
    }

    /** Persists [wireTransaction] data to database. */
    private fun persistTransaction(
        entityManager: EntityManager,
        timestamp: Instant,
        wireTransaction: WireTransaction,
        account: String
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("id", wireTransaction.id.toString())
            .setParameter("privacySalt", wireTransaction.privacySalt.bytes)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    /** Persists component's leaf [data] to database. */
    @Suppress("LongParameterList")
    private fun persistComponentLeaf(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray
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
            .setParameter("hash", data.hashAsString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    /**
     * Persists or updates transaction [status]. There is only one status per transaction. In case that status already
     * exists, it will be updated only if old and new statuses are one of the following combinations (and ignored otherwise):
     * - UNVERIFIED -> *
     * - VERIFIED -> VERIFIED
     * - INVALID -> INVALID
     */
    private fun persistTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        status: TransactionStatus
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

        check(rowsUpdated == 1 || status == TransactionStatus.UNVERIFIED) {
            // VERIFIED -> INVALID or INVALID -> VERIFIED is a system error as verify should always be consistent and deterministic
            "Existing status for transaction with ID $transactionId can't be updated to $status (illegal state that shouldn't happen)"
        }

        return rowsUpdated
    }

    /** Persists transaction's [signature] to database. */
    private fun persistSignature(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata
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
    }

    /** Persists link between [signedTransaction] and it's CPK data to database. */
    fun persistTransactionCpk(
        entityManager: EntityManager,
        signedTransaction: SignedTransactionContainer
    ): Int {
        val cpkMetadata = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}consensual_cpk
            WHERE file_checksum in (:fileChecksums)
            ON CONFLICT DO NOTHING"""
        )
            .setParameter("transactionId", signedTransaction.id.toString())
            .setParameter("fileChecksums", cpkMetadata.map { it.fileChecksum })
            .executeUpdate()
    }

    /** Finds file checksums of CPKs linked to transaction. */
    fun findTransactionCpkChecksums(
        entityManager: EntityManager,
        signedTransaction: SignedTransactionContainer,
    ): Set<String> {
        val cpkMetadata = signedTransaction.wireTransaction.metadata.getCpkMetadata()
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

    private fun ByteArray.hashAsString() =
        digestService.hash(this, DigestAlgorithmName.SHA2_256).toString()

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
