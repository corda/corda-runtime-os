package net.corda.processors.ledger.impl

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.corda.data.persistence.EntityResponse
import net.corda.v5.base.util.contextLogger
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.Instant
import javax.persistence.Entity
import javax.persistence.EntityManager

class ConsensualLedgerDAO(
    val loadClass: (holdingIdentity: net.corda.virtualnode.HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
) {
    companion object {
        private val logger = contextLogger()
        private val mapper = jacksonObjectMapper() // TODO: replace this with AMQP serialisation
    }

    // TODO: This should probably take a ConsensualSignedTransactionImpl which includes WireTransaction and signers.
    fun persistTransaction(transaction: MappableWireTransaction, entityManager: EntityManager): EntityResponse {
        val now = Instant.now()
        writeTransaction(entityManager, now, transaction)
        transaction.componentGroupLists.mapIndexed { groupIndex, leaves ->
            leaves.mapIndexed { leafIndex, bytes ->
                writeComponentLeaf(entityManager, now, transaction, groupIndex, leafIndex, bytes)
            }
        }
        writeTransactionStatus(entityManager, now, transaction, "Faked")
        // TODO: when and what do we write to the signatures table?
        // TODO: when and what do we write to the CPKs table?

        // construct response
        return EntityResponse(emptyList())
    }

    @Entity
    data class Transaction(val privacySalt: ByteArray,
                           val accountId: String,
                           val componentGroups: List<List<ByteArray>>,
                           val created: Instant)

    fun findTransaction(id: String, entityManager: EntityManager): EntityResponse {
        val results = entityManager.createNativeQuery(
            """
                SELECT tx.id, tx.privacy_salt, tx.account_id, tx.created, txc.group_idx, txc.leaf_idx, txc.data, txc.hash
                FROM {h-schema}consensual_transaction AS tx
                JOIN {h-schema}consensual_transaction_component AS txc ON tx.id = txc.transaction_id
                WHERE tx.id = :id
                """
        )
            .setParameter("id", id)
            .resultList

        return EntityResponse(results.map { r ->
            val json = mapper.writeValueAsBytes(r)
            logger.info("DEBUG: ${json.toString(Charset.defaultCharset())}")
            ByteBuffer.wrap(json)
        })
    }

    private fun writeTransaction(entityManager: EntityManager, timestamp: Instant, tx: MappableWireTransaction) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
                VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", tx.id.toHexString())
            .setParameter("privacySalt", tx.privacySalt.bytes)
            .setParameter("accountId", 123)             // TODO: where do we get this?
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeComponentLeaf(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: MappableWireTransaction,
        groupIndex: Int,
        leafIndex: Int,
        bytes: ByteArray
    ) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
                VALUES(:transactionId, :groupIndex, :leafIndex, :bytes, :hash, :createdAt)"""
        )
            .setParameter("transactionId", tx.id.toHexString())
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("bytes", bytes)
            .setParameter("hash", "fake_hash_123")
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: MappableWireTransaction,
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

}