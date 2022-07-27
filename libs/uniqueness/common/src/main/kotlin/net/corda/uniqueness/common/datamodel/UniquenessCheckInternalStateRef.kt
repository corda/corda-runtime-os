package net.corda.uniqueness.common.datamodel

import net.corda.v5.crypto.SecureHash

/**
 * Internal representation of a state reference used by the uniqueness checker and backing store
 * only. This representation does not depend on any specific ledger model and is agnostic to both
 * the message bus API and any DB schema that may be used to persist data by the backing store.
 */
data class UniquenessCheckInternalStateRef(
    val txHash: SecureHash,
    val stateIndex: Int
) {
    override fun toString() = "${txHash}:${stateIndex}"
}
