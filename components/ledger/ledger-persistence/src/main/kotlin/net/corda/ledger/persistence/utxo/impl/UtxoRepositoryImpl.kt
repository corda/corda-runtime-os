package net.corda.ledger.persistence.utxo.impl

import net.corda.ledger.persistence.utxo.UtxoRepository
import java.time.Instant
import javax.persistence.EntityManager

class UtxoRepositoryImpl : UtxoRepository {

    override fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created)
            VALUES (:id, :privacySalt, :accountId, :createdAt)"""
        )
            .setParameter("id", id)
            .setParameter("privacySalt", privacySalt)
            .setParameter("accountId", account)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash, created)
            VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("groupIndex", groupIndex)
            .setParameter("leafIndex", leafIndex)
            .setParameter("data", data)
            .setParameter("hash", hash)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }

    override fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: String,
        timestamp: Instant
    ) {
        entityManager.createNativeQuery(
            """
            INSERT INTO {h-schema}utxo_transaction_status(transaction_id, status, created)
            VALUES (:transactionId, :status, :createdAt)"""
        )
            .setParameter("transactionId", transactionId)
            .setParameter("status", status)
            .setParameter("createdAt", timestamp)
            .executeUpdate()
    }
}
