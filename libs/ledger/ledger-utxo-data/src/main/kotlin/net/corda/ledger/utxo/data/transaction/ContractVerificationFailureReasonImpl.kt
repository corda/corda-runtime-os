package net.corda.ledger.utxo.data.transaction

import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.ContractVerificationFailureReason

/**
 * Represents a contract verification failure reason.
 *
 * @property command The contract [Command] that caused the verification failure.
 * @property cause The underlying cause of the verification failure.
 */
data class ContractVerificationFailureReasonImpl(
    override val command: Class<out Command>,
    override val cause: Exception?,
) : ContractVerificationFailureReason {

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    override fun toString(): String {
        val message = if (cause?.message.isNullOrBlank()) "An unknown exception occurred." else cause!!.message
        return "${command.canonicalName}: $message."
    }
}
