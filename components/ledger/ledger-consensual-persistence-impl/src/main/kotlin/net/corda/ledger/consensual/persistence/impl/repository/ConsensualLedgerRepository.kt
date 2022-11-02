package net.corda.ledger.consensual.persistence.impl.repository

import net.corda.ledger.common.data.transaction.PrivacySaltImpl
import net.corda.ledger.common.data.transaction.WireTransaction
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.consensual.data.transaction.ConsensualSignedTransactionContainer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import java.time.Instant
import javax.persistence.EntityManager
import javax.persistence.Query
import javax.persistence.Tuple

/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 */
class ConsensualLedgerRepository(
    private val digestService: DigestService,
    private val serializationService: SerializationService,
    private val wireTransactionFactory: WireTransactionFactory
) {
    companion object {
        private val logger = contextLogger()
        private val componentGroupListsTuplesMapper = ComponentGroupListsTuplesMapper()
    }

    /** Reads [ConsensualSignedTransactionContainer] with given [id] from database. */
    fun findTransaction(entityManager: EntityManager, id: String): ConsensualSignedTransactionContainer? {
        val rows = entityManager.createNativeQuery(
            """
                SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data, txc.hash
                FROM {h-schema}consensual_transaction AS tx
                JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
                WHERE tx.id = :id
                ORDER BY txc.group_idx, txc.leaf_idx
                """,
            Tuple::class.java)
            .setParameter("id", id)
            .resultListAsTuples()

        if (rows.isEmpty()) return null

        val wireTransaction = wireTransactionFactory.create(
            rows.mapTuples(componentGroupListsTuplesMapper),
            PrivacySaltImpl(rows.first()[1] as ByteArray)
        )

        return ConsensualSignedTransactionContainer(
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
            Tuple::class.java)
            .setParameter("transactionId", transactionId)
            .resultListAsTuples()
            .map { r -> serializationService.deserialize(r.get(0) as ByteArray) }
    }

    /** Persists [signedTransaction] data to database and link it to existing CPKs. */
    fun persistTransaction(
        entityManager: EntityManager,
        signedTransaction: ConsensualSignedTransactionContainer,
        status: String,
        account :String
    ) {
        val transactionId = signedTransaction.id.toHexString()
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
            VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", wireTransaction.id.toHexString())
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
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", data.hashAsHexString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    /** Persists transaction's [status] to database. */
    private fun persistTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        transactionId: String,
        status: String
    ): Int {
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, created)
            VALUES (:txId, :status, :createdAt)"""
        )
            .setParameter("txId", transactionId)
            .setParameter("status", status)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
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
            VALUES (:transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("signatureIdx", index)
            .setParameter("signature", serializationService.serialize(signature).bytes)
            .setParameter("publicKeyHash", signature.by.encoded.hashAsHexString())
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    /** Persists link between [signedTransaction] and it's CPK data to database. */
    fun persistTransactionCpk(
        entityManager: EntityManager,
        signedTransaction: ConsensualSignedTransactionContainer
    ): Int {
        val cpkMetadata = signedTransaction.wireTransaction.metadata.getCpkMetadata()
        return entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}consensual_transaction_cpk
            SELECT :transactionId, file_checksum
            FROM {h-schema}consensual_cpk
            WHERE file_checksum in (:fileChecksums)"""
        )
            .setParameter("transactionId", signedTransaction.id.toHexString())
            .setParameter("fileChecksums", cpkMetadata.map { it.fileChecksum })
            .executeUpdate()
    }

    /** Finds file checksums of CPKs linked to transaction. */
    fun findTransactionCpkChecksums(
        entityManager: EntityManager,
        signedTransaction: ConsensualSignedTransactionContainer,
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

    private fun ByteArray.hashAsHexString() =
        digestService.hash(this, DigestAlgorithmName.SHA2_256).toHexString()

    @Suppress("UNCHECKED_CAST")
    private fun Query.resultListAsTuples() = resultList as List<Tuple>
}
