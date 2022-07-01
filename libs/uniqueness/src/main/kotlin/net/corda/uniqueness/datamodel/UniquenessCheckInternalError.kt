package net.corda.uniqueness.datamodel

import net.corda.data.crypto.SecureHash
import net.corda.v5.ledger.contracts.StateRef
import net.corda.v5.ledger.contracts.TimeWindow
import net.corda.v5.ledger.notary.StateConsumptionDetails
import java.time.Instant

/**
 * Internal representation of errors that can be raised by the uniqueness checker, used by the
 * uniqueness checker and backing store only. This representation is agnostic to both the message
 * bus API and any DB schema that may be used to persist data by the backing store.
 */
sealed class UniquenessCheckInternalError {
    companion object {
        const val NUM_STATES = 5
    }

    /** Occurs when one or more reference states have already been consumed by another transaction. */
    data class ReferenceStateConflict(
        /** Id of the transaction that was attempted to be notarised. */
        val txId: SecureHash,
        /** Specifies which reference states have already been consumed in another transaction. */
        val consumedStates: Map<StateRef, StateConsumptionDetails>
    ) : UniquenessCheckInternalError() {
        override fun toString() = "One or more referenced states have already been used as input states in other transactions. " +
                "Conflicting state count: ${consumedStates.size}, consumption details:\n" +
                "${consumedStates.asSequence().joinToString(",\n", limit = NUM_STATES) { it.key.toString() + " -> " + it.value }}.\n"
    }

    /** Occurs when one or more input states have already been consumed by another transaction. */
    data class InputStateConflict(
        /** Id of the transaction that was attempted to be notarised. */
        val txId: SecureHash,
        /** Specifies which states have already been consumed in another transaction. */
        val consumedStates: Map<StateRef, StateConsumptionDetails>
    ) : UniquenessCheckInternalError() {
        override fun toString() = "One or more input states have already been used as input states in other transactions. " +
                "Conflicting state count: ${consumedStates.size}, consumption details:\n" +
                "${consumedStates.asSequence().joinToString(",\n", limit = NUM_STATES) { it.key.toString() + " -> " + it.value }}.\n"
    }

    data class TimeWindowMissing(
        /** Id of the transaction that was attempted to be notarised. */
        val txId: SecureHash
    ) : UniquenessCheckInternalError() {
        override fun toString() = "The transaction with id: $txId has no time window provided."
    }

    data class UnknownInputState(
        /** Id of the transaction that was attempted to be notarised. */
        val txId: SecureHash
    ) : UniquenessCheckInternalError() {
        override fun toString() = "The transaction with id: $txId is trying to consume states that are not known."
    }

    data class UnknownReferenceState(
        /** Id of the transaction that was attempted to be notarised. */
        val txId: SecureHash
    ) : UniquenessCheckInternalError() {
        override fun toString() = "The transaction with id: $txId is trying to consume references that are not known."
    }

    /** Occurs when time specified in the [TimeWindow] command is outside the allowed tolerance. */
    data class TimeWindowInvalid(
        val currentTime: Instant,
        val txTimeWindow: TimeWindow
    ) : UniquenessCheckInternalError() {
        override fun toString() = "Current time $currentTime is outside the time bounds specified by the transaction: $txTimeWindow"
    }

    /** Occurs when the provided transaction fails to verify. */
    data class TransactionInvalid(
        val cause: Throwable
    ) : UniquenessCheckInternalError() {
        override fun toString() = cause.toString()
    }

    /** Occurs when the notarisation request signature does not verify for the provided transaction. */
    data class RequestSignatureInvalid(
        val cause: Throwable
    ) : UniquenessCheckInternalError() {
        override fun toString() = "Request signature invalid: $cause"
    }

    /** Occurs when the notary service encounters an unexpected issue or becomes temporarily unavailable. */
    data class General(
        val cause: Throwable
    ) : UniquenessCheckInternalError() {
        override fun toString() = cause.toString()
    }
}
