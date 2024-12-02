package net.corda.ledger.libs.persistence.utxo

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
import java.sql.Connection
import java.time.Instant

@Suppress("TooManyFunctions")
interface UtxoRepository {

    /** Retrieves transaction IDs and their statuses if its ID is included in the [transactionIds] list. */
    fun findSignedTransactionIdsAndStatuses(
        connection: Connection,
        transactionIds: List<String>
    ): Map<SecureHash, String>

    /** Retrieves transaction by [id] */
    fun findTransaction(
        connection: Connection,
        id: String
    ): SignedTransactionContainer?

    /** Retrieves transaction component leafs except metadata which is stored separately */
    fun findTransactionComponentLeafs(
        connection: Connection,
        transactionId: String
    ): Map<Int, List<ByteArray>>

    /** Retrieves transaction component leaves related to visible unspent states and subclass states.*/
    fun findUnconsumedVisibleStatesByType(
        connection: Connection
    ): List<UtxoVisibleTransactionOutputDto>

    /** Retrieves transaction component leafs related to specific StateRefs */
    fun resolveStateRefs(
        connection: Connection,
        stateRefs: List<StateRef>
    ): List<UtxoVisibleTransactionOutputDto>

    /** Retrieves transaction signatures */
    fun findTransactionSignatures(
        connection: Connection,
        transactionId: String
    ): List<DigitalSignatureAndMetadata>

    /** Retrieves a transaction's status */
    fun findSignedTransactionStatus(
        connection: Connection,
        id: String,
    ): String?

    /** Marks visible states of transactions consumed */
    fun markTransactionVisibleStatesConsumed(
        connection: Connection,
        stateRefs: List<StateRef>,
        timestamp: Instant
    )

    /** Persists transaction (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistTransaction(
        connection: Connection,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        status: TransactionStatus,
        metadataHash: String
    ): Boolean

    /** Persists unverified transaction (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistUnverifiedTransaction(
        connection: Connection,
        id: String,
        privacySalt: ByteArray,
        account: String,
        timestamp: Instant,
        metadataHash: String,
    )

    /** Persists unverified transaction (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistFilteredTransactions(
        connection: Connection,
        filteredTransactions: List<FilteredTransaction>,
        timestamp: Instant,
    )

    /** Updates an existing verified transaction */
    fun updateTransactionToVerified(
        connection: Connection,
        id: String,
        timestamp: Instant
    )

    /** Persists transaction metadata (operation is idempotent) */
    fun persistTransactionMetadata(
        connection: Connection,
        hash: String,
        metadataBytes: ByteArray,
        groupParametersHash: String,
        cpiFileChecksum: String
    )

    /** Persists transaction source (operation is idempotent) */
    @Suppress("LongParameterList")
    fun persistTransactionSources(
        connection: Connection,
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
        connection: Connection,
        transactionId: String,
        components: List<List<ByteArray>>,
        hash: (ByteArray) -> String
    )

    fun persistTransactionComponents(
        connection: Connection,
        components: List<TransactionComponent>,
        hash: (ByteArray) -> String
    )

    /** Persists transaction output (operation is idempotent) */
    fun persistVisibleTransactionOutputs(
        connection: Connection,
        transactionId: String,
        timestamp: Instant,
        visibleTransactionOutputs: List<VisibleTransactionOutput>
    )

    /** Persists transaction [signature] (operation is idempotent) */
    fun persistTransactionSignatures(
        connection: Connection,
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
        connection: Connection,
        transactionId: String,
        transactionStatus: TransactionStatus,
        timestamp: Instant
    )

    /** Retrieves signed group parameters */
    fun findSignedGroupParameters(
        connection: Connection,
        hash: String
    ): SignedGroupParameters?

    /** Persists signed group parameters */
    fun persistSignedGroupParameters(
        connection: Connection,
        hash: String,
        signedGroupParameters: SignedGroupParameters,
        timestamp: Instant
    )

    /** Persists a merkle proof and returns its ID */
    fun persistMerkleProofs(connection: Connection, merkleProofs: List<TransactionMerkleProof>)

    /** Persist a leaf index that belongs to a given merkle proof with ID [merkleProofId] */
    fun persistMerkleProofLeaves(
        connection: Connection,
        leaves: List<TransactionMerkleProofLeaf>
    )

    /** Find all the merkle proofs for a given list of transaction IDs */
    fun findMerkleProofs(
        connection: Connection,
        transactionIds: List<String>
    ): Map<String, List<MerkleProofDto>>

    /** Find filtered transactions with the given [ids] */
    fun findFilteredTransactions(
        connection: Connection,
        ids: List<String>
    ): Map<String, UtxoFilteredTransactionDto>

    fun findConsumedTransactionSourcesForTransaction(
        connection: Connection,
        transactionId: String,
        indexes: List<Int>
    ): List<Int>

    fun findTransactionsWithStatusCreatedBetweenTime(
        connection: Connection,
        status: TransactionStatus,
        from: Instant,
        until: Instant,
        limit: Int,
    ): List<String>

    fun incrementTransactionRepairAttemptCount(connection: Connection, id: String)

    fun stateRefsExist(connection: Connection, stateRefs: List<StateRef>): List<Pair<String, Int>>

    data class TransactionComponent(val transactionId: String, val groupIndex: Int, val leafIndex: Int, val leafData: ByteArray)

    data class VisibleTransactionOutput(
        val stateIndex: Int,
        val className: String,
        val customRepresentation: CustomRepresentation,
        val token: UtxoToken?,
        val notaryName: String,
    )

    data class TransactionSignature(val transactionId: String, val signatureBytes: ByteArray, val publicKeyHash: SecureHash)

    data class FilteredTransaction(
        val transactionId: String,
        val privacySalt: ByteArray,
        val account: String,
        val metadataHash: String
    )

    data class TransactionMerkleProof(
        val merkleProofId: String,
        val transactionId: String,
        val groupIndex: Int,
        val treeSize: Int,
        val leafIndexes: List<Int>,
        val leafHashes: List<String>
    )

    data class TransactionMerkleProofLeaf(val merkleProofId: String, val leafIndex: Int)
}
