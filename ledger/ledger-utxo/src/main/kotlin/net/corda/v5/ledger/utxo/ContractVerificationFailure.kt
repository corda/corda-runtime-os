package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Defines a contract verification failure.
 *
 * @property contractClassName The class name of the [Contract] that caused verification failure.
 * @property contractStateClassNames The class names of the contract states that import the [Contract] that caused verification failure.
 * @property exceptionClassName The class name of the [Exception] that caused verification failure.
 * @property exceptionMessage The details of the [Exception] that caused verification failure.
 */
@CordaSerializable
@DoNotImplement
interface ContractVerificationFailure {
    val contractClassName: String
    val contractStateClassNames: List<String>
    val exceptionClassName: String
    val exceptionMessage: String
}
