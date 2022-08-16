package net.corda.ledger.consensual.impl.internal

import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import javax.persistence.EntityManager

class ConsensualLedgerDAO(
    val requestId: String,
    val clock: () -> Instant,
    val loadClass: (holdingIdentity: net.corda.virtualnode.HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
) {
    companion object {
        private val logger = contextLogger()
    }

    // TODO: rm tmp transaction/leaf structures when real ones are merged
    data class SignedTransaction(val id: String, val privacySalt: Array<Byte>, val accountId: String, val merkleTree: List<List<MerkleLeaf>>)
    data class MerkleLeaf(val data: Array<Byte>, val hash: String)

    fun persistTransaction(request: PersistTransaction, entityManager: EntityManager): EntityResponse {
        logger.debug("TMP DEBUG 1. request: $request, requestId: $requestId, loadClass: $loadClass")
        val now = clock()
        // TODO: deserialise the request data to a real SignedTransaction
        val tx = SignedTransaction(
            "0",
            arrayOf<Byte>(1),
            "2",
            listOf(listOf(MerkleLeaf(arrayOf<Byte>(3,4), "5")))
        )
        // do some native queries to insert multiple rows (and across tables)
        writeTransaction(entityManager, now, tx)
        writeTransactionStatus(entityManager, now, tx, "Faked")
        // TODO: when and what do we write to the signatures table?
        // TODO: when and what do we write to the CPKs table?

        // construct response
        return EntityResponse(now, requestId, EntityResponseSuccess())
    }

    private fun writeTransaction(entityManager: EntityManager, timestamp: Instant, tx: SignedTransaction) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction(id, privacy_salt, account_id, created)
                VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", tx.id)
            .setParameter("privacySalt", tx.privacySalt)
            .setParameter("accountId", tx.accountId)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    private fun writeTransactionStatus(
        entityManager: EntityManager,
        timestamp: Instant,
        tx: SignedTransaction,
        status: String
    ) {
        entityManager.createNativeQuery(
            """
                INSERT INTO {h-schema}consensual_transaction_status(transaction_id, status, created)
                VALUES (:txId, :status, :createdAt)"""
        )
            .setParameter("txId", tx.id)
            .setParameter("status", status)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }
}
