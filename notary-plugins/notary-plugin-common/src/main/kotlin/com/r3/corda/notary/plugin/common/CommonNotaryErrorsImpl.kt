package com.r3.corda.notary.plugin.common

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import java.time.Instant

/**
 * This class contains implementation of error types that are common for plugins that use the uniqueness checker (e.g.
 * non-validating and validating notary plugins)
 */
data class NotaryErrorInputStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : NotaryErrorInputStateConflict

data class NotaryErrorInputStateUnknownImpl(
    override val unknownStates: List<UniquenessCheckStateRef>
) : NotaryErrorInputStateUnknown

data class NotaryErrorReferenceStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : NotaryErrorReferenceStateConflict

data class NotaryErrorReferenceStateUnknownImpl(
    override val unknownStates: List<UniquenessCheckStateRef>
) : NotaryErrorReferenceStateUnknown

data class NotaryErrorTimeWindowOutOfBoundsImpl(
    override val evaluationTimestamp: Instant,
    override val timeWindowLowerBound: Instant?,
    override val timeWindowUpperBound: Instant
) : NotaryErrorTimeWindowOutOfBounds

data class NotaryErrorMalformedRequestImpl(
    override val errorText: String
) : NotaryErrorMalformedRequest

data class NotaryErrorGeneralImpl(
    override val errorText: String
) : NotaryErrorGeneral
