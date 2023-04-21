package com.r3.corda.notary.plugin.common

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionFatal
import net.corda.v5.ledger.notary.plugin.core.NotaryExceptionUnknown
import java.time.Instant

/**
 * This class contains error types that are common for plugins that use the uniqueness checker (e.g. non-validating and
 * validating notary plugins)
 */

/**
 * Occurs when one or more input states have already been consumed by another transaction.
 *
 * @property conflictingStates Specifies which states have already been consumed in another transaction.
 */
@CordaSerializable
class NotaryExceptionInputStateConflict(
    val conflictingStates: List<UniquenessCheckStateDetails>,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Input State Conflict(s): $conflictingStates",
    txId
)

/**
 * Occurs when one or more input states are not known to the uniqueness checker.
 *
 * @property unknownStates Specifies which states are not known to the uniqueness checker.
 */
@CordaSerializable
class NotaryExceptionInputStateUnknown(
    val unknownStates: List<UniquenessCheckStateRef>,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Unknown Input State(s): $unknownStates",
    txId
)

/**
 * Occurs when one or more reference states have already been consumed by another transaction.
 *
 * @property conflictingStates Specifies which reference states have already been consumed in another transaction.
 */
@CordaSerializable
class NotaryExceptionReferenceStateConflict(
    val conflictingStates: List<UniquenessCheckStateDetails>,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Reference State Conflict(s): $conflictingStates",
    txId
)

/**
 * Occurs when one or more reference states are not known to the uniqueness checker.
 *
 * @property unknownStates Specifies which states are not known to the uniqueness checker.
 */
@CordaSerializable
class NotaryExceptionReferenceStateUnknown(
    val unknownStates: List<UniquenessCheckStateRef>,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Unknown Reference State(s): $unknownStates",
    txId
)

/**
 * Occurs when the specified time is outside the allowed tolerance.
 *
 * @property evaluationTimestamp Specifies the timestamp when the transaction was evaluated
 * @property timeWindowLowerBound Specifies the upper bound of the transaction's time window
 * @property timeWindowUpperBound Specifies the lower bound of the transaction's time window
 */
@CordaSerializable
class NotaryExceptionTimeWindowOutOfBounds(
    val evaluationTimestamp: Instant,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Time Window Out of Bounds. " +
            "Evaluated at $evaluationTimestamp, upper bound: $timeWindowUpperBound, lower bound: $timeWindowLowerBound",
    txId
)

/**
 * Occurs when data in the received request is considered invalid by the uniqueness checker.
 *
 * @property errorText The error text produced by the uniqueness checker
 */
@CordaSerializable
class NotaryExceptionMalformedRequest(
    val errorText: String,
    txId: SecureHash? = null
) : NotaryExceptionFatal(
    "Malformed Request received by the uniqueness checker: $errorText",
    txId
)

/**
 * Error type used for scenarios that were unexpected, or couldn't be mapped.
 *
 * @property errorText Any additional details regarding the error
 */
@CordaSerializable
class NotaryExceptionGeneral(
    val errorText: String?,
    txId: SecureHash? = null
) : NotaryExceptionUnknown(
    "General Error: $errorText",
    txId
)
