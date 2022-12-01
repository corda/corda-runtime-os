package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManager

interface UtxoRepository {

    fun findTransaction(
        entityManager: EntityManager,
        id: String
    ): SignedTransactionContainer?

    fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): Map<Int, List<ByteArray>>

    fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata>

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

    fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    )

    @Suppress("LongParameterList")
    fun persistTransactionOutput(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        type: String,
        tokenType: String,
        tokenIssuerHash: String,
        tokenNotaryX500Name: String,
        tokenSymbol: String,
        tokenTag: String,
        tokenOwnerHash: String,
        tokenAmount: BigDecimal,
        timestamp: Instant
    )

    @Suppress("LongParameterList")
    fun persistTransactionRelevancy(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        relevant: Boolean,
        consumed: Boolean,
        timestamp: Instant
    )

    fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    )

    @Suppress("LongParameterList")
    fun persistTransactionSource(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        refTransactionId: String,
        refLeafIndex: Int,
        isRefInput: Boolean,
        timestamp: Instant
    )

    fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: String,
        timestamp: Instant
    )
}
