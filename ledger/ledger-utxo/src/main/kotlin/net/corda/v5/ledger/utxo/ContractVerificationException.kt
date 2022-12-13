package net.corda.v5.ledger.utxo

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.crypto.SecureHash

class ContractVerificationException(
    val transactionId: SecureHash,
    val failureReasons: List<ContractVerificationFailure>
) : CordaRuntimeException(buildString {
    appendLine("Ledger transaction contract verification failed for the specified transaction: $transactionId.")
    appendLine("The following contract verification requirements were not met:")
    appendLine(failureReasons.joinToString("\n"))
})