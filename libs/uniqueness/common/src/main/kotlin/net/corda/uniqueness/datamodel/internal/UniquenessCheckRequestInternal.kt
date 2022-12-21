package net.corda.uniqueness.datamodel.internal

import net.corda.data.uniqueness.UniquenessCheckRequestAvro
import net.corda.uniqueness.datamodel.common.toStateRef
import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.StateRef
import java.time.Instant

/**
 * Internal representation of a uniqueness check request, used by the uniqueness checker and
 * backing store only. This simply wraps the external message bus request, converting data that
 * is represented as primitive types into the internal types used within the uniqueness checker.
 */
data class UniquenessCheckRequestInternal constructor(
    val txId: SecureHash,
    val rawTxId: String,
    val inputStates: List<StateRef>,
    val referenceStates: List<StateRef>,
    val numOutputStates: Int,
    val timeWindowLowerBound: Instant?,
    val timeWindowUpperBound: Instant
) {
    companion object {
        fun create(externalRequest: UniquenessCheckRequestAvro): UniquenessCheckRequestInternal {
            if (externalRequest.numOutputStates < 0) {
                throw IllegalArgumentException("Number of output states cannot be less than 0.")
            }

            with (externalRequest) {
                return UniquenessCheckRequestInternal(
                    SecureHash.parse(txId),
                    txId,
                    inputStates?.map { it.toStateRef() } ?: emptyList(),
                    referenceStates?.map { it.toStateRef() } ?: emptyList(),
                    numOutputStates,
                    timeWindowLowerBound,
                    timeWindowUpperBound
                )
            }
        }
    }
}
