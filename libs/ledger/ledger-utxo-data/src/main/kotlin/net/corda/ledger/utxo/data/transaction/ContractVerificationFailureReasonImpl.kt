package net.corda.ledger.utxo.data.transaction

import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.ContractVerificationFailure

/**
 * Represents a contract verification failure.
 *
 * @property contractClassName The class name of the [Contract] that caused verification failure.
 * @property contractStateClassNames The class names of the contract states that import the [Contract] that caused verification failure.
 * @property exceptionClassName The class name of the [Exception] that caused verification failure.
 * @property exceptionMessage The details of the [Exception] that caused verification failure.
 */
data class ContractVerificationFailureReasonImpl(
    override val contractClassName: String,
    override val contractStateClassNames: List<String>,
    override val exceptionClassName: String,
    override val exceptionMessage: String,
) : ContractVerificationFailure {

    /**
     * Returns a string that represents the current object.
     *
     * @return Returns a string that represents the current object.
     */
    override fun toString(): String {
        return "$contractClassName: $exceptionMessage"
    }
}
