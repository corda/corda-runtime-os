package com.r3.corda.notary.plugin.common

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.uniqueness.data.UniquenessCheckStateDetails
import java.time.Instant

/**
 * This class contains implementation of error types that are common for plugins that use the uniqueness checker (e.g.
 * non-validating and validating notary plugins)
 */
@CordaSerializable
data class NotaryErrorInputStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : NotaryErrorInputStateConflict

@CordaSerializable
data class NotaryErrorInputStateUnknownImpl(
    override val unknownStates: List<StateRef>
) : NotaryErrorInputStateUnknown

@CordaSerializable
data class NotaryErrorReferenceStateConflictImpl(
    override val conflictingStates: List<UniquenessCheckStateDetails>
) : NotaryErrorReferenceStateConflict

@CordaSerializable
data class NotaryErrorReferenceStateUnknownImpl(
    override val unknownStates: List<StateRef>
) : NotaryErrorReferenceStateUnknown

@CordaSerializable
data class NotaryErrorTimeWindowOutOfBoundsImpl(
    override val evaluationTimestamp: Instant,
    override val timeWindowLowerBound: Instant?,
    override val timeWindowUpperBound: Instant
) : NotaryErrorTimeWindowOutOfBounds

@CordaSerializable
data class NotaryErrorMalformedRequestImpl(
    override val errorText: String
) : NotaryErrorMalformedRequest

@CordaSerializable
data class NotaryErrorGeneralImpl(
    override val errorText: String?,
    override val cause: Throwable? = null
) : NotaryErrorGeneral
