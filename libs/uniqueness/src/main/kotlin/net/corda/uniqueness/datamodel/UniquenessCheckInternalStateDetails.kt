package net.corda.uniqueness.datamodel

import net.corda.v5.crypto.SecureHash

/**
 * Internal representation of the state details that must be persisted by the uniqueness checker,
 * used by the uniqueness checker and backing store only. This representation is agnostic to both
 * the message bus API and any DB schema that may be used to persist data by the backing store.
 */
data class UniquenessCheckInternalStateDetails(
    val stateRef: UniquenessCheckInternalStateRef,
    val consumingTxId: SecureHash?
)
