package net.corda.ledger.persistence.utxo.impl

/**
 * Provider for the SQL queries executed by [UtxoRepositoryImpl].
 * We should use ANSI SQL, whenever possible, in which case the SQL
 * text should be added to [AbstractUtxoQueryProvider] so that
 * it can be shared across all [UtxoQueryProvider] implementations.
 *
 * DBMS-specific SQL must be written for each concrete [UtxoQueryProvider],
 * e.g. [PostgresUtxoQueryProvider].
 */
interface UtxoQueryProvider {
    /**
     * @property findTransactionsPrivacySaltAndMetadata SQL text for [UtxoRepositoryImpl.findTransactionsPrivacySaltAndMetadata].
     */
    val findTransactionsPrivacySaltAndMetadata: String

    /**
     * @property findTransactionComponentLeafs SQL text for [UtxoRepositoryImpl.findTransactionComponentLeafs].
     */
    val findTransactionComponentLeafs: String

    /**
     * @property findUnconsumedVisibleStatesByType SQL text for [UtxoRepositoryImpl.findUnconsumedVisibleStatesByType].
     */
    val findUnconsumedVisibleStatesByType: String

    /**
     * @property findTransactionSignatures SQL text for [UtxoRepositoryImpl.findTransactionSignatures].
     */
    val findTransactionSignatures: String

    /**
     * @property findSignedTransactionStatus SQL text for [UtxoRepositoryImpl.findSignedTransactionStatus].
     *
     * VERIFIED, UNVERIFIED, DRAFT and INVALID are all returned where `is_filtered` = false.
     *
     * Where `is_filtered` = true, the following rules apply:
     *
     * - VERIFIED can exist with is_filtered = true when there is only a filtered transaction => not returned.
     * - UNVERIFIED can exist with is_filtered = true when there is an unverified signed and filtered transaction => returned.
     * - DRAFT cannot exist with is_filtered = true => doesn't exist.
     * - INVALID filtered transaction => doesn't exist.
     */
    val findSignedTransactionStatus: String

    /**
     * @property markTransactionVisibleStatesConsumed SQL text for
     * [UtxoRepositoryImpl.markTransactionVisibleStatesConsumed].
     */
    val markTransactionVisibleStatesConsumed: String

    /**
     * @property findSignedGroupParameters SQL text for [UtxoRepositoryImpl.findSignedGroupParameters].
     */
    val findSignedGroupParameters: String

    /**
     * @property resolveStateRefs SQL text for [UtxoRepositoryImpl.resolveStateRefs].
     */
    val resolveStateRefs: String

    /**
     * @property stateRefsExist SQL text for [UtxoRepositoryImpl.stateRefsExist].
     */
    val stateRefsExist: (batchSize: Int) -> String

    /**
     * @property persistTransaction SQL text for [UtxoRepositoryImpl.persistTransaction].
     */
    val persistTransaction: String

    /**
     * @property persistUnverifiedTransaction SQL text for [UtxoRepositoryImpl.persistUnverifiedTransaction].
     */
    val persistUnverifiedTransaction: String

    /**
     * @property persistFilteredTransaction SQL text for [UtxoRepositoryImpl.persistFilteredTransaction].
     */
    val persistFilteredTransaction: (batchSize: Int) -> String

    /**
     * @property persistTransactionMetadata SQL text for [UtxoRepositoryImpl.persistTransactionMetadata].
     */
    val persistTransactionMetadata: String

    /**
     * @property persistTransactionSource SQL text for [UtxoRepositoryImpl.persistTransactionSource].
     */
    val persistTransactionSources: (batchSize: Int) -> String

    /**
     * @property persistTransactionComponentLeaf SQL text for [UtxoRepositoryImpl.persistTransactionComponentLeaf].
     */
    val persistTransactionComponents: (batchSize: Int) -> String

    /**
     * @param consumed Whether the persisted states have been consumed.
     * @property persistVisibleTransactionOutput SQL text for [UtxoRepositoryImpl.persistVisibleTransactionOutputs].
     */
    val persistVisibleTransactionOutputs: (batchSize: Int) -> String

    /**
     * @property persistTransactionSignature SQL text for [UtxoRepositoryImpl.persistTransactionSignature].
     */
    val persistTransactionSignatures: (batchSize: Int) -> String

    /**
     * @property persistMerkleProof SQL text for [UtxoRepositoryImpl.persistMerkleProof].
     */
    val persistMerkleProofs: (batchSize: Int) -> String

    /**
     * @property persistMerkleProofLeaf SQL text for [UtxoRepositoryImpl.persistMerkleProofLeaf]
     */
    val persistMerkleProofLeaves: (batchSize: Int) -> String

    /**
     * @property updateTransactionStatus SQL text for [UtxoRepositoryImpl.updateTransactionStatus].
     */
    val updateTransactionStatus: String

    /**
     * @property persistSignedGroupParameters SQL text for [UtxoRepositoryImpl.persistSignedGroupParameters].
     */
    val persistSignedGroupParameters: String

    /**
     * @property findSignedTransactionIdsAndStatuses SQL text for [UtxoRepositoryImpl.findSignedTransactionIdsAndStatuses].
     *
     * VERIFIED, UNVERIFIED, DRAFT and INVALID are all returned where `is_filtered` = false.
     *
     * Where `is_filtered` = true, the following rules apply:
     *
     * - VERIFIED can exist with is_filtered = true when there is only a filtered transaction => not returned.
     * - UNVERIFIED can exist with is_filtered = true when there is an unverified signed and filtered transaction => returned.
     * - DRAFT cannot exist with is_filtered = true => doesn't exist.
     * - INVALID filtered transaction => doesn't exist.
     */
    val findSignedTransactionIdsAndStatuses: String

    /**
     * @property findMerkleProofs SQL text for [UtxoRepositoryImpl.findMerkleProofs].
     */
    val findMerkleProofs: String

    val findConsumedTransactionSourcesForTransaction: String

    val updateTransactionToVerified: String

    val findTransactionsWithStatusCreatedBetweenTime: String

    val incrementRepairAttemptCount: String
}
