package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorMalformedRequest
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant
import java.util.Collections.unmodifiableList

data class UniquenessCheckErrorInputStateConflictImpl(
    private val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorInputStateConflict {
    override fun getConflictingStates() = conflictingStates
}

data class UniquenessCheckErrorInputStateUnknownImpl(
    private val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorInputStateUnknown {
    override fun getUnknownStates() = unknownStates
}

data class UniquenessCheckErrorReferenceStateConflictImpl(
    private val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorReferenceStateConflict {
    override fun getConflictingStates(): List<UniquenessCheckStateDetails> = unmodifiableList(conflictingStates)
}

data class UniquenessCheckErrorReferenceStateUnknownImpl(
    private val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorReferenceStateUnknown {
    override fun getUnknownStates(): List<UniquenessCheckStateRef> = unmodifiableList(unknownStates)
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

data class UniquenessCheckErrorMalformedRequestImpl(
    private val errorText: String
) : UniquenessCheckErrorMalformedRequest {
    override fun getErrorText() = errorText
}
