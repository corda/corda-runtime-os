package net.corda.uniqueness.datamodel.internal

import net.corda.v5.application.uniqueness.model.UniquenessCheckStateRef
import net.corda.v5.crypto.SecureHash
import java.time.Instant

/**
 * Internal representation of a uniqueness check request, used by the uniqueness checker and
 * backing store only. This simply wraps the external message bus request, converting data that
 * is represented as primitive types into the internal types used within the uniqueness checker.
 */
data class UniquenessCheckRequestInternal constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val originatorX500Name: String,
    val inputStates: List<UniquenessCheckStateRef>,
    val referenceStates: List<UniquenessCheckStateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
)