package net.corda.uniqueness.datamodel

import java.time.Instant

/**
 * Internal representation of the result of a uniqueness check request, used by the uniqueness
 * checker and backing store only. This representation is agnostic to both the message bus API
 * and any DB schema that may be used to persist data by the backing store.
 */
sealed class UniquenessCheckInternalResult {
    companion object {
        const val RESULT_ACCEPTED_REPRESENTATION = 'A'
        const val RESULT_REJECTED_REPRESENTATION = 'R'
    }

    data class Success(val commitTimestamp: Instant) : UniquenessCheckInternalResult() {
        override fun toCharacterRepresentation(): Char = RESULT_ACCEPTED_REPRESENTATION
    }

    data class Failure(val error: UniquenessCheckInternalError) : UniquenessCheckInternalResult() {
        override fun toCharacterRepresentation(): Char = RESULT_REJECTED_REPRESENTATION
    }

    abstract fun toCharacterRepresentation(): Char
}
