package net.corda.ledger.persistence.consensual.impl

/**
 * Provider for the SQL queries executed by [ConsensualRepositoryImpl].
 * We should use ANSI SQL, whenever possible, in which case the SQL
 * text should be added to [AbstractConsensualQueryProvider] so that
 * it can be shared across all [ConsensualQueryProvider] implementations.
 *
 * DBMS-specific SQL must be written for each concrete [ConsensualQueryProvider],
 * e.g. [PostgresConsensualQueryProvider].
 */
interface ConsensualQueryProvider {
    /**
     * @property findTransaction SQL text for [ConsensualRepositoryImpl.findTransaction].
     */
    val findTransaction: String

    /**
     * @property findTransactionCpkChecksums SQL text for [ConsensualRepositoryImpl.findTransaction].
     */
    val findTransactionCpkChecksums: String

    /**
     * @property findTransactionSignatures SQL text for [ConsensualRepositoryImpl.findTransactionSignatures].
     */
    val findTransactionSignatures: String

    /**
     * @property persistTransaction SQL text for [ConsensualRepositoryImpl.persistTransaction].
     */
    val persistTransaction: String

    /**
     * @property persistTransactionComponentLeaf SQL text for
     * [ConsensualRepositoryImpl.persistTransactionComponentLeaf].
     */
    val persistTransactionComponentLeaf: String

    /**
     * @property persistTransactionStatus SQL text for [ConsensualRepositoryImpl.persistTransactionStatus].
     */
    val persistTransactionStatus: String

    /**
     * @property persistTransactionSignature SQL text for [ConsensualRepositoryImpl.persistTransactionSignature].
     */
    val persistTransactionSignature: String

    /**
     * @property persistTransactionCpk SQL text for [ConsensualRepositoryImpl.persistTransactionCpk].
     */
    val persistTransactionCpk: String
}
