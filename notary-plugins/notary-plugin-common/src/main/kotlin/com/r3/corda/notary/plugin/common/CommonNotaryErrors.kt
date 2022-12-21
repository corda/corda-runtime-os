package com.r3.corda.notary.plugin.common

import net.corda.v5.ledger.notary.plugin.core.NotaryError
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import java.time.Instant

/**
 * This class contains error types that are common for plugins that use the uniqueness checker (e.g. non-validating and
 * validating notary plugins)
 */

/** Occurs when one or more input states have already been consumed by another transaction. */
interface NotaryErrorInputStateConflict : NotaryError {
    /** Specifies which states have already been consumed in another transaction. */
    val conflictingStates: List<UniquenessCheckStateDetails>
}

/** Occurs when one or more input states are not known to the uniqueness checker. */
interface NotaryErrorInputStateUnknown : NotaryError {
    /** Specifies which states are not known to the uniqueness checker. */
    val unknownStates: List<StateRef>
}

/** Occurs when one or more reference states have already been consumed by another transaction. */
interface NotaryErrorReferenceStateConflict : NotaryError {
    /** Specifies which reference states have already been consumed in another transaction. */
    val conflictingStates: List<UniquenessCheckStateDetails>
}

/** Occurs when one or more reference states are not known to the uniqueness checker. */
interface NotaryErrorReferenceStateUnknown : NotaryError {
    /** Specifies which states are not known to the uniqueness checker. */
    val unknownStates: List<StateRef>
}

/** Occurs when the specified time is outside the allowed tolerance. */
interface NotaryErrorTimeWindowOutOfBounds : NotaryError {
    val evaluationTimestamp: Instant
    val timeWindowLowerBound: Instant?
    val timeWindowUpperBound: Instant
}

/** Occurs when data in the received request is considered invalid by the uniqueness checker. */
interface NotaryErrorMalformedRequest : NotaryError {
    val errorText: String
}

/** Error type used for scenarios that were unexpected, or couldn't be mapped. */
interface NotaryErrorGeneral : NotaryError {
    val errorText: String?
    val cause: Throwable?
}
