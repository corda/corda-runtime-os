package net.corda.uniqueness.datamodel

import net.corda.data.uniqueness.UniquenessCheckRequest
import java.lang.IllegalArgumentException
import java.time.Instant
import kotlin.jvm.Throws

/**
 * Internal representation of a uniqueness check request, used by the uniqueness checker and
 * backing store only. This simply wraps the external message bus request, converting data that
 * is represented as primitive types into the internal types used within the uniqueness checker.
 */
class UniquenessCheckInternalRequest @Throws(IllegalArgumentException::class) constructor(
    externalRequest: UniquenessCheckRequest
) {
    val txId: UniquenessCheckInternalTxHash = externalRequest.txId.toInternalTxHash()

    val rawTxId: String = externalRequest.txId

    val inputStates: List<UniquenessCheckInternalStateRef> =
        externalRequest.inputStates?.map { it.toInternalStateRef() } ?: emptyList()

    val referenceStates: List<UniquenessCheckInternalStateRef> =
        externalRequest.referenceStates?.map { it.toInternalStateRef() } ?: emptyList()

    val numOutputStates: Int = if (externalRequest.numOutputStates < 0) {
        throw IllegalArgumentException("Number of output states cannot be less than 0.")
    } else {
        externalRequest.numOutputStates
    }

    val timeWindowLowerBound: Instant? by externalRequest::timeWindowLowerBound

    val timeWindowUpperBound: Instant by externalRequest::timeWindowUpperBound

    private fun String.toInternalTxHash(): UniquenessCheckInternalTxHash {
        val components = this.split(':')

        if (components.size != 2 || !isHexString(components[1]))
            throw IllegalArgumentException("Transaction id $txId is invalid")

        return UniquenessCheckInternalTxHash(components[0], components[1])
    }

    private fun String.toInternalStateRef(): UniquenessCheckInternalStateRef {
        val components = this.split(':')

        if (components.size != 3 ||
            !isHexString(components[1]) ||
            !components[2].all { Character.isDigit(it) }
        ) {
            throw IllegalArgumentException("Transaction id $txId is invalid")
        }

        return UniquenessCheckInternalStateRef(
            UniquenessCheckInternalTxHash(components[0], components[1]),
            components[2].toInt()
        )
    }

    private fun isHexString(string: String) = string.matches(Regex("[0-9a-fA-F]+"))
}
