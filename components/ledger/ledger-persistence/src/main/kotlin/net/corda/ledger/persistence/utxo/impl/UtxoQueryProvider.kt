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
     * @property findTransactionStatus SQL text for [UtxoRepositoryImpl.findTransactionStatus].
     */
    val findTransactionStatus: String

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
    val persistFilteredTransaction: String

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
     * @property findTransactionIdsAndStatuses SQL text for [UtxoRepositoryImpl.findTransactionIdsAndStatuses].
     */
    val findTransactionIdsAndStatuses: String

    /**
     * @property findMerkleProofs SQL text for [UtxoRepositoryImpl.findMerkleProofs].
     */
    val findMerkleProofs: String
}
