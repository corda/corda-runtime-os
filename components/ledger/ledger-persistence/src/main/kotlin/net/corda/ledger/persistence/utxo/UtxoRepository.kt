package net.corda.ledger.persistence.utxo

import net.corda.data.membership.SignedGroupParameters
import net.corda.ledger.common.data.transaction.SignedTransactionContainer
import net.corda.ledger.common.data.transaction.TransactionStatus
import net.corda.ledger.utxo.data.transaction.MerkleProofDto
import net.corda.ledger.utxo.data.transaction.UtxoComponentGroup
import net.corda.ledger.utxo.data.transaction.UtxoFilteredTransactionDto
import net.corda.ledger.utxo.data.transaction.UtxoVisibleTransactionOutputDto
import net.corda.v5.application.crypto.DigitalSignatureAndMetadata
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.observer.UtxoToken
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
    ): Pair<String, Boolean>?

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

    /** Persists unverified transaction (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistUnverifiedTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        metadataHash: String,
    )

    /** Persists unverified transaction (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistFilteredTransaction(
        entityManager: EntityManager,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        metadataHash: String,
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
    fun persistTransactionSources(
        entityManager: EntityManager,
        transactionId: String,
        transactionSources: List<TransactionSource>
    )

    data class TransactionSource(
        val group: UtxoComponentGroup,
        val index: Int,
        val sourceTransactionId: String,
        val sourceIndex: Int
    )

    fun persistTransactionComponents(
        entityManager: EntityManager,
        transactionId: String,
        components: List<List<ByteArray>>,
        hash: (ByteArray) -> String
    )

    fun persistTransactionComponents(
        entityManager: EntityManager,
        components: List<TransactionComponent>,
        hash: (ByteArray) -> String
    )

    /** Persists transaction output (operation is idempotent) */
    fun persistVisibleTransactionOutputs(
        entityManager: EntityManager,
        transactionId: String,
        timestamp: Instant,
        visibleTransactionOutputs: List<VisibleTransactionOutput>
    )

    /** Persists transaction [signature] (operation is idempotent) */
    fun persistTransactionSignatures(
        entityManager: EntityManager,
        transactionId: String,
        signatures: List<TransactionSignature>,
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
    fun persistMerkleProofs(entityManager: EntityManager, merkleProofs: List<TransactionMerkleProof>)

    /** Persist a leaf index that belongs to a given merkle proof with ID [merkleProofId] */
    fun persistMerkleProofLeaves(
        entityManager: EntityManager,
        leaves: List<TransactionMerkleProofLeaf>
    )

    /** Find all the merkle proofs for a given list of transaction IDs */
    fun findMerkleProofs(
        entityManager: EntityManager,
        transactionIds: List<String>
    ): Map<String, List<MerkleProofDto>>

    /** Find filtered transactions with the given [ids] */
    fun findFilteredTransactions(
        entityManager: EntityManager,
        ids: List<String>
    ): Map<String, UtxoFilteredTransactionDto>

    data class TransactionComponent(val transactionId: String, val groupIndex: Int, val leafIndex: Int, val leafData: ByteArray)

    data class VisibleTransactionOutput(
        val stateIndex: Int,
        val className: String,
        val customRepresentation: CustomRepresentation,
        val token: UtxoToken?,
        val notaryName: String,
    )

    data class TransactionSignature(val index: Int, val signatureBytes: ByteArray, val publicKeyHash: SecureHash)

    data class TransactionMerkleProof(
        val transactionId: String,
        val groupIndex: Int,
        val treeSize: Int,
        val leafIndexes: List<Int>,
        val leafHashes: List<String>
    ) {
        val merkleProofId: String = "$transactionId;$groupIndex;${leafIndexes.joinToString(separator = ",")}"
    }

    data class TransactionMerkleProofLeaf(val merkleProofId: String, val leafIndex: Int)
}
