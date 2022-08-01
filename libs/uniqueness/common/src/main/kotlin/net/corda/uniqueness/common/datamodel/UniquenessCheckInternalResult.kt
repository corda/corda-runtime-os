package net.corda.uniqueness.common.datamodel

import net.corda.data.uniqueness.*
import org.apache.avro.specific.SpecificRecord
import java.time.Instant

/**
 * Internal representation of the result of a uniqueness check request, used by the uniqueness
 * checker and backing store only. This representation is agnostic to both the message bus API
 * and any DB schema that may be used to persist data by the backing store.
 */
sealed class UniquenessCheckInternalResult(val commitTimestamp: Instant) {
    companion object {
        const val RESULT_ACCEPTED_REPRESENTATION = 'A'
        const val RESULT_REJECTED_REPRESENTATION = 'R'
    }

    class Success(commitTimestamp: Instant) : UniquenessCheckInternalResult(commitTimestamp) {
        override fun toCharacterRepresentation(): Char = RESULT_ACCEPTED_REPRESENTATION
    }

    class Failure(
        commitTimestamp: Instant,
        val error: UniquenessCheckInternalError
        ) : UniquenessCheckInternalResult(commitTimestamp) {
        override fun toCharacterRepresentation(): Char = RESULT_REJECTED_REPRESENTATION

        /**
         * Converts the failure to the external Avro error
         */
        fun toExternalError(): SpecificRecord {
            return when (error) {
                is UniquenessCheckInternalError.InputStateConflict ->
                    UniquenessCheckResultInputStateConflict(
                        error.conflictingStates.map { it.stateRef.toString() })
                is UniquenessCheckInternalError.InputStateUnknown ->
                    UniquenessCheckResultInputStateUnknown(
                        error.unknownStates.map { it.toString() })
                is UniquenessCheckInternalError.ReferenceStateConflict ->
                    UniquenessCheckResultReferenceStateConflict(
                        error.conflictingStates.map { it.stateRef.toString() })
                is UniquenessCheckInternalError.ReferenceStateUnknown ->
                    UniquenessCheckResultReferenceStateUnknown(
                        error.unknownStates.map { it.toString() })
                is UniquenessCheckInternalError.TimeWindowOutOfBounds ->
                    with(error) {
                        UniquenessCheckResultTimeWindowOutOfBounds(
                            evaluationTimestamp,
                            timeWindowLowerBound,
                            timeWindowUpperBound
                        )
                    }
            }
        }
    }

    abstract fun toCharacterRepresentation(): Char
}
