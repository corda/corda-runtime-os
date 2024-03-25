package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorNotPreviouslySeenTransaction
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowBeforeLowerBound
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorUnhandledException
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant
import java.util.Collections.unmodifiableList

// NOTE: All state based errors are currently capped to only return the details of the first state,
// since the size of each one in the DB is large and more than one conflicting state exceeds the
// 1024 storage limit. This will be fixed properly by CORE-17155
data class UniquenessCheckErrorInputStateConflictImpl(
    private val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorInputStateConflict {
    override fun getConflictingStates() : List<UniquenessCheckStateDetails> =
        unmodifiableList(listOf(conflictingStates.first()))
}

data class UniquenessCheckErrorInputStateUnknownImpl(
    private val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorInputStateUnknown {
    override fun getUnknownStates() : List<UniquenessCheckStateRef> =
        unmodifiableList(listOf(unknownStates.first()))
}

data class UniquenessCheckErrorReferenceStateConflictImpl(
    private val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorReferenceStateConflict {
    override fun getConflictingStates(): List<UniquenessCheckStateDetails> =
        unmodifiableList(listOf(conflictingStates.first()))
}

data class UniquenessCheckErrorReferenceStateUnknownImpl(
    private val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorReferenceStateUnknown {
    override fun getUnknownStates(): List<UniquenessCheckStateRef> =
        unmodifiableList(listOf(unknownStates.first()))
}

data class UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
    private val evaluationTimestamp: Instant,
    private val timeWindowLowerBound: Instant?,
    private val timeWindowUpperBound: Instant
) : UniquenessCheckErrorTimeWindowOutOfBounds {
    override fun getEvaluationTimestamp() = evaluationTimestamp
    override fun getTimeWindowLowerBound() = timeWindowLowerBound
    override fun getTimeWindowUpperBound() = timeWindowUpperBound
}

data class UniquenessCheckErrorTimeWindowBeforeLowerBoundImpl(
    private val evaluationTimestamp: Instant,
    private val timeWindowLowerBound: Instant,
) : UniquenessCheckErrorTimeWindowBeforeLowerBound {
    override fun getEvaluationTimestamp() = evaluationTimestamp
    override fun getTimeWindowLowerBound() = timeWindowLowerBound
}

data class UniquenessCheckErrorMalformedRequestImpl(
    private val errorText: String
) : UniquenessCheckErrorMalformedRequest {
    override fun getErrorText() = errorText
}

data class UniquenessCheckErrorUnhandledExceptionImpl(
    private val unhandledExceptionType: String,
    private val unhandledExceptionMessage: String
) : UniquenessCheckErrorUnhandledException {
    override fun getUnhandledExceptionType(): String = unhandledExceptionType
    override fun getUnhandledExceptionMessage(): String = unhandledExceptionMessage
}

object UniquenessCheckErrorNotPreviouslySeenTransactionImpl: UniquenessCheckErrorNotPreviouslySeenTransaction