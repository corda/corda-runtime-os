package com.r3.corda.notary.plugin.common

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateDetails
import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorGeneral
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorInputStateConflict
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorInputStateUnknown
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorMalformedRequest
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorReferenceStateConflict
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorReferenceStateUnknown
import net.corda.v5.ledger.notary.plugin.core.NotaryErrorTimeWindowOutOfBounds
import java.time.Instant

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

// TODO CORE-7249 Extend with more errors once FilteredTransaction support has been added

