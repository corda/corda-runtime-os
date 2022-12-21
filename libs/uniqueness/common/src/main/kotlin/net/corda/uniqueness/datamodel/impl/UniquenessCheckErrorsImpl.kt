package net.corda.uniqueness.datamodel.impl

import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorInputStateConflict
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorInputStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorMalformedRequest
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorReferenceStateConflict
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorReferenceStateUnknown
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckErrorTimeWindowOutOfBounds
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import java.time.Instant

data class UniquenessCheckErrorInputStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorInputStateConflict

data class UniquenessCheckErrorInputStateUnknownImpl(
    override val unknownStates: List<StateRef>
) : UniquenessCheckErrorInputStateUnknown

data class UniquenessCheckErrorReferenceStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : UniquenessCheckErrorReferenceStateConflict

data class UniquenessCheckErrorReferenceStateUnknownImpl(
    override val unknownStates: List<StateRef>
) : UniquenessCheckErrorReferenceStateUnknown

data class UniquenessCheckErrorTimeWindowOutOfBoundsImpl(
    override val evaluationTimestamp: Instant,
    override val timeWindowLowerBound: Instant?,
    override val timeWindowUpperBound: Instant
) : UniquenessCheckErrorTimeWindowOutOfBounds

data class UniquenessCheckErrorMalformedRequestImpl(
    override val errorText: String
) : UniquenessCheckErrorMalformedRequest
