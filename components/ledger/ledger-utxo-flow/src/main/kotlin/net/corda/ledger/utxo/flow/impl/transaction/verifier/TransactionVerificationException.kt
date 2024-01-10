package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.ledger.utxo.data.transaction.TransactionVerificationStatus
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

class TransactionVerificationException(
    val transactionId: SecureHash,
    val status: TransactionVerificationStatus,
    originalExceptionClassName: String? = null,
    originalMessage: String? = null
) : CordaRuntimeException(
    originalExceptionClassName,
    "Verification of ledger transaction with ID $transactionId failed: $originalExceptionClassName: $originalMessage",
    null
)