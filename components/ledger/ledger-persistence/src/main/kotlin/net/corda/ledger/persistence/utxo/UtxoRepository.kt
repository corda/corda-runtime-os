package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.MerkleProofDto
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import java.math.BigDecimal
import java.time.Instant
import javax.persistence.EntityManager

@Suppress("TooManyFunctions")
interface UtxoRepository {

    /** Retrieves transaction IDs and their statuses if its ID is included in the [transactionIds] list. */
    fun findTransactionIdsAndStatuses(
        entityManager: EntityManager,
        transactionIds: List<String>
    ): Map<SecureHash, String>

    /** Retrieves transaction by [id] */
    fun findTransaction(
        entityManager: EntityManager,
        id: String
    ): SignedTransactionContainer?

    /** Retrieves transaction component leafs except metadata which is stored separately */
    fun findTransactionComponentLeafs(
        entityManager: EntityManager,
        transactionId: String
    ): Map<Int, List<ByteArray>>

    /** Retrieves transaction component leaves related to visible unspent states and subclass states.*/
    fun findUnconsumedVisibleStatesByType(
        entityManager: EntityManager
    ): List<UtxoVisibleTransactionOutputDto>

    /** Retrieves transaction component leafs related to specific StateRefs */
    fun resolveStateRefs(
        entityManager: EntityManager,
        stateRefs: List<StateRef>
    ): List<UtxoVisibleTransactionOutputDto>

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
    @Suppress("LongParameterList")
    fun persistTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        status: TransactionStatus,
        metadataHash: String
    )

    /** Persists transaction metadata (operation is idempotent) */
    fun persistTransactionMetadata(
        entityManager: EntityManager,
        hash: String,
        metadataBytes: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String
    )

    /** Persists transaction source (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistTransactionSource(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        sourceStateTransactionId: String,
        sourceStateIndex: Int
    )

    /** Persists transaction component leaf [data] (operation is idempotent) */
//    @Suppress("LongParameterList")
//    fun persistTransactionComponentLeaf(
//        entityManager: EntityManager,
//        transactionId: String,
//        groupIndex: Int,
//        leafIndex: Int,
//        data: ByteArray,
//        hash: String
//    )

    fun persistTransactionComponents(
        entityManager: EntityManager,
        transactionId: String,
        components: List<List<ByteArray>>,
        hash: (ByteArray) -> String
    )

    /** Persists transaction output (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistVisibleTransactionOutput(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        leafIndex: Int,
        type: String,
        timestamp: Instant,
        consumed: Boolean,
        customRepresentation: CustomRepresentation,
        tokenType: String? = null,
        tokenIssuerHash: String? = null,
        tokenNotaryX500Name: String? = null,
        tokenSymbol: String? = null,
        tokenTag: String? = null,
        tokenOwnerHash: String? = null,
        tokenAmount: BigDecimal? = null
    )

    /** Persists transaction [signature] (operation is idempotent) */
    fun persistTransactionSignature(
        entityManager: EntityManager,
        transactionId: String,
        index: Int,
        signature: DigitalSignatureAndMetadata,
        timestamp: Instant
    )

    /**
     * Updates transaction [transactionStatus]. There is only one status per transaction. In case that status already
     * exists, it will be updated only if old and new statuses are one of the following combinations (and ignored otherwise):
     * - UNVERIFIED -> *
     * - VERIFIED -> VERIFIED
     * - INVALID -> INVALID
     */
    fun updateTransactionStatus(
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

    /** Persists a merkle proof and returns its ID */
    @Suppress("LongParameterList")
    fun persistMerkleProof(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int,
        treeSize: Int,
        leaves: List<Int>,
        hashes: List<String>
    ): String

    /** Persist a leaf index that belongs to a given merkle proof with ID [merkleProofId] */
    fun persistMerkleProofLeaf(
        entityManager: EntityManager,
        merkleProofId: String,
        leafIndex: Int
    )

    /** Find all the merkle proofs for a given transaction ID and component group index */
    fun findMerkleProofs(
        entityManager: EntityManager,
        transactionId: String,
        groupIndex: Int
    ): List<MerkleProofDto>
}
