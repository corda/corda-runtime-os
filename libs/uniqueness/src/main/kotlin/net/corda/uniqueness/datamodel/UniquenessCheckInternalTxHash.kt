package net.corda.uniqueness.datamodel

/**
 * Internal representation of a transaction hash used by the uniqueness checker and backing store
 * only. This representation does not depend on any specific ledger model and is agnostic to both
 * the message bus API and any DB schema that may be used to persist data by the backing store.
 */
data class UniquenessCheckInternalTxHash(
    val algorithmName: String,
    val hash: String
) {
    override fun toString() = "${algorithmName}:${hash}"
}
