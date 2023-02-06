package net.corda.v5.ledger.common.transaction

import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.crypto.SecureHash

/**
 * TransactionWithMetadata contains metadata properties of transactions common across different ledger implementations.
 *
 * * @property signature The signature that was applied.
 */
@DoNotImplement
interface TransactionWithMetadata {
    /**
     * @property id The ID of the transaction.
     */
    val id: SecureHash

    /**
     * @property metadata The metadata for this transaction.
     */
    val metadata: TransactionMetadata
}