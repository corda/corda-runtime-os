package net.corda.ledger.persistence.utxo

import java.time.Instant
import javax.persistence.EntityManager

interface UtxoRepository {

    fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    )

    @Suppress("LongParameterList")
    fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    )

    fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: String,
        timestamp: Instant
    )
}
