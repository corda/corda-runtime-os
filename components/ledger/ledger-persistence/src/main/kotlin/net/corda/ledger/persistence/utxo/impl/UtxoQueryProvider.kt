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
     * @property findTransactionPrivacySalt SQL text for [UtxoRepositoryImpl.findTransactionPrivacySalt].
     */
    val findTransactionPrivacySalt: String

    /**
     * @property findTransactionComponentLeafs SQL text for [UtxoRepositoryImpl.findTransactionComponentLeafs].
     */
    val findTransactionComponentLeafs: String

    /**
     * @property findUnconsumedVisibleStatesByType SQL text for [UtxoRepositoryImpl.findUnconsumedVisibleStatesByType].
     */
    val findUnconsumedVisibleStatesByType: String

    /**
     * @property findUnconsumedVisibleStatesByExactType SQL text for [UtxoRepositoryImpl.findUnconsumedVisibleStatesByExactType].
     */
    val findUnconsumedVisibleStatesByExactType: String

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
     * @property persistTransactionComponentLeaf SQL text for [UtxoRepositoryImpl.persistTransactionComponentLeaf].
     */
    val persistTransactionComponentLeaf: String

    /**
     * @property persistTransactionCpk SQL text for [UtxoRepositoryImpl.persistTransactionCpk].
     */
    val persistTransactionCpk: String

    /**
     * @param consumed Whether the persisted states have been consumed.
     * @property persistTransactionOutput SQL text for [UtxoRepositoryImpl.persistTransactionOutput].
     */
    fun persistTransactionOutput(consumed: Boolean): String

    /**
     * @property persistTransactionSignature SQL text for [UtxoRepositoryImpl.persistTransactionSignature].
     */
    val persistTransactionSignature: String

    /**
     * @property persistTransactionSource SQL text for [UtxoRepositoryImpl.persistTransactionSource].
     */
    val persistTransactionSource: String

    /**
     * @property updateTransactionStatus SQL text for [UtxoRepositoryImpl.updateTransactionStatus].
     */
    val updateTransactionStatus: String

    /**
     * @property persistSignedGroupParameters SQL text for [UtxoRepositoryImpl.persistSignedGroupParameters].
     */
    val persistSignedGroupParameters: String
}
