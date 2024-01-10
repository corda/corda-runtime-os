package net.corda.ledger.persistence.consensual

import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.common.transaction.CordaPackageSummary
import java.time.Instant
import javax.persistence.EntityManager

interface ConsensualRepository {
    /** Reads [SignedTransactionContainer] with given [id] from database. */
    fun findTransaction(entityManager: EntityManager, id: String): SignedTransactionContainer?

    /** Finds file checksums of CPKs linked to transaction. */
    fun findTransactionCpkChecksums(
        entityManager: EntityManager,
        cpkMetadata: List<CordaPackageSummary>
    ): Set<String>

    /** Reads [DigitalSignatureAndMetadata] for signed transaction with given [transactionId] from database. */
    fun findTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String
    ): List<DigitalSignatureAndMetadata>

    /** Persists transaction to database. */
    fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant
    )

    /** Persists component's leaf [data] to database. */
    @Suppress("LongParameterList")
    fun persistTransactionComponentLeaf(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        data: ByteArray,
        hash: String,
        timestamp: Instant
    ): Int

    /** Persists link between transaction and it's CPK data to database. */
    fun persistTransactionCpk(
        entityManager: EntityManager,
        transactionId: String,
        cpkMetadata: List<CordaPackageSummary>
    ): Int

    /** Persists transaction's [signature] to database. */
    fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    ): Int

    /**
     * Persists or updates transaction [status]. There is only one status per transaction. In case that status already
     * exists, it will be updated only if old and new statuses are one of the following combinations (and ignored otherwise):
     * - UNVERIFIED -> *
     * - VERIFIED -> VERIFIED
     * - INVALID -> INVALID
     */
    fun persistTransactionStatus(
        entityManager: EntityManager,
        transactionId: String,
        status: TransactionStatus,
        timestamp: Instant
    ): Int
}