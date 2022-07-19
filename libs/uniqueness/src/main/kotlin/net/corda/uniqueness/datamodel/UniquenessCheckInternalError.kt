package net.corda.uniqueness.datamodel

import java.time.Instant

/**
 * Internal representation of errors that can be raised by the uniqueness checker, used by the
 * uniqueness checker and backing store only. This representation is agnostic to both the message
 * bus API and any DB schema that may be used to persist data by the backing store.
 */
sealed class UniquenessCheckInternalError {
    /** Occurs when one or more input states have already been consumed by another transaction. */
    data class InputStateConflict(
        /** Specifies which states have already been consumed in another transaction. */
        val conflictingStates: List<UniquenessCheckInternalStateDetails>
    ) : UniquenessCheckInternalError()

    /** Occurs when one or more input states are not known to the uniqueness checker. */
    data class InputStateUnknown(
        /** Specifies which states are not known to the uniqueness checker. */
        val unknownStates: List<UniquenessCheckInternalStateRef>
    ) : UniquenessCheckInternalError()

    /** Occurs when one or more reference states have already been consumed by another transaction. */
    data class ReferenceStateConflict(
        /** Specifies which reference states have already been consumed in another transaction. */
        val conflictingStates: List<UniquenessCheckInternalStateDetails>
    ) : UniquenessCheckInternalError()

    /** Occurs when one or more reference states are not known to the uniqueness checker. */
    data class ReferenceStateUnknown(
        /** Specifies which states are not known to the uniqueness checker. */
        val unknownStates: List<UniquenessCheckInternalStateRef>
    ) : UniquenessCheckInternalError()

    /** Occurs when the specified time is outside the allowed tolerance. */
    data class TimeWindowOutOfBounds(
        val evaluationTimestamp: Instant,
        val timeWindowLowerBound: Instant?,
        val timeWindowUpperBound: Instant
    ) : UniquenessCheckInternalError()
}
