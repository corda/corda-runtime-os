package net.corda.uniqueness.datamodel.impl

import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorGeneral
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.application.uniqueness.model.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant

// TODO JSON de-/serialisation needs to be solved for these classes in the future

data class UniquenessCheckErrorInputStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorInputStateConflict

data class UniquenessCheckErrorInputStateUnknownImpl(
    override val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorInputStateUnknown

data class UniquenessCheckErrorReferenceStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorReferenceStateConflict

data class UniquenessCheckErrorReferenceStateUnknownImpl(
    override val unknownStates: List<UniquenessCheckStateRef>
) : UniquenessCheckErrorReferenceStateUnknown

data class UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
    override val evaluationTimestamp: Instant,
    override val timeWindowLowerBound: Instant?,
    override val timeWindowUpperBound: Instant
) : UniquenessCheckErrorTimeWindowOutOfBounds

data class UniquenessCheckErrorGeneralImpl(
    override val errorText: String
) : UniquenessCheckErrorGeneral

