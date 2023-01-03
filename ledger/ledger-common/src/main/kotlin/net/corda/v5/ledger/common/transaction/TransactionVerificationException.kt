package net.corda.v5.ledger.common.transaction

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

/**
 * Indicates that some aspect of the transaction named by [txId] violates some rules.
 *
 * @property txId the Merkle root hash (identifier) of the transaction that failed verification.
 */
open class TransactionVerificationException(val txId: SecureHash, message: String, cause: Throwable?)
    : CordaRuntimeException(message, cause)