package net.corda.ledger.persistence.utxo

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManager

interface UtxoRepository {

    /** Retrieves transaction by [id] */
    fun findTransaction(
        entityManager: EntityManager,
        id: String
    ): SignedTransactionContainer?

    /** Retrieves transaction component leafs */
    fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): Map<Int, List<ByteArray>>

    /** Retrieves transaction signatures */
    fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata>

    /** Retrieves a transaction's status */
    fun findTransactionStatus(
        entityManager: EntityManager,
        id: String,
    ): String?

    /** Persists transaction (operation is idempotent) */
    fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    )

    /** Persists transaction component leaf [data] (operation is idempotent) */
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

    /** Persists transaction CPK (operation is idempotent) */
    fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        fileChecksums: Collection<String>
    )

    /** Persists transaction output (operation is idempotent) */
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

    /** Persists relevant transaction states (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistTransactionRelevantStates(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        consumed: Boolean,
        timestamp: Instant
    )

    /** Persists transaction [signature] (operation is idempotent) */
    fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    )

    /** Persists transaction source (operation is idempotent) */
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

    /**
     * Persists or updates transaction [transactionStatus]. There is only one status per transaction. In case that status already
     * exists, it will be updated only if old and new statuses are one of the following combinations (and ignored otherwise):
     * - UNVERIFIED -> *
     * - VERIFIED -> VERIFIED
     * - INVALID -> INVALID
     */
    fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant
    )
}
