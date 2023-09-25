package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.UtxoTransactionOutputDto
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.ledger.utxo.StateRef
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManager

@Suppress("TooManyFunctions")
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

    /** Retrieves transaction component leaves related to visible unspent states and subclass states.*/
    fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager
    ):  List<UtxoTransactionOutputDto>

    /** Retrieves transaction component leaves related to visible unspent states */
    fun findUnconsumedVisibleStatesByExactType(
        entityManager: EntityManager,
        stateClassType: String
    ):  List<UtxoTransactionOutputDto>

    /** Retrieves transaction component leafs related to specific StateRefs */
    fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>
    ):  List<UtxoTransactionOutputDto>

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

    /** Marks visible states of transactions consumed */
    fun markTransactionVisibleStatesConsumed(
        entityManager: EntityManager,
        stateRefs: List<StateRef>,
        timestamp: Instant
    )

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
        tokenType: String? = null,
        tokenIssuerHash: String? = null,
        tokenNotaryX500Name: String? = null,
        tokenSymbol: String? = null,
        tokenTag: String? = null,
        tokenOwnerHash: String? = null,
        tokenAmount: BigDecimal? = null,
        timestamp: Instant
    )

    /** Persists visible transaction states (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistTransactionVisibleStates(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        consumed: Boolean,
        customRepresentation: CustomRepresentation,
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

    /** Retrieves signed group parameters */
    fun findSignedGroupParameters(
        entityManager: EntityManager,
        hash: String
    ): SignedGroupParameters?

    /** Persists signed group parameters */
    fun persistSignedGroupParameters(
        entityManager: EntityManager,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant
    )
}
