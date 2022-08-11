package net.corda.uniqueness.common.datamodel

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.corda.v5.base.annotations.CordaSerializable
import java.time.Instant

/**
 * Internal representation of errors that can be raised by the uniqueness checker, used by the
 * uniqueness checker and backing store only. This representation is agnostic to both the message
 * bus API and any DB schema that may be used to persist data by the backing store.
 *
 *  Unfortunately since this is a sealed class with multiple mplementations we need to tell
 *  Jackson what types to look for. Hence the annotations. This will be removed when we start
 *  using standard Corda serialization
 */
@CordaSerializable
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = UniquenessCheckInternalError.InputStateConflict::class),
    JsonSubTypes.Type(value = UniquenessCheckInternalError.InputStateUnknown::class),
    JsonSubTypes.Type(value = UniquenessCheckInternalError.ReferenceStateConflict::class),
    JsonSubTypes.Type(value = UniquenessCheckInternalError.ReferenceStateUnknown::class),
    JsonSubTypes.Type(value = UniquenessCheckInternalError.TimeWindowOutOfBounds::class),
)
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
