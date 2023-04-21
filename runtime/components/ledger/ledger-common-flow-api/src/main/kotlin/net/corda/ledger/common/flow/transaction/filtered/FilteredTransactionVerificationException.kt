package net.corda.ledger.common.flow.transaction.filtered

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

class FilteredTransactionVerificationException(id: SecureHash, reason: String) : CordaRuntimeException(
    "Failed to verify filtered transaction $id. Reason: $reason"
)