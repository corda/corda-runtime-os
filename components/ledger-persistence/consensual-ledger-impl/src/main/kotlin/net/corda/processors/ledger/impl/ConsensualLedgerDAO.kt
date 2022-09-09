package net.corda.processors.ledger.impl

import net.corda.data.ledger.consensual.PersistTransaction
import net.corda.data.persistence.EntityResponse
import net.corda.data.persistence.EntityResponseSuccess
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.v5.base.util.contextLogger
import java.time.Instant
import javax.persistence.EntityManager

class ConsensualLedgerDAO(
    val loadClass: (holdingIdentity: net.corda.virtualnode.HoldingIdentity, fullyQualifiedClassName: String) -> Class<*>,
) {
    companion object {
        private val logger = contextLogger()
    }

    // TODO: This should probably take a ConsensualSignedTransactionImpl which includes WireTransaction and signers.
    fun persistTransaction(transaction: MappableWireTransaction, entityManager: EntityManager): EntityResponse {
        logger.debug("TMP DEBUG 1. transaction: $transaction, loadClass: $loadClass")

        val now = Instant.now()
        writeTransaction(entityManager, now, transaction)
        writeTransactionStatus(entityManager, now, transaction, "Faked")
        // TODO: when and what do we write to the signatures table?
        // TODO: when and what do we write to the CPKs table?

        // construct response
        return EntityResponse(emptyList())
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
