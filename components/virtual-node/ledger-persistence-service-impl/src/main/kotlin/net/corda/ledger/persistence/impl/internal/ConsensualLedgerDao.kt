package net.corda.ledger.persistence.impl.internal

import net.corda.data.persistence.EntityResponse
import net.corda.ledger.common.impl.transaction.PrivacySaltImpl
import net.corda.ledger.common.impl.transaction.TransactionMetaData.Companion.CPK_IDENTIFIERS_KEY
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.base.types.toHexString
import net.corda.v5.base.util.contextLogger
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.merkle.MerkleTreeFactory
import java.security.MessageDigest
import java.time.Instant
import javax.persistence.EntityManager

class ConsensualLedgerDao(
    private val merkleTreeFactory: MerkleTreeFactory,
    private val digestService: DigestService,
    private val jsonMarshallingService: JsonMarshallingService
) {
    companion object {
        private val logger = contextLogger()
    }

    // TODO This should probably take a ConsensualSignedTransactionImpl which includes WireTransaction and signers.
    fun persistTransaction(entityManager: EntityManager, transaction: WireTransaction): EntityResponse {
        val now = Instant.now()
        writeTransaction(entityManager, now, transaction)
        transaction.componentGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, bytes ->
                writeComponentLeaf(entityManager, now, transaction, groupIndex, leafIndex, bytes)
            }
        }
        writeTransactionStatus(entityManager, now, transaction, "Faked") // TODO where to get the status from
        // TODO when and what do we write to the signatures table?
        // TODO when and what do we write to the CPKs table?
        writeCpk(entityManager, now, transaction)
        writeTransactionCpk(entityManager, transaction)

        // construct response
        return EntityResponse(emptyList())
    }

    fun findTransaction(entityManager: EntityManager, id: String): WireTransaction? {

        val rows = entityManager.createNativeQuery(
            """
                SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data, txc.hash
                FROM {h-schema}consensual_transaction AS tx
                JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
                WHERE tx.id = :id
                ORDER BY txc.group_idx, txc.leaf_idx
                """
        )
            .setParameter("id", id)
            .resultList

        if (rows.isEmpty()) return null

        val firstRowColumns = rows.first() as Array<*>
        val privacySalt = PrivacySaltImpl(firstRowColumns[1] as ByteArray)
        val componentGroupLists = queryRowsToComponentGroupLists(rows)
        return WireTransaction(merkleTreeFactory, digestService, jsonMarshallingService, privacySalt, componentGroupLists)
    }

    @VisibleForTesting
    internal fun queryRowsToComponentGroupLists(rows: List<Any?>): List<List<ByteArray>> {
        val componentGroupLists: MutableList<MutableList<ByteArray>> = mutableListOf()
        var componentsList: MutableList<ByteArray> = mutableListOf()
        var expectedGroupIdx = 0
        rows.forEach {
            val columns = it as Array<*>
            val groupIdx = (columns[4] as Number).toInt()   // txc.group_idx
            val leafIdx = (columns[5] as Number).toInt()    // txc.leaf_idx
            val data = columns[6] as ByteArray              // txc.data
            while (groupIdx > expectedGroupIdx) {
                // add empty lists for skipped group indices
                componentGroupLists.add(componentsList)
                componentsList = mutableListOf()
                expectedGroupIdx++
            }
            check(componentsList.size == leafIdx) {
                val id = columns[0] as String   // tx.id
                "Missing data for transaction with ID: $id, groupIdx: $groupIdx, leafIdx: ${componentsList.size}"
            }
            componentsList.add(data)
        }
        componentGroupLists.add(componentsList)
        return componentGroupLists
    }

    private fun writeTransaction(entityManager: EntityManager, timestamp: Instant, tx: WireTransaction) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
                VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", tx.id.toHexString())
            .setParameter("privacySalt", tx.privacySalt.bytes)
            .setParameter("accountId", 123)             // TODO where do we get this?
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    @Suppress("LongParameterList")
    private fun writeComponentLeaf(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: WireTransaction,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray
    ) {
        val dataDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        val hash = dataDigest.digest(data).toHexString()

        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)"""
        )
            .setParameter("transactionId", tx.id.toHexString())
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: WireTransaction,
        status: String
    ) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, created)
                VALUES (:txId, :status, :createdAt)"""
        )
            .setParameter("txId", tx.id.toHexString())
            .setParameter("status", status)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeCpk(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: WireTransaction
    ) {
        // TODO get values from transaction metadata
        val cpkIdentifiers = tx.metadata.get(CPK_IDENTIFIERS_KEY)
        logger.info("cpkIdentifiers = [$cpkIdentifiers]")
        val fileHash = SecureHash.parse("SHA-256:1234567890123456")
        val name = "cpk-name"
        val signerHash = SecureHash.parse("SHA-256:0000000000000000")
        val version = 1
        val data = ByteArray(10000)

        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_cpk(file_hash, name, signer_hash, version, data, created)
                VALUES (:fileHash, :name, :signerHash, :version, :data, :createdAt) ON CONFLICT DO NOTHING""")
            .setParameter("fileHash", fileHash.toHexString())
            .setParameter("name", name)
            .setParameter("signerHash", signerHash.toHexString())
            .setParameter("version", version)
            .setParameter("data", data)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeTransactionCpk(
        entityManager: EntityManager,
        tx: WireTransaction
    ) {
        // TODO get values from transaction metadata
        val fileHash = SecureHash.parse("SHA-256:1234567890123456")
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_cpk(transaction_id, file_hash)
                VALUES (:transactionId, :fileHash)""")
            .setParameter("transactionId", tx.id.toHexString())
            .setParameter("fileHash", fileHash.toHexString())
            .executeUpdate()
    }

}